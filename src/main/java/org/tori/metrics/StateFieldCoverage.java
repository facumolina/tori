package org.tori.metrics;

import org.tori.metrics.ExecutionLevel;
import org.tori.metrics.Metric;
import org.tori.MethodOracles;

import org.tori.metrics.sfc.TargetField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract base class for computing state field coverage.
 * Contains the language-agnostic logic for computing coverage once target fields
 * and accessed fields have been identified by a language-specific subclass.
 *
 * <p>Subclasses are responsible for language-specific concerns:
 * <ul>
 *   <li>Validating target-class paths</li>
 *   <li>Identifying the set of {@link TargetField}s for the target class(es)</li>
 *   <li>Determining which {@link TargetField}s are accessed by a given oracle</li>
 *   <li>Resolving fallback class paths when none are explicitly configured</li>
 *   <li>Clearing internal caches on reconfiguration</li>
 * </ul>
 *
 * <p>Concrete implementations must also implement the {@link Metric} interface.
 *
 * @see StateFieldCoverageJava
 */
public abstract class StateFieldCoverage implements Metric {

    protected List<String> targetClassPaths;
    protected ExecutionLevel executionLevel;
    protected boolean detailedReportingEnabled;
    protected boolean iterableFieldTrackingEnabled;
    protected boolean includeStaticFields;
    protected boolean includeConcreteParentClassFields;
    protected boolean assertOnlyTargetClassMethods;

    private Set<TargetField> lastTargetFields;
    private Set<TargetField> lastAccessedFields;

    // Dependency class tracking (populated by subclasses during field computation)
    protected Set<String> lastLoadedDependencyClasses;
    protected Set<String> lastFailedDependencyClasses;

    public StateFieldCoverage() {
        this.targetClassPaths = new ArrayList<>();
        this.executionLevel = ExecutionLevel.ASSERT;
        this.detailedReportingEnabled = true;
        this.iterableFieldTrackingEnabled = true;
        this.includeStaticFields = false;
        this.includeConcreteParentClassFields = false;
        this.assertOnlyTargetClassMethods = false;
        this.lastTargetFields = new HashSet<>();
        this.lastAccessedFields = new HashSet<>();
        this.lastLoadedDependencyClasses = new HashSet<>();
        this.lastFailedDependencyClasses = new HashSet<>();
    }

    /**
     * Configure this metric with properties.
     * Expected property: {@code target_class} – comma-separated path(s) to the target class file(s).
     * Language-specific path validation is delegated to {@link #validateTargetClassPaths(List)}.
     *
     * @param config Properties object containing metric configuration
     * @throws IllegalArgumentException if required properties are missing or invalid
     */
    public void configure(Properties config) {
        String targetClassProperty = config.getProperty("target_class");
        if (targetClassProperty == null || targetClassProperty.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration must contain 'target_class' property with path(s) to the target class(es)");
        }

        this.targetClassPaths = new ArrayList<>();
        String[] paths = targetClassProperty.split(",");
        for (String path : paths) {
            String trimmedPath = path.trim();
            if (!trimmedPath.isEmpty()) {
                this.targetClassPaths.add(trimmedPath);
            }
        }

        if (this.targetClassPaths.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuration must contain at least one valid non-empty target class path");
        }

        // Language-specific validation
        validateTargetClassPaths(this.targetClassPaths);

        String execLevelValue = config.getProperty("exec_level");
        if (execLevelValue != null && !execLevelValue.isEmpty()) {
            this.executionLevel = ExecutionLevel.fromConfigValue(execLevelValue);
        }

        String iterableTrackingValue = config.getProperty("iterable_field_tracking");
        if (iterableTrackingValue != null && !iterableTrackingValue.isEmpty()) {
            this.iterableFieldTrackingEnabled = Boolean.parseBoolean(iterableTrackingValue);
        }

        String includeStaticFieldsValue = config.getProperty("include_static_fields");
        if (includeStaticFieldsValue != null && !includeStaticFieldsValue.isEmpty()) {
            this.includeStaticFields = Boolean.parseBoolean(includeStaticFieldsValue);
        }

        String includeConcreteParentClassFieldsValue = config.getProperty("include_concrete_parent_class_fields");
        if (includeConcreteParentClassFieldsValue != null && !includeConcreteParentClassFieldsValue.isEmpty()) {
            this.includeConcreteParentClassFields = Boolean.parseBoolean(includeConcreteParentClassFieldsValue);
        }

        String assertOnlyTargetClassMethodsValue = config.getProperty("assert_only_target_class_methods");
        if (assertOnlyTargetClassMethodsValue != null && !assertOnlyTargetClassMethodsValue.isEmpty()) {
            this.assertOnlyTargetClassMethods = Boolean.parseBoolean(assertOnlyTargetClassMethodsValue);
        }

        // Clear language-specific caches before fresh computation
        clearCaches();

        // Pre-load target fields for reporting
        this.lastTargetFields = computeAllTargetFields(this.targetClassPaths);
    }

    /**
     * Assess the state field coverage of a single oracle.
     *
     * @param testCase the full source code of the test case (method body)
     * @param oracle   the oracle (assertion statement) to assess
     * @return proportion of target fields accessed (0.0 to 1.0)
     */
    public double assess(String testCase, String oracle) {
        List<String> classPaths = resolveClassPaths(testCase);
        if (classPaths == null || classPaths.isEmpty()) {
            lastTargetFields = new HashSet<>();
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }

        Set<TargetField> allFields = computeAllTargetFields(classPaths);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }

        Set<TargetField> accessed = computeAccessedFields(testCase, oracle, classPaths);
        lastAccessedFields = new HashSet<>(accessed);

        return (double) accessed.size() / allFields.size();
    }

    /**
     * Assess multiple oracles by computing the union of fields accessed by any oracle.
     * Used for {@link ExecutionLevel#TEST_METHOD} and {@link ExecutionLevel#TEST_CLASS} levels.
     *
     * @param testCase the full source code of the test case (method body or class)
     * @param oracles  list of oracle statements to assess
     * @return proportion of target fields accessed by any oracle (0.0 to 1.0)
     */
    public double assessMultiple(String testCase, List<String> oracles) {
        if (oracles == null || oracles.isEmpty()) {
            lastTargetFields = new HashSet<>();
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }

        List<String> classPaths = resolveClassPaths(testCase);
        if (classPaths == null || classPaths.isEmpty()) {
            lastTargetFields = new HashSet<>();
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }

        Set<TargetField> allFields = computeAllTargetFields(classPaths);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }

        Set<TargetField> unionAccessed = new HashSet<>();
        for (String oracle : oracles) {
            Set<TargetField> oracleFields = computeAccessedFields(testCase, oracle, classPaths);
            unionAccessed.addAll(oracleFields);
        }

        lastAccessedFields = new HashSet<>(unionAccessed);

        return (double) unionAccessed.size() / allFields.size();
    }

    /**
     * Returns the target fields identified during the last assessment.
     *
     * @return set of field labels (FQNs, with {@code "+"} suffix for iterable variants)
     */
    public Set<String> getLastTargetFields() {
        return lastTargetFields.stream().map(TargetField::toLabel).collect(Collectors.toSet());
    }

    /**
     * Returns the fields accessed by the oracle in the last assessment.
     *
     * @return set of accessed field labels
     */
    public Set<String> getLastAccessedFields() {
        return lastAccessedFields.stream().map(TargetField::toLabel).collect(Collectors.toSet());
    }

    /**
     * Returns the target fields that were not accessed in the last assessment.
     *
     * @return set of missing field labels
     */
    public Set<String> getLastMissingFields() {
        Set<String> missing = getLastTargetFields();
        missing.removeAll(getLastAccessedFields());
        return missing;
    }

    /**
     * Returns the first configured target class path, or {@code null} if not configured.
     * For backward compatibility; prefer {@link #getTargetClassPaths()}.
     *
     * @return first target class path, or {@code null}
     */
    public String getTargetClassPath() {
        if (targetClassPaths == null || targetClassPaths.isEmpty()) {
            return null;
        }
        return targetClassPaths.get(0);
    }

    /**
     * Returns all configured target class paths.
     *
     * @return list of target class paths (may be empty if not configured)
     */
    public List<String> getTargetClassPaths() {
        return new ArrayList<>(targetClassPaths);
    }

    /** Returns {@code true} if iterable field tracking is enabled. */
    public boolean isIterableFieldTrackingEnabled() {
        return iterableFieldTrackingEnabled;
    }

    /** Returns {@code true} if static fields are included in coverage computation. */
    public boolean isIncludeStaticFieldsEnabled() {
        return includeStaticFields;
    }

    /**
     * Returns {@code true} if only methods on objects of the target class type are
     * inspected when computing field coverage.
     */
    public boolean isAssertOnlyTargetClassMethods() {
        return assertOnlyTargetClassMethods;
    }

    /**
     * Returns the dependency classes successfully loaded during the last assessment.
     *
     * @return set of dependency class names
     */
    public Set<String> getLastLoadedDependencyClasses() {
        return new HashSet<>(lastLoadedDependencyClasses);
    }

    /**
     * Returns the dependency classes that failed to load during the last assessment.
     *
     * @return set of dependency class names that could not be found
     */
    public Set<String> getLastFailedDependencyClasses() {
        return new HashSet<>(lastFailedDependencyClasses);
    }

    /**
     * Enable or disable detailed reporting.
     *
     * @param enabled {@code true} to enable, {@code false} to disable
     */
    public void setDetailedReportingEnabled(boolean enabled) {
        this.detailedReportingEnabled = enabled;
    }

    /** Returns the execution level configured for this metric. */
    public ExecutionLevel getExecutionLevel() {
        return executionLevel;
    }

    /** Sets the execution level for this metric. */
    public void setExecutionLevel(ExecutionLevel level) {
        this.executionLevel = level;
    }

    // -------------------------------------------------------------------------
    // Shared helpers available to all language-specific subclasses
    // -------------------------------------------------------------------------

    /**
     * Converts a set of internal FQN strings (possibly with {@code "+"} suffix for
     * iterable variants) to a set of {@link TargetField} objects.
     */
    protected Set<TargetField> toTargetFields(Set<String> fqns) {
        Set<TargetField> result = new HashSet<>();
        for (String fqn : fqns) {
            if (fqn.endsWith("+")) {
                result.add(new TargetField(fqn.substring(0, fqn.length() - 1), true));
            } else {
                result.add(new TargetField(fqn, false));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Abstract methods — implemented by language-specific subclasses
    // -------------------------------------------------------------------------

    /**
     * Validate that the provided target-class paths are acceptable for this language.
     *
     * @param paths list of paths to validate
     * @throws IllegalArgumentException if any path is invalid
     */
    protected abstract void validateTargetClassPaths(List<String> paths);

    /**
     * Compute the complete set of {@link TargetField}s for the given target class paths.
     * This forms the denominator of the coverage ratio.
     *
     * @param classPaths list of target class file paths
     * @return set of all target fields
     */
    protected abstract Set<TargetField> computeAllTargetFields(List<String> classPaths);

    /**
     * Compute the set of {@link TargetField}s that are accessed by the given oracle
     * statement, forming the numerator of the coverage ratio.
     *
     * @param testCase   full source code of the test case (method body)
     * @param oracle     oracle (assertion statement) to analyse
     * @param classPaths list of target class file paths
     * @return set of accessed target fields
     */
    protected abstract Set<TargetField> computeAccessedFields(String testCase, String oracle,
                                                               List<String> classPaths);

    /**
     * Resolve the list of target class paths to use when no explicit paths have been
     * configured (fallback mode). Implementations typically derive a class name from
     * the test case and build a path to the corresponding source file.
     *
     * @param testCase full source code of the test case
     * @return list of fallback class paths, or an empty list if none can be determined
     */
    protected abstract List<String> resolveFallbackClassPaths(String testCase);

    /**
     * Clear any internal caches (field-extraction caches, method-access caches, etc.).
     * Called by {@link #configure(Properties)} before applying a new configuration.
     */
    protected abstract void clearCaches();

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> resolveClassPaths(String testCase) {
        if (targetClassPaths != null && !targetClassPaths.isEmpty()) {
            return targetClassPaths;
        }
        return resolveFallbackClassPaths(testCase);
    }

    // -------------------------------------------------------------------------
    // Report methods
    // -------------------------------------------------------------------------
    public void reportAssertLevel(double score, String oracle) {
        Set<String> accessedFields = getLastAccessedFields();
        Set<String> missingFields = getLastMissingFields();
        System.out.println("  - oracle: " + oracle);
        System.out.println("    state_field_coverage_score: " + String.format("%.2f", score));
        System.out.println("    total_assertions: 1");
        System.out.println("    covered_fields: " + accessedFields.size() + " " + accessedFields);
        System.out.println("    uncovered_fields: " + missingFields.size() + " " + missingFields);
    }

    public void reportTestMethodLevel(double score, MethodOracles methodOracles) {
        Set<String> accessedFields = getLastAccessedFields();
        Set<String> missingFields = getLastMissingFields();
        System.out.println("  state_field_coverage_score: " + String.format("%.2f", score));
        System.out.println("  total_assertions: " + methodOracles.oracles().size());
        System.out.println("  covered_fields: " + accessedFields.size() + " " + accessedFields);
        System.out.println("  uncovered_fields: " + missingFields.size() + " " + missingFields);
    }

    public void reportTestClassLevel(double score, List<String> allOracles) {
        Set<String> accessedFields = getLastAccessedFields();
        Set<String> missingFields = getLastMissingFields();
        System.out.println("  state_field_coverage_score: " + String.format("%.2f", score));
        System.out.println("  total_assertions: " + allOracles.size());
        System.out.println("  covered_fields: " + accessedFields.size() + " " + accessedFields);
        System.out.println("  uncovered_fields: " + missingFields.size() + " " + missingFields);
    }

}
