package org.tori.metrics;

import org.treesitter.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Metric that measures the proportion of fields in a target class that are accessed
 * by test oracles (assertions). This metric helps assess how comprehensively an oracle
 * tests the state of an object.
 * 
 * For each assertion, it computes the ratio of fields accessed to total fields in the class.
 */
public class StateFieldCoverage implements Metric {
    private final TSParser parser;
    private final TSLanguage javaLanguage;
    private final Map<String, Set<String>> classFieldsCache;
    private final Map<String, Map<String, Set<String>>> methodFieldAccessCache;
    private List<String> targetClassPaths;
    private ExecutionLevel executionLevel;
    
    // Store detailed information for the last assessment
    private Set<String> lastTargetFields;
    private Set<String> lastAccessedFields;
    private boolean detailedReportingEnabled;
    
    // Iterable field tracking
    private boolean iterableFieldTrackingEnabled;
    private final Map<String, Set<String>> iterableFieldsCache; // Maps class path to iterable field FQNs
    private final Map<String, Set<String>> methodIterationCache; // Maps classPath.methodName to iterated field FQNs
    
    // Dependency class tracking (external dependencies only, cleared at start of each assessment)
    private Set<String> lastLoadedDependencyClasses; // External classes successfully loaded from separate files
    private Set<String> lastFailedDependencyClasses; // External classes that failed to load (source file not found)
    
    public StateFieldCoverage() {
        this.javaLanguage = new TreeSitterJava();
        this.parser = new TSParser();
        parser.setLanguage(javaLanguage);
        this.classFieldsCache = new HashMap<>();
        this.methodFieldAccessCache = new HashMap<>();
        this.targetClassPaths = new ArrayList<>();
        this.executionLevel = ExecutionLevel.ASSERT;
        this.lastTargetFields = new HashSet<>();
        this.lastAccessedFields = new HashSet<>();
        this.detailedReportingEnabled = true;
        this.iterableFieldTrackingEnabled = true; // Enabled by default
        this.iterableFieldsCache = new HashMap<>();
        this.methodIterationCache = new HashMap<>();
        this.lastLoadedDependencyClasses = new HashSet<>();
        this.lastFailedDependencyClasses = new HashSet<>();
    }
    
    /**
     * Configure the metric with properties.
     * Expected property: target_class - the path(s) to the target class file(s).
     * Multiple classes can be specified as comma-separated values.
     * 
     * @param config Properties object containing metric configuration
     */
    @Override
    public void configure(Properties config) {
        String targetClassProperty = config.getProperty("target_class");
        if (targetClassProperty == null || targetClassProperty.isEmpty()) {
            throw new IllegalArgumentException("Configuration must contain 'target_class' property with path(s) to the target class(es)");
        }
        
        // Parse comma-separated target class paths
        this.targetClassPaths = new ArrayList<>();
        String[] paths = targetClassProperty.split(",");
        for (String path : paths) {
            String trimmedPath = path.trim();
            if (!trimmedPath.isEmpty()) {
                this.targetClassPaths.add(trimmedPath);
            }
        }
        
        if (this.targetClassPaths.isEmpty()) {
            throw new IllegalArgumentException("Configuration must contain at least one valid non-empty target class path");
        }
        
        // Validate that all target class paths are valid Java files
        for (String classPath : this.targetClassPaths) {
            Path path = Paths.get(classPath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Target class file does not exist: " + classPath);
            }
            if (!classPath.endsWith(".java")) {
                throw new IllegalArgumentException("Target class must be a Java file (.java extension): " + classPath);
            }
        }
        
        // Configure execution level
        String execLevelValue = config.getProperty("exec_level");
        if (execLevelValue != null && !execLevelValue.isEmpty()) {
            this.executionLevel = ExecutionLevel.fromConfigValue(execLevelValue);
        }
        
        // Configure iterable field tracking
        String iterableTrackingValue = config.getProperty("iterable_field_tracking");
        if (iterableTrackingValue != null && !iterableTrackingValue.isEmpty()) {
            this.iterableFieldTrackingEnabled = Boolean.parseBoolean(iterableTrackingValue);
        }
        
        // Identify target fields after configuration for reporting
        this.lastTargetFields = getAllFieldsInTargetClasses(this.targetClassPaths);
    }
    
    /**
     * Assess the state field coverage of an oracle by computing the proportion of fields
     * that are accessed through the assertion.
     * 
     * @param testCase The full source code of the test case (method body)
     * @param oracle The oracle (assertion statement) to assess
     * @return The proportion of fields accessed (0.0 to 1.0)
     */
    @Override
    public double assess(String testCase, String oracle) {
        // Use configured target class paths if available, otherwise extract from test case
        List<String> classPaths = targetClassPaths;
        if (classPaths == null || classPaths.isEmpty()) {
            // Fallback: extract the target class from the test case
            // Note: This fallback is primarily for backward compatibility and testing purposes
            // The hardcoded 'src/test/resources/' path is intentional as this metric is designed
            // to work with test resources. For production use, always provide a configuration file.
            String className = extractTargetClassName(testCase);
            if (className == null || className.isEmpty()) {
                lastTargetFields = new HashSet<>();
                lastAccessedFields = new HashSet<>();
                return 0.0;
            }
            classPaths = new ArrayList<>();
            classPaths.add("src/test/resources/" + className + ".java");
        }
        
        // Get all fields from all target classes
        Set<String> allFields = getAllFieldsInTargetClasses(classPaths);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }
        
        // Get fields accessed by the oracle from all target classes
        Set<String> accessedFields = getFieldsAccessedByOracleInTargetClasses(testCase, oracle, classPaths);
        lastAccessedFields = new HashSet<>(accessedFields);
        
        // Calculate coverage
        return (double) accessedFields.size() / allFields.size();
    }
    
    /**
     * Get the target fields from the last assessment.
     * 
     * @return Set of target field names
     */
    public Set<String> getLastTargetFields() {
        return new HashSet<>(lastTargetFields);
    }
    
    /**
     * Get the accessed fields from the last assessment.
     * 
     * @return Set of accessed field names
     */
    public Set<String> getLastAccessedFields() {
        return new HashSet<>(lastAccessedFields);
    }
    
    /**
     * Get the missing fields from the last assessment.
     * Missing fields are target fields that were not accessed.
     * 
     * @return Set of missing field names
     */
    public Set<String> getLastMissingFields() {
        Set<String> missingFields = new HashSet<>(lastTargetFields);
        missingFields.removeAll(lastAccessedFields);
        return missingFields;
    }
    
    /**
     * Get the target class path configured for this metric.
     * If multiple target classes are configured, returns the first one.
     * For backward compatibility only. Use getTargetClassPaths() for full list.
     * 
     * @return The first target class path, or null if not configured
     */
    public String getTargetClassPath() {
        if (targetClassPaths == null || targetClassPaths.isEmpty()) {
            return null;
        }
        return targetClassPaths.get(0);
    }
    
    /**
     * Get all target class paths configured for this metric.
     * 
     * @return List of target class paths, or empty list if not configured
     */
    public List<String> getTargetClassPaths() {
        return new ArrayList<>(targetClassPaths);
    }
    
    /**
     * Check if iterable field tracking is enabled.
     * 
     * @return true if iterable field tracking is enabled, false otherwise
     */
    public boolean isIterableFieldTrackingEnabled() {
        return iterableFieldTrackingEnabled;
    }
    
    /**
     * Get the dependency classes that were successfully loaded.
     * These are classes referenced by target classes that were found and included.
     * 
     * @return Set of dependency class names (simple names without package)
     */
    public Set<String> getLastLoadedDependencyClasses() {
        return new HashSet<>(lastLoadedDependencyClasses);
    }
    
    /**
     * Get the dependency classes that failed to load.
     * These are classes referenced by target classes but their source files could not be found.
     * 
     * @return Set of dependency class names that failed to load
     */
    public Set<String> getLastFailedDependencyClasses() {
        return new HashSet<>(lastFailedDependencyClasses);
    }
    
    /**
     * Enable or disable detailed reporting in the configure method.
     * This method should be called before configure() to prevent printing
     * during configuration. If called after configure(), it will only affect
     * future calls to configure() but not the current configuration's output.
     * 
     * @param enabled true to enable detailed reporting, false to disable
     */
    public void setDetailedReportingEnabled(boolean enabled) {
        this.detailedReportingEnabled = enabled;
    }
    
    @Override
    public ExecutionLevel getExecutionLevel() {
        return executionLevel;
    }
    
    @Override
    public void setExecutionLevel(ExecutionLevel level) {
        this.executionLevel = level;
    }
    
    /**
     * Assess multiple oracles at once, computing the union of accessed fields.
     * This is used for TEST_METHOD and TEST_CLASS execution levels.
     * 
     * @param testCase The full source code of the test case (method body or class)
     * @param oracles List of oracle statements to assess
     * @return The proportion of fields accessed by any of the oracles (0.0 to 1.0)
     */
    @Override
    public double assessMultiple(String testCase, List<String> oracles) {
        if (oracles == null || oracles.isEmpty()) {
            lastTargetFields = new HashSet<>();
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }
        
        // Use configured target class paths if available
        List<String> classPaths = targetClassPaths;
        if (classPaths == null || classPaths.isEmpty()) {
            String className = extractTargetClassName(testCase);
            if (className == null || className.isEmpty()) {
                lastTargetFields = new HashSet<>();
                lastAccessedFields = new HashSet<>();
                return 0.0;
            }
            classPaths = new ArrayList<>();
            classPaths.add("src/test/resources/" + className + ".java");
        }
        
        // Get all fields from all target classes
        Set<String> allFields = getAllFieldsInTargetClasses(classPaths);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }
        
        // Compute the union of all fields accessed by any oracle
        Set<String> unionAccessedFields = new HashSet<>();
        for (String oracle : oracles) {
            Set<String> oracleFields = getFieldsAccessedByOracleInTargetClasses(testCase, oracle, classPaths);
            unionAccessedFields.addAll(oracleFields);
        }
        
        lastAccessedFields = new HashSet<>(unionAccessedFields);
        
        // Calculate coverage
        return (double) unionAccessedFields.size() / allFields.size();
    }
    
    /**
     * Extract the name of the target class being tested from the test case.
     * This looks for class instantiation in the test case.
     */
    private String extractTargetClassName(String testCase) {
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();
        
        Set<String> classNames = new HashSet<>();
        findClassInstantiations(rootNode, testCase, classNames);
        
        // Return the first non-test class name found (heuristic)
        for (String className : classNames) {
            if (!className.endsWith("Test") && !className.equals("String") && 
                !className.equals("Integer") && !className.equals("Object")) {
                return className;
            }
        }
        
        return null;
    }
    
    /**
     * Find all class instantiations in the test case.
     */
    private void findClassInstantiations(TSNode node, String sourceCode, Set<String> classNames) {
        String nodeType = node.getType();
        
        if ("object_creation_expression".equals(nodeType)) {
            // Get the type identifier
            TSNode typeNode = node.getChildByFieldName("type");
            if (typeNode != null) {
                String className = extractClassName(typeNode, sourceCode);
                if (className != null) {
                    classNames.add(className);
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findClassInstantiations(child, sourceCode, classNames);
        }
    }
    
    /**
     * Extract the class name from a type node.
     */
    private String extractClassName(TSNode typeNode, String sourceCode) {
        if ("type_identifier".equals(typeNode.getType())) {
            int startByte = typeNode.getStartByte();
            int endByte = typeNode.getEndByte();
            return sourceCode.substring(startByte, endByte);
        }
        
        // Handle generic types
        if ("generic_type".equals(typeNode.getType())) {
            TSNode identifier = typeNode.getChild(0);
            if (identifier != null && "type_identifier".equals(identifier.getType())) {
                int startByte = identifier.getStartByte();
                int endByte = identifier.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        
        return null;
    }
    
    /**
     * Get all fields defined in a class, including fields from inner classes.
     * If iterable field tracking is enabled, this includes both normal labels and
     * special labels (with '+' suffix) for iterable fields.
     */
    private Set<String> getAllFieldsInClass(String classPath) {
        if (classFieldsCache.containsKey(classPath)) {
            return classFieldsCache.get(classPath);
        }
        
        Set<String> fields = new HashSet<>();
        Set<String> visitedClasses = new HashSet<>();
        fields = getAllFieldsInClassRecursive(classPath, visitedClasses, true);
        
        classFieldsCache.put(classPath, fields);
        return fields;
    }
    
    /**
     * Recursively get all fields defined in a class, including fields from inner classes
     * and fields from classes used by the target class (declared in other files).
     * If iterable field tracking is enabled, this includes both normal labels and
     * special labels (with '+' suffix) for iterable fields.
     * 
     * @param classPath Path to the class file
     * @param visitedClasses Set of already visited class paths to avoid infinite recursion
     * @param isRootClass Whether this is a root target class (not a dependency)
     * @return Set of all field names
     */
    private Set<String> getAllFieldsInClassRecursive(String classPath, Set<String> visitedClasses, boolean isRootClass) {
        // Avoid infinite recursion
        if (visitedClasses.contains(classPath)) {
            return new HashSet<>();
        }
        visitedClasses.add(classPath);
        
        Set<String> fields = new HashSet<>();
        
        Path path = Paths.get(classPath);
        if (Files.exists(path)) {
            try {
                String classSource = Files.readString(path);
                fields = extractFieldsFromClass(classSource);
                
                // Extract field types to find referenced classes
                TSTree tree = parser.parseString(null, classSource);
                TSNode rootNode = tree.getRootNode();
                String packageName = extractPackageName(rootNode, classSource);
                Map<String, String> fieldTypes = extractFieldTypes(rootNode, classSource, packageName, "");
                
                // Find referenced classes in the same directory
                Set<String> referencedClassPaths = findReferencedClassPaths(classPath, classSource, fieldTypes, isRootClass);
                
                // Recursively include fields from referenced classes (not root anymore)
                for (String referencedClassPath : referencedClassPaths) {
                    Set<String> referencedFields = getAllFieldsInClassRecursive(referencedClassPath, visitedClasses, false);
                    fields.addAll(referencedFields);
                }
                
                // If iterable field tracking is enabled, add special labels for iterable fields
                if (iterableFieldTrackingEnabled) {
                    Set<String> iterableFields = identifyIterableFields(classPath, classSource, fields);
                    iterableFieldsCache.put(classPath, iterableFields);
                    
                    // Add special labels (field+) for iterable fields
                    for (String iterableField : iterableFields) {
                        fields.add(iterableField + "+");
                    }
                }
            } catch (IOException e) {
                // Failed to read class file
            }
        }
        
        return fields;
    }
    
    /**
     * Find paths to classes that are referenced by field types in the given class.
     * This identifies custom classes (not primitives or standard library classes)
     * that are used as field types and declared in separate files.
     * Uses import statements and package names to resolve class paths.
     * Also tracks which classes were successfully found and which were not.
     * 
     * @param classPath Path to the class file
     * @param classSource Source code of the class (to check for inner classes)
     * @param fieldTypes Map of field FQN to type name
     * @param isRootClass Whether this is a root target class (not a dependency)
     * @return Set of paths to referenced class files
     */
    private Set<String> findReferencedClassPaths(String classPath, String classSource, Map<String, String> fieldTypes, boolean isRootClass) {
        Set<String> referencedPaths = new HashSet<>();
        Path currentPath = Paths.get(classPath).normalize();
        Path directory = currentPath.getParent();
        
        if (directory == null) {
            return referencedPaths;
        }
        
        // Extract package name and imports
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        String packageName = extractPackageName(rootNode, classSource);
        Map<String, String> imports = extractImports(rootNode, classSource);
        
        // Extract inner class names from the source
        Set<String> innerClassNames = extractInnerClassNames(classSource);
        
        // Extract unique type names (without array brackets)
        Set<String> typeNames = new HashSet<>();
        for (String typeName : fieldTypes.values()) {
            // Remove array brackets if present
            String baseTypeName = typeName.replace("[]", "");
            
            // Skip primitives and common standard library types
            if (!isPrimitiveOrStandardType(baseTypeName)) {
                typeNames.add(baseTypeName);
            }
        }
        
        // For each type, resolve its file path
        for (String typeName : typeNames) {
            // Skip inner classes - they are not in separate files
            if (innerClassNames.contains(typeName)) {
                continue;
            }
            
            String resolvedPath = resolveClassPath(typeName, packageName, imports, currentPath);
            if (resolvedPath != null && Files.exists(Paths.get(resolvedPath))) {
                referencedPaths.add(resolvedPath);
                // Track successfully loaded dependency (including transitive dependencies)
                lastLoadedDependencyClasses.add(typeName);
            } else {
                // Track failed dependency (including transitive dependencies)
                lastFailedDependencyClasses.add(typeName);
            }
        }
        
        return referencedPaths;
    }
    
    /**
     * Check if a type name represents a primitive type or standard library type.
     * 
     * @param typeName The type name to check
     * @return true if the type is primitive or from standard library, false otherwise
     */
    private boolean isPrimitiveOrStandardType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return true;
        }
        
        // Primitives
        Set<String> primitives = Set.of(
            "int", "long", "short", "byte", "char", 
            "boolean", "float", "double", "void"
        );
        if (primitives.contains(typeName)) {
            return true;
        }
        
        // Common standard library types
        Set<String> standardTypes = Set.of(
            "String", "Integer", "Long", "Short", "Byte", "Character",
            "Boolean", "Float", "Double", "Object", "List", "Set",
            "Map", "Collection", "ArrayList", "HashSet", "HashMap"
        );
        return standardTypes.contains(typeName);
    }
    
    /**
     * Extract the names of all inner classes (nested classes) defined in the given source code.
     * 
     * @param classSource The source code of the class
     * @return Set of inner class names
     */
    private Set<String> extractInnerClassNames(String classSource) {
        Set<String> innerClassNames = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        findInnerClassNames(rootNode, classSource, innerClassNames, false);
        
        return innerClassNames;
    }
    
    /**
     * Recursively find all inner class names in the AST.
     * 
     * @param node Current node being processed
     * @param sourceCode Source code
     * @param innerClassNames Set to collect inner class names
     * @param insideClass Whether we're currently inside a class declaration
     */
    private void findInnerClassNames(TSNode node, String sourceCode, Set<String> innerClassNames, boolean insideClass) {
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            // If we're already inside a class, this is an inner class
            if (insideClass) {
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    int startByte = nameNode.getStartByte();
                    int endByte = nameNode.getEndByte();
                    String className = sourceCode.substring(startByte, endByte);
                    innerClassNames.add(className);
                }
            }
            // Now we're inside a class, recurse into it
            insideClass = true;
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findInnerClassNames(child, sourceCode, innerClassNames, insideClass);
        }
    }
    
    /**
     * Get all fields defined in multiple target classes, including fields from inner classes.
     * This method collects fields from all target classes and returns their union.
     * Fields that are the same (by fully qualified name) are only counted once.
     * 
     * @param classPaths List of paths to target class files
     * @return Set of all field names from all target classes
     */
    private Set<String> getAllFieldsInTargetClasses(List<String> classPaths) {
        // Clear dependency tracking for fresh collection
        lastLoadedDependencyClasses = new HashSet<>();
        lastFailedDependencyClasses = new HashSet<>();
        
        Set<String> allFields = new HashSet<>();
        
        for (String classPath : classPaths) {
            Set<String> classFields = getAllFieldsInClass(classPath);
            allFields.addAll(classFields);
        }
        
        return allFields;
    }
    
    /**
     * Extract all field names from a class, including inner classes.
     * Fields are stored as fully qualified names: package.class.field_name
     */
    private Set<String> extractFieldsFromClass(String classSource) {
        Set<String> fields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        // Extract package name
        String packageName = extractPackageName(rootNode, classSource);
        
        // Find field declarations with class context
        findFieldDeclarationsWithContext(rootNode, classSource, fields, packageName, "");
        
        return fields;
    }
    
    /**
     * Recursively find all field declarations in the AST.
     */
    private void findFieldDeclarations(TSNode node, String sourceCode, Set<String> fields) {
        String nodeType = node.getType();
        
        if ("field_declaration".equals(nodeType)) {
            extractFieldNames(node, sourceCode, fields);
        } else if ("class_declaration".equals(nodeType)) {
            // Process inner class fields
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = node.getChild(i);
                if ("class_body".equals(child.getType())) {
                    findFieldDeclarations(child, sourceCode, fields);
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldDeclarations(child, sourceCode, fields);
        }
    }
    
    /**
     * Extract package name from the AST root node.
     */
    private String extractPackageName(TSNode rootNode, String sourceCode) {
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("package_declaration".equals(child.getType())) {
                // Find the identifier in the package declaration
                int pkgChildCount = child.getChildCount();
                for (int j = 0; j < pkgChildCount; j++) {
                    TSNode pkgChild = child.getChild(j);
                    if ("scoped_identifier".equals(pkgChild.getType()) || 
                        "identifier".equals(pkgChild.getType())) {
                        int startByte = pkgChild.getStartByte();
                        int endByte = pkgChild.getEndByte();
                        return sourceCode.substring(startByte, endByte);
                    }
                }
            }
        }
        return "";
    }
    
    /**
     * Extract import statements from the AST root node.
     * Returns a map from simple class name to fully qualified class name.
     * For example: "ReferencedClass" -> "com.example.package2.ReferencedClass"
     */
    private Map<String, String> extractImports(TSNode rootNode, String sourceCode) {
        Map<String, String> imports = new HashMap<>();
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("import_declaration".equals(child.getType())) {
                // Find the identifier in the import declaration
                int importChildCount = child.getChildCount();
                for (int j = 0; j < importChildCount; j++) {
                    TSNode importChild = child.getChild(j);
                    if ("scoped_identifier".equals(importChild.getType())) {
                        int startByte = importChild.getStartByte();
                        int endByte = importChild.getEndByte();
                        String fullImport = sourceCode.substring(startByte, endByte);
                        
                        // Extract simple class name (last part after the last dot)
                        int lastDot = fullImport.lastIndexOf('.');
                        if (lastDot >= 0) {
                            String simpleClassName = fullImport.substring(lastDot + 1);
                            imports.put(simpleClassName, fullImport);
                        }
                    }
                }
            }
        }
        return imports;
    }
    
    /**
     * Resolve a class path based on the type name, package, and imports.
     * 
     * @param typeName The simple class name to resolve
     * @param packageName The package of the current class
     * @param imports Map of simple class names to fully qualified class names
     * @param currentPath Path to the current class file
     * @return Resolved file path or null if not found
     */
    private String resolveClassPath(String typeName, String packageName, Map<String, String> imports, Path currentPath) {
        // Normalize the current path to handle any relative components
        currentPath = currentPath.normalize();
        Path directory = currentPath.getParent();
        if (directory == null) {
            return null;
        }
        
        // First, check if the type is explicitly imported
        if (imports.containsKey(typeName)) {
            String fullyQualifiedName = imports.get(typeName);
            // Convert package name to path (e.g., "com.example.package2" -> "com/example/package2")
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            if (lastDot < 0) {
                // No package in the fully qualified name (default package).
                // Default package imports are not supported in modern Java for cross-package references.
                // Fall through to try same-package resolution instead.
                // This is intentional to avoid path resolution issues.
            } else {
                String packagePath = fullyQualifiedName.substring(0, lastDot);
                packagePath = packagePath.replace('.', '/');
                String className = typeName + ".java";
                
                // Try to find the file by navigating from the current directory
                // First, try to find the root of the source tree by going up
                Path searchPath = findSourceRoot(directory);
                if (searchPath != null) {
                    Path resolvedPath = searchPath.resolve(packagePath).resolve(className);
                    if (Files.exists(resolvedPath)) {
                        return resolvedPath.toString();
                    }
                }
                
                // If not found via source root, try relative to current directory
                Path relativePath = directory.resolve(packagePath).resolve(className);
                if (Files.exists(relativePath)) {
                    return relativePath.toString();
                }
            }
        }
        
        // If not imported, check if it's in the same package (same directory)
        Path samePackagePath = directory.resolve(typeName + ".java");
        if (Files.exists(samePackagePath)) {
            return samePackagePath.toString();
        }
        
        // If the current class has a package, try to find the class in the same package root
        if (packageName != null && !packageName.isEmpty()) {
            // Navigate up to the source root and then down to the same package
            Path sourceRoot = findSourceRoot(directory);
            if (sourceRoot != null) {
                String packagePath = packageName.replace('.', '/');
                Path packageDir = sourceRoot.resolve(packagePath);
                Path classPath = packageDir.resolve(typeName + ".java");
                if (Files.exists(classPath)) {
                    return classPath.toString();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find the source root directory by navigating up from the current directory.
     * Looks for common source root markers like "java", "src", "resources", etc.
     * 
     * This method handles typical Java project structures like:
     * - src/main/java
     * - src/test/java
     * - src/main/resources
     * - src/test/resources
     * 
     * @param currentDir Current directory
     * @return Source root directory or current directory if not found
     */
    private Path findSourceRoot(Path currentDir) {
        Path dir = currentDir;
        
        // Navigate up looking for source root indicators
        while (dir != null) {
            String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
            
            // Stop at common source root directories
            if ("java".equals(dirName) || "resources".equals(dirName)) {
                // These are typically the direct source roots (e.g., src/main/java)
                return dir;
            } else if ("main".equals(dirName) || "test".equals(dirName)) {
                // These are usually under "src" in typical Maven/Gradle structures.
                // We want to return their parent (likely "src") if it exists,
                // otherwise just return this directory as the best guess.
                // This handles src/main/java and src/test/java structures.
                Path parent = dir.getParent();
                return parent != null ? parent : dir;
            } else if ("src".equals(dirName)) {
                // src is a common source root in many project structures
                return dir;
            }
            
            dir = dir.getParent();
        }
        
        // If no source root found, return the original directory
        // This fallback ensures we can still attempt resolution even in non-standard structures
        return currentDir;
    }
    
    /**
     * Recursively find all field declarations with their class context.
     * Fields are stored as fully qualified names: package.class.field_name
     */
    private void findFieldDeclarationsWithContext(TSNode node, String sourceCode, Set<String> fields, 
                                                   String packageName, String classContext) {
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            // Extract the class name
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String className = sourceCode.substring(startByte, endByte);
                
                // Build the new class context
                String newClassContext = classContext.isEmpty() ? className : classContext + "." + className;
                
                // Find the class body and process it - DON'T process children later
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if ("class_body".equals(child.getType())) {
                        findFieldDeclarationsWithContext(child, sourceCode, fields, packageName, newClassContext);
                    }
                }
                return; // Important: don't process children again
            }
        } else if ("field_declaration".equals(nodeType)) {
            // Extract field names with fully qualified prefix
            extractFieldNamesWithContext(node, sourceCode, fields, packageName, classContext);
            return; // Don't process children of field declarations
        }
        
        // Recursively process children for non-class, non-field nodes
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldDeclarationsWithContext(child, sourceCode, fields, packageName, classContext);
        }
    }
    
    /**
     * Extract field names from a field declaration node.
     */
    private void extractFieldNames(TSNode declarationNode, String sourceCode, Set<String> fields) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                TSNode identifier = child.getChild(0);
                if (identifier != null && "identifier".equals(identifier.getType())) {
                    int startByte = identifier.getStartByte();
                    int endByte = identifier.getEndByte();
                    String fieldName = sourceCode.substring(startByte, endByte);
                    fields.add(fieldName);
                }
            }
        }
    }
    
    /**
     * Extract field names from a field declaration node with fully qualified context.
     * Stores fields as: package.class.field_name
     */
    private void extractFieldNamesWithContext(TSNode declarationNode, String sourceCode, Set<String> fields,
                                              String packageName, String classContext) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                TSNode identifier = child.getChild(0);
                if (identifier != null && "identifier".equals(identifier.getType())) {
                    int startByte = identifier.getStartByte();
                    int endByte = identifier.getEndByte();
                    String fieldName = sourceCode.substring(startByte, endByte);
                    
                    // Build fully qualified field name
                    String fqn = packageName.isEmpty() ? classContext + "." + fieldName :
                                packageName + "." + classContext + "." + fieldName;
                    fields.add(fqn);
                }
            }
        }
    }
    
    /**
     * Get the fields accessed by an oracle statement.
     * This traces method calls to determine which fields are accessed.
     * Now includes package-aware filtering to ensure only fields from the target class are counted.
     */
    private Set<String> getFieldsAccessedByOracle(String testCase, String oracle, String classPath) {
        Set<String> accessedFields = new HashSet<>();
        
        // Get all fields in the target class (with FQN)
        Set<String> allFields = getAllFieldsInClass(classPath);
        
        // Extract target class information (name and package)
        TargetClassInfo targetClassInfo = extractTargetClassInfo(classPath);
        if (targetClassInfo == null) {
            return accessedFields;
        }
        
        // Extract variable types from the test case
        Map<String, String> variableTypes = extractVariableTypes(testCase);
        
        // Extract package from test class (if available)
        String testPackage = extractPackageNameFromTestCase(testCase);
        
        TSTree tree = parser.parseString(null, oracle);
        TSNode rootNode = tree.getRootNode();
        
        // Find all method invocations in the oracle, filtering by variable type
        findMethodInvocationsWithTypeCheck(rootNode, oracle, variableTypes, targetClassInfo, testPackage, accessedFields, classPath);
        
        // Also check for direct field accesses in the oracle, filtering by variable type
        findDirectFieldAccessesWithTypeCheck(rootNode, oracle, accessedFields, allFields, variableTypes, targetClassInfo, testPackage);
        
        return accessedFields;
    }
    
    /**
     * Get the fields accessed by an oracle statement from multiple target classes.
     * This method collects accessed fields from all target classes and returns their union.
     * 
     * @param testCase The full source code of the test case (method body)
     * @param oracle The oracle (assertion statement) to assess
     * @param classPaths List of paths to target class files
     * @return Set of accessed field names from all target classes
     */
    private Set<String> getFieldsAccessedByOracleInTargetClasses(String testCase, String oracle, List<String> classPaths) {
        Set<String> allAccessedFields = new HashSet<>();
        
        for (String classPath : classPaths) {
            Set<String> classAccessedFields = getFieldsAccessedByOracle(testCase, oracle, classPath);
            allAccessedFields.addAll(classAccessedFields);
        }
        
        return allAccessedFields;
    }
    
    /**
     * Find all method invocations in the AST.
     */
    private void findMethodInvocations(TSNode node, String sourceCode, Set<String> methodNames) {
        String nodeType = node.getType();
        
        if ("method_invocation".equals(nodeType)) {
            // Get the method name
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String methodName = sourceCode.substring(startByte, endByte);
                methodNames.add(methodName);
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findMethodInvocations(child, sourceCode, methodNames);
        }
    }
    
    /**
     * Find direct field accesses in the oracle (e.g., obj.field).
     * Maps short field names to their fully qualified names.
     */
    private void findDirectFieldAccesses(TSNode node, String sourceCode, Set<String> fields, Set<String> allFields) {
        String nodeType = node.getType();
        
        if ("field_access".equals(nodeType)) {
            TSNode fieldNode = node.getChildByFieldName("field");
            if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                int startByte = fieldNode.getStartByte();
                int endByte = fieldNode.getEndByte();
                String fieldName = sourceCode.substring(startByte, endByte);
                
                // Map short field name to FQN
                String fqn = mapFieldNameToFQN(fieldName, allFields);
                if (fqn != null) {
                    fields.add(fqn);
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findDirectFieldAccesses(child, sourceCode, fields, allFields);
        }
    }
    
    /**
     * Map a short field name to its fully qualified name.
     * If multiple FQNs match, returns the first one.
     */
    private String mapFieldNameToFQN(String shortName, Set<String> allFields) {
        for (String fqn : allFields) {
            if (fqn.endsWith("." + shortName)) {
                return fqn;
            }
        }
        return null;
    }
    
    /**
     * Get the fields accessed by a specific method in a class.
     * This analyzes the method body to determine which fields are accessed.
     * If multiple methods with the same name exist (e.g., in different classes),
     * we return the union of fields accessed by all of them.
     */
    private Set<String> getFieldsAccessedByMethod(String classPath, String methodName) {
        String cacheKey = classPath + "." + methodName;
        
        if (methodFieldAccessCache.containsKey(classPath) && 
            methodFieldAccessCache.get(classPath).containsKey(methodName)) {
            return methodFieldAccessCache.get(classPath).get(methodName);
        }
        
        Set<String> accessedFields = new HashSet<>();
        
        Path path = Paths.get(classPath);
        if (Files.exists(path)) {
            try {
                String classSource = Files.readString(path);
                accessedFields = extractFieldAccessFromAllMethodsNamed(classSource, methodName);
            } catch (IOException e) {
                // Failed to read class file
            }
        }
        
        // Cache the result
        methodFieldAccessCache.computeIfAbsent(classPath, k -> new HashMap<>())
            .put(methodName, accessedFields);
        
        return accessedFields;
    }
    
    /**
     * Extract fields accessed by ALL methods with the given name.
     * This handles cases where multiple classes have methods with the same name.
     * Prefers methods in outer classes over inner classes when possible.
     * Also detects when fields are iterated over (for special + labels).
     */
    private Set<String> extractFieldAccessFromAllMethodsNamed(String classSource, String methodName) {
        Set<String> allAccessedFields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        // Get all fields in the class with FQN
        Set<String> allFields = extractFieldsFromClass(classSource);
        
        // Extract package name
        String packageName = extractPackageName(rootNode, classSource);
        
        // Find ALL methods with this name and collect their accessed fields
        List<MethodContext> methods = findAllMethodsWithClassContext(rootNode, classSource, methodName, packageName, "");
        
        // When we can't determine which specific method is called (no type inference),
        // we conservatively include fields from all methods with that name
        for (MethodContext methodContext : methods) {
            // Filter fields: include fields in the method's class AND its inner classes
            Set<String> relevantFields = new HashSet<>();
            for (String fqn : allFields) {
                if (isFieldInClassOrInnerClass(fqn, methodContext.classContext)) {
                    relevantFields.add(fqn);
                }
            }
            
            // Create a mapping from short names to FQN for easier lookup
            Map<String, Set<String>> shortNameToFQN = new HashMap<>();
            for (String fqn : relevantFields) {
                String shortName = extractShortFieldName(fqn);
                shortNameToFQN.computeIfAbsent(shortName, k -> new HashSet<>()).add(fqn);
            }
            
            // Get local variables declared in the method
            Set<String> localVars = new HashSet<>();
            findLocalVariables(methodContext.methodNode, classSource, localVars);
            
            // Find all identifier and field access nodes in the method
            Set<String> methodFields = new HashSet<>();
            findFieldAccessesInMethod(methodContext.methodNode, classSource, methodFields, shortNameToFQN, localVars);
            allAccessedFields.addAll(methodFields);
            
            // If iterable field tracking is enabled, detect iterated fields
            if (iterableFieldTrackingEnabled) {
                Set<String> iteratedFields = findIteratedFields(methodContext.methodNode, classSource, shortNameToFQN, localVars);
                // Add special labels for iterated fields
                for (String iteratedField : iteratedFields) {
                    allAccessedFields.add(iteratedField + "+");
                }
            }
        }
        
        return allAccessedFields;
    }
    
    /**
     * Find ALL methods with the given name and their class contexts.
     */
    private List<MethodContext> findAllMethodsWithClassContext(TSNode node, String sourceCode, String methodName,
                                                                String packageName, String classContext) {
        List<MethodContext> methods = new ArrayList<>();
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String className = sourceCode.substring(startByte, endByte);
                
                String newClassContext = classContext.isEmpty() ? 
                    (packageName.isEmpty() ? className : packageName + "." + className) :
                    classContext + "." + className;
                
                // Search in this class
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    methods.addAll(findAllMethodsWithClassContext(child, sourceCode, methodName, 
                                                                   packageName, newClassContext));
                }
            }
            return methods; // Don't continue searching outside this class
        } else if ("method_declaration".equals(nodeType)) {
            // Only add methods that have a class context (i.e., we're inside a class)
            if (!classContext.isEmpty()) {
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    int startByte = nameNode.getStartByte();
                    int endByte = nameNode.getEndByte();
                    String name = sourceCode.substring(startByte, endByte);
                    if (methodName.equals(name)) {
                        methods.add(new MethodContext(node, classContext));
                    }
                }
            }
            return methods; // Don't search inside method declarations
        }
        
        // Recursively search children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            methods.addAll(findAllMethodsWithClassContext(child, sourceCode, methodName, 
                                                          packageName, classContext));
        }
        
        return methods;
    }
    
    /**
     * Extract which fields are accessed by a specific method.
     * Returns fields as fully qualified names.
     */
    private Set<String> extractFieldAccessFromMethod(String classSource, String methodName) {
        Set<String> accessedFields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        // Get all fields in the class with FQN
        Set<String> allFields = extractFieldsFromClass(classSource);
        
        // Extract package name
        String packageName = extractPackageName(rootNode, classSource);
        
        // Find the method declaration and its containing class
        MethodContext methodContext = findMethodWithClassContext(rootNode, classSource, methodName, packageName, "");
        if (methodContext == null || methodContext.methodNode == null) {
            return accessedFields;
        }
        
        // Filter fields to only those in the method's class (not inner classes)
        Set<String> relevantFields = new HashSet<>();
        for (String fqn : allFields) {
            if (fqn.startsWith(methodContext.classContext + ".") && 
                !fqn.substring(methodContext.classContext.length() + 1).contains(".")) {
                // Field is directly in this class (not in an inner class)
                relevantFields.add(fqn);
            }
        }
        
        // Create a mapping from short names to FQN for easier lookup
        Map<String, Set<String>> shortNameToFQN = new HashMap<>();
        for (String fqn : relevantFields) {
            String shortName = extractShortFieldName(fqn);
            shortNameToFQN.computeIfAbsent(shortName, k -> new HashSet<>()).add(fqn);
        }
        
        // Get local variables declared in the method
        Set<String> localVars = new HashSet<>();
        findLocalVariables(methodContext.methodNode, classSource, localVars);
        
        // Find all identifier and field access nodes in the method
        findFieldAccessesInMethod(methodContext.methodNode, classSource, accessedFields, shortNameToFQN, localVars);
        
        return accessedFields;
    }
    
    /**
     * Context information for a method.
     */
    private static class MethodContext {
        TSNode methodNode;
        String classContext;
        
        MethodContext(TSNode methodNode, String classContext) {
            this.methodNode = methodNode;
            this.classContext = classContext;
        }
    }
    
    /**
     * Find a method declaration with its class context.
     */
    private MethodContext findMethodWithClassContext(TSNode node, String sourceCode, String methodName,
                                                      String packageName, String classContext) {
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String className = sourceCode.substring(startByte, endByte);
                
                String newClassContext = classContext.isEmpty() ? 
                    (packageName.isEmpty() ? className : packageName + "." + className) :
                    classContext + "." + className;
                
                // Search in this class
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    MethodContext result = findMethodWithClassContext(child, sourceCode, methodName, 
                                                                      packageName, newClassContext);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } else if ("method_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String name = sourceCode.substring(startByte, endByte);
                if (methodName.equals(name)) {
                    return new MethodContext(node, classContext);
                }
            }
        }
        
        // Recursively search children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            MethodContext result = findMethodWithClassContext(child, sourceCode, methodName, 
                                                              packageName, classContext);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Extract the short field name from a fully qualified name.
     * E.g., "com.example.IntsList.size" -> "size"
     */
    private String extractShortFieldName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fqn.length() - 1) {
            return fqn.substring(lastDot + 1);
        }
        return fqn;
    }
    
    /**
     * Check if a field FQN belongs to a class or its inner classes.
     * E.g., "com.example.Person.name" belongs to "com.example.Person"
     * E.g., "com.example.Person.Address.name" belongs to "com.example.Person" (inner class)
     * E.g., "com.example.PersonUtil.field" does NOT belong to "com.example.Person"
     */
    private boolean isFieldInClassOrInnerClass(String fieldFQN, String classContext) {
        if (classContext.isEmpty()) {
            return false;
        }
        String prefix = classContext + ".";
        return fieldFQN.startsWith(prefix) && fieldFQN.length() > prefix.length();
    }
    
    /**
     * Find a method declaration by name.
     */
    private TSNode findMethodDeclaration(TSNode node, String sourceCode, String methodName) {
        String nodeType = node.getType();
        
        if ("method_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String name = sourceCode.substring(startByte, endByte);
                if (methodName.equals(name)) {
                    return node;
                }
            }
        }
        
        // Recursively search children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            TSNode result = findMethodDeclaration(child, sourceCode, methodName);
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Find all local variables declared in a method.
     */
    private void findLocalVariables(TSNode methodNode, String sourceCode, Set<String> localVars) {
        int childCount = methodNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodNode.getChild(i);
            findLocalVariableDeclarations(child, sourceCode, localVars);
        }
    }
    
    /**
     * Recursively find local variable declarations.
     */
    private void findLocalVariableDeclarations(TSNode node, String sourceCode, Set<String> localVars) {
        String nodeType = node.getType();
        
        if ("local_variable_declaration".equals(nodeType)) {
            extractVariableNames(node, sourceCode, localVars);
        } else if ("enhanced_for_statement".equals(nodeType) || "for_statement".equals(nodeType)) {
            // Handle for loop variables
            TSNode initNode = node.getChild(0);
            if (initNode != null) {
                extractVariableNames(initNode, sourceCode, localVars);
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findLocalVariableDeclarations(child, sourceCode, localVars);
        }
    }
    
    /**
     * Extract variable names from a declaration node.
     */
    private void extractVariableNames(TSNode declarationNode, String sourceCode, Set<String> variables) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                TSNode identifier = child.getChild(0);
                if (identifier != null && "identifier".equals(identifier.getType())) {
                    int startByte = identifier.getStartByte();
                    int endByte = identifier.getEndByte();
                    String varName = sourceCode.substring(startByte, endByte);
                    variables.add(varName);
                }
            }
        }
    }
    
    /**
     * Find all field accesses within a method body.
     * This includes both explicit field accesses (obj.field) and implicit accesses (field).
     * Maps short field names to their fully qualified names.
     */
    private void findFieldAccessesInMethod(TSNode node, String sourceCode, Set<String> accessedFields, 
                                           Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        String nodeType = node.getType();
        
        // Look for explicit field access patterns (obj.field)
        if ("field_access".equals(nodeType)) {
            TSNode fieldNode = node.getChildByFieldName("field");
            if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                int startByte = fieldNode.getStartByte();
                int endByte = fieldNode.getEndByte();
                String fieldName = sourceCode.substring(startByte, endByte);
                // Map short name to FQN and add all matching FQNs
                if (shortNameToFQN.containsKey(fieldName)) {
                    accessedFields.addAll(shortNameToFQN.get(fieldName));
                }
            }
        } else if ("identifier".equals(nodeType)) {
            // Check if this identifier is a field (not a local variable or parameter)
            TSNode parent = node.getParent();
            if (parent != null && !isMethodName(node, parent)) {
                int startByte = node.getStartByte();
                int endByte = node.getEndByte();
                String name = sourceCode.substring(startByte, endByte);
                
                // It's a field if it's in shortNameToFQN and NOT in localVars
                if (shortNameToFQN.containsKey(name) && !localVars.contains(name)) {
                    accessedFields.addAll(shortNameToFQN.get(name));
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldAccessesInMethod(child, sourceCode, accessedFields, shortNameToFQN, localVars);
        }
    }
    
    /**
     * Check if an identifier is a method name (to avoid counting method names as fields).
     */
    private boolean isMethodName(TSNode identifierNode, TSNode parent) {
        String parentType = parent.getType();
        return "method_invocation".equals(parentType) || "method_declaration".equals(parentType);
    }
    
    /**
     * Identify iterable fields in a class. A field is iterable if:
     * 1. Its type is a collection (array, List, Set, etc.)
     * 2. It's a recursive field (field from a class to itself)
     * 3. It's in a class that contains at least one recursive field (e.g., item/value in Node)
     */
    private Set<String> identifyIterableFields(String classPath, String classSource, Set<String> allFields) {
        Set<String> iterableFields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        // Extract package name
        String packageName = extractPackageName(rootNode, classSource);
        
        // Build field type information
        Map<String, String> fieldTypes = extractFieldTypes(rootNode, classSource, packageName, "");
        
        // Identify recursive fields and classes with recursive fields
        Set<String> recursiveFields = new HashSet<>();
        Set<String> classesWithRecursiveFields = new HashSet<>();
        
        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldFQN = entry.getKey();
            String fieldType = entry.getValue();
            
            // Check if field is recursive (type matches its containing class)
            if (isRecursiveField(fieldFQN, fieldType)) {
                recursiveFields.add(fieldFQN);
                String classContext = extractClassContextFromFieldFQN(fieldFQN);
                classesWithRecursiveFields.add(classContext);
            }
        }
        
        // Classify fields as iterable
        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldFQN = entry.getKey();
            String fieldType = entry.getValue();
            
            // Check if field is a collection type
            if (isCollectionType(fieldType)) {
                iterableFields.add(fieldFQN);
            }
            // Check if field is recursive
            else if (recursiveFields.contains(fieldFQN)) {
                iterableFields.add(fieldFQN);
            }
            // Check if field is in a class with recursive fields
            else {
                String classContext = extractClassContextFromFieldFQN(fieldFQN);
                if (classesWithRecursiveFields.contains(classContext)) {
                    iterableFields.add(fieldFQN);
                }
            }
        }
        
        return iterableFields;
    }
    
    /**
     * Extract field types from the AST.
     * Returns a map from field FQN to field type name.
     */
    private Map<String, String> extractFieldTypes(TSNode node, String sourceCode, String packageName, String classContext) {
        Map<String, String> fieldTypes = new HashMap<>();
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String className = sourceCode.substring(startByte, endByte);
                
                String newClassContext = classContext.isEmpty() ? 
                    (packageName.isEmpty() ? className : packageName + "." + className) :
                    classContext + "." + className;
                
                // Process fields in this class
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if ("class_body".equals(child.getType())) {
                        fieldTypes.putAll(extractFieldTypes(child, sourceCode, packageName, newClassContext));
                    }
                }
            }
            return fieldTypes;
        } else if ("field_declaration".equals(nodeType)) {
            // Extract field type and name
            TSNode typeNode = null;
            List<String> fieldNames = new ArrayList<>();
            
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = node.getChild(i);
                String childType = child.getType();
                
                if ("type_identifier".equals(childType) || "array_type".equals(childType) || 
                    "generic_type".equals(childType) || "integral_type".equals(childType) ||
                    "floating_point_type".equals(childType) || "boolean_type".equals(childType)) {
                    typeNode = child;
                } else if ("variable_declarator".equals(childType)) {
                    TSNode identifier = child.getChild(0);
                    if (identifier != null && "identifier".equals(identifier.getType())) {
                        int startByte = identifier.getStartByte();
                        int endByte = identifier.getEndByte();
                        fieldNames.add(sourceCode.substring(startByte, endByte));
                    }
                }
            }
            
            if (typeNode != null && !classContext.isEmpty()) {
                String typeName = extractTypeName(typeNode, sourceCode);
                for (String fieldName : fieldNames) {
                    // classContext already includes package name, so don't prepend it again
                    String fqn = classContext + "." + fieldName;
                    fieldTypes.put(fqn, typeName);
                }
            }
            return fieldTypes;
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            fieldTypes.putAll(extractFieldTypes(child, sourceCode, packageName, classContext));
        }
        
        return fieldTypes;
    }
    
    /**
     * Extract the type name from a type node.
     */
    private String extractTypeName(TSNode typeNode, String sourceCode) {
        if ("type_identifier".equals(typeNode.getType())) {
            int startByte = typeNode.getStartByte();
            int endByte = typeNode.getEndByte();
            return sourceCode.substring(startByte, endByte);
        } else if ("array_type".equals(typeNode.getType())) {
            // For arrays, get the element type
            TSNode elementType = typeNode.getChild(0);
            if (elementType != null) {
                return extractTypeName(elementType, sourceCode) + "[]";
            }
        } else if ("generic_type".equals(typeNode.getType())) {
            TSNode identifier = typeNode.getChild(0);
            if (identifier != null && "type_identifier".equals(identifier.getType())) {
                int startByte = identifier.getStartByte();
                int endByte = identifier.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        
        // For primitive types, return the text directly
        int startByte = typeNode.getStartByte();
        int endByte = typeNode.getEndByte();
        return sourceCode.substring(startByte, endByte);
    }
    
    /**
     * Check if a field is recursive (its type matches its containing class name).
     */
    private boolean isRecursiveField(String fieldFQN, String fieldType) {
        String classContext = extractClassContextFromFieldFQN(fieldFQN);
        if (classContext.isEmpty()) {
            return false;
        }
        
        // Get the simple class name (last part)
        String simpleClassName = classContext.substring(classContext.lastIndexOf('.') + 1);
        
        // Check if the field type matches the class name
        return fieldType.equals(simpleClassName);
    }
    
    /**
     * Extract the class context from a field FQN.
     * E.g., "com.example.IntsList.Node.next" -> "com.example.IntsList.Node"
     */
    private String extractClassContextFromFieldFQN(String fieldFQN) {
        int lastDot = fieldFQN.lastIndexOf('.');
        if (lastDot > 0) {
            return fieldFQN.substring(0, lastDot);
        }
        return "";
    }
    
    /**
     * Check if a type is a collection type (array, List, Set, Collection, etc.).
     */
    private boolean isCollectionType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }
        
        // Check for array types
        if (typeName.endsWith("[]")) {
            return true;
        }
        
        // Check for common collection types
        return typeName.equals("List") || typeName.equals("Set") || 
               typeName.equals("Collection") || typeName.equals("ArrayList") ||
               typeName.equals("LinkedList") || typeName.equals("HashSet") ||
               typeName.equals("TreeSet") || typeName.equals("Vector") ||
               typeName.equals("Stack") || typeName.equals("Queue") ||
               typeName.equals("Deque") || typeName.equals("Map") ||
               typeName.equals("HashMap") || typeName.equals("TreeMap");
    }
    
    /**
     * Find fields that are iterated over in a method.
     * A field is considered iterated if it's accessed within a loop or in a recursive method call.
     */
    private Set<String> findIteratedFields(TSNode methodNode, String sourceCode, 
                                          Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        Set<String> iteratedFields = new HashSet<>();
        
        // Find fields accessed in loops
        findFieldsInLoops(methodNode, sourceCode, iteratedFields, shortNameToFQN, localVars);
        
        // Find fields accessed in recursive method calls
        // (methods that call themselves directly or indirectly)
        findFieldsInRecursiveCalls(methodNode, sourceCode, iteratedFields, shortNameToFQN, localVars);
        
        return iteratedFields;
    }
    
    /**
     * Find fields accessed within loop statements (while, for, enhanced-for).
     */
    private void findFieldsInLoops(TSNode node, String sourceCode, Set<String> iteratedFields,
                                   Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        String nodeType = node.getType();
        
        // Check if this is a loop node
        if ("while_statement".equals(nodeType) || "for_statement".equals(nodeType) || 
            "enhanced_for_statement".equals(nodeType) || "do_statement".equals(nodeType)) {
            // Find all field accesses within this loop
            Set<String> fieldsInLoop = new HashSet<>();
            findFieldAccessesInMethod(node, sourceCode, fieldsInLoop, shortNameToFQN, localVars);
            iteratedFields.addAll(fieldsInLoop);
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldsInLoops(child, sourceCode, iteratedFields, shortNameToFQN, localVars);
        }
    }
    
    /**
     * Find fields accessed in recursive method calls.
     * Detects methods that call themselves (direct recursion).
     */
    private void findFieldsInRecursiveCalls(TSNode methodNode, String sourceCode, Set<String> iteratedFields,
                                            Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        // Get the method name
        TSNode nameNode = methodNode.getChildByFieldName("name");
        if (nameNode == null || !"identifier".equals(nameNode.getType())) {
            return;
        }
        
        int startByte = nameNode.getStartByte();
        int endByte = nameNode.getEndByte();
        String methodName = sourceCode.substring(startByte, endByte);
        
        // Check if the method calls itself
        if (methodCallsItself(methodNode, sourceCode, methodName)) {
            // If the method is recursive, all fields it accesses are considered iterated
            Set<String> fieldsInMethod = new HashSet<>();
            findFieldAccessesInMethod(methodNode, sourceCode, fieldsInMethod, shortNameToFQN, localVars);
            iteratedFields.addAll(fieldsInMethod);
        }
    }
    
    /**
     * Check if a method calls itself (direct recursion).
     */
    private boolean methodCallsItself(TSNode methodNode, String sourceCode, String methodName) {
        return containsMethodCall(methodNode, sourceCode, methodName);
    }
    
    /**
     * Check if a node contains a method invocation with the given name.
     */
    private boolean containsMethodCall(TSNode node, String sourceCode, String methodName) {
        String nodeType = node.getType();
        
        if ("method_invocation".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                String name = sourceCode.substring(startByte, endByte);
                if (methodName.equals(name)) {
                    return true;
                }
            }
        }
        
        // Recursively check children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (containsMethodCall(child, sourceCode, methodName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Helper class to store target class information.
     */
    private static class TargetClassInfo {
        String className;
        String packageName;
        String fullyQualifiedName;
        
        TargetClassInfo(String className, String packageName) {
            this.className = className;
            this.packageName = packageName;
            this.fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;
        }
    }
    
    /**
     * Extract target class information (class name and package) from the target class file.
     */
    private TargetClassInfo extractTargetClassInfo(String classPath) {
        Path path = Paths.get(classPath);
        if (!Files.exists(path)) {
            return null;
        }
        
        try {
            String classSource = Files.readString(path);
            TSTree tree = parser.parseString(null, classSource);
            TSNode rootNode = tree.getRootNode();
            
            // Extract package name
            String packageName = extractPackageName(rootNode, classSource);
            
            // Extract class name
            String className = extractClassNameFromDeclaration(rootNode, classSource);
            if (className == null) {
                return null;
            }
            
            return new TargetClassInfo(className, packageName);
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Extract the class name from the class declaration.
     */
    private String extractClassNameFromDeclaration(TSNode node, String sourceCode) {
        String nodeType = node.getType();
        
        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                int startByte = nameNode.getStartByte();
                int endByte = nameNode.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        
        // Recursively search children (only look at the first level)
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            String className = extractClassNameFromDeclaration(child, sourceCode);
            if (className != null) {
                return className;
            }
        }
        
        return null;
    }
    
    /**
     * Extract variable types from the test case.
     * Returns a map from variable name to type (simple class name).
     */
    private Map<String, String> extractVariableTypes(String testCase) {
        Map<String, String> variableTypes = new HashMap<>();
        
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();
        
        findVariableDeclarations(rootNode, testCase, variableTypes);
        
        return variableTypes;
    }
    
    /**
     * Find variable declarations and extract their types.
     */
    private void findVariableDeclarations(TSNode node, String sourceCode, Map<String, String> variableTypes) {
        if (node == null) {
            return;
        }
        
        String nodeType = node.getType();
        
        if ("local_variable_declaration".equals(nodeType)) {
            // Extract type
            TSNode typeNode = node.getChildByFieldName("type");
            if (typeNode != null) {
                String type = extractTypeFromNode(typeNode, sourceCode);
                
                // Find all variable declarators
                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if (child != null && "variable_declarator".equals(child.getType())) {
                        TSNode nameNode = child.getChildCount() > 0 ? child.getChild(0) : null;
                        if (nameNode != null && "identifier".equals(nameNode.getType())) {
                            int startByte = nameNode.getStartByte();
                            int endByte = nameNode.getEndByte();
                            String varName = sourceCode.substring(startByte, endByte);
                            if (type != null) {
                                variableTypes.put(varName, type);
                            }
                        }
                    }
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findVariableDeclarations(child, sourceCode, variableTypes);
            }
        }
    }
    
    /**
     * Extract type from a type node.
     */
    private String extractTypeFromNode(TSNode typeNode, String sourceCode) {
        if (typeNode == null) {
            return null;
        }
        
        String nodeType = typeNode.getType();
        
        if ("type_identifier".equals(nodeType)) {
            int startByte = typeNode.getStartByte();
            int endByte = typeNode.getEndByte();
            return sourceCode.substring(startByte, endByte);
        } else if ("generic_type".equals(nodeType)) {
            // For generic types like List<String>, extract just the base type
            TSNode typeIdentifier = typeNode.getChildCount() > 0 ? typeNode.getChild(0) : null;
            if (typeIdentifier != null && "type_identifier".equals(typeIdentifier.getType())) {
                int startByte = typeIdentifier.getStartByte();
                int endByte = typeIdentifier.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        
        return null;
    }
    
    /**
     * Extract package name from test case.
     */
    private String extractPackageNameFromTestCase(String testCase) {
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();
        return extractPackageName(rootNode, testCase);
    }
    
    /**
     * Find method invocations with type checking to ensure they belong to the target class.
     * Note: We use a conservative approach - if we cannot identify the variable (e.g., for
     * chained method calls like r.createList().method()), we allow the field access rather
     * than rejecting it. This trade-off:
     * - Prevents false negatives (important for not under-reporting coverage)
     * - May allow some false positives for complex chained calls
     * - Maintains backward compatibility with existing tests
     * The primary goal is to filter out clearly different classes (different package/name),
     * not to achieve perfect type inference.
     */
    private void findMethodInvocationsWithTypeCheck(TSNode node, String sourceCode, 
                                                     Map<String, String> variableTypes,
                                                     TargetClassInfo targetClassInfo,
                                                     String testPackage,
                                                     Set<String> accessedFields,
                                                     String classPath) {
        if (node == null) {
            return;
        }
        
        String nodeType = node.getType();
        
        if ("method_invocation".equals(nodeType)) {
            // Get the object on which the method is called
            TSNode objectNode = node.getChildByFieldName("object");
            if (objectNode != null) {
                String variableName = extractIdentifierFromExpression(objectNode, sourceCode);
                // If we can identify the variable, check its type
                // If we can't (like for method chains), allow it (conservative approach)
                if (variableName == null || isTargetClassType(variableName, variableTypes, targetClassInfo, testPackage)) {
                    // Get the method name
                    TSNode nameNode = node.getChildByFieldName("name");
                    if (nameNode != null && "identifier".equals(nameNode.getType())) {
                        int startByte = nameNode.getStartByte();
                        int endByte = nameNode.getEndByte();
                        String methodName = sourceCode.substring(startByte, endByte);
                        
                        // Get fields accessed by this method
                        Set<String> methodFields = getFieldsAccessedByMethod(classPath, methodName);
                        accessedFields.addAll(methodFields);
                    }
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findMethodInvocationsWithTypeCheck(child, sourceCode, variableTypes, targetClassInfo, 
                                                  testPackage, accessedFields, classPath);
            }
        }
    }
    
    /**
     * Find direct field accesses with type checking.
     */
    private void findDirectFieldAccessesWithTypeCheck(TSNode node, String sourceCode, 
                                                      Set<String> fields, Set<String> allFields,
                                                      Map<String, String> variableTypes,
                                                      TargetClassInfo targetClassInfo,
                                                      String testPackage) {
        if (node == null) {
            return;
        }
        
        String nodeType = node.getType();
        
        if ("field_access".equals(nodeType)) {
            // Get the object on which the field is accessed
            TSNode objectNode = node.getChildByFieldName("object");
            if (objectNode != null) {
                String variableName = extractIdentifierFromExpression(objectNode, sourceCode);
                // If we can identify the variable, check its type
                // If we can't (like for method chains), allow it (conservative approach)
                if (variableName == null || isTargetClassType(variableName, variableTypes, targetClassInfo, testPackage)) {
                    TSNode fieldNode = node.getChildByFieldName("field");
                    if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                        int startByte = fieldNode.getStartByte();
                        int endByte = fieldNode.getEndByte();
                        String fieldName = sourceCode.substring(startByte, endByte);
                        
                        // Map short field name to FQN
                        String fqn = mapFieldNameToFQN(fieldName, allFields);
                        if (fqn != null) {
                            fields.add(fqn);
                        }
                    }
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findDirectFieldAccessesWithTypeCheck(child, sourceCode, fields, allFields, 
                                                    variableTypes, targetClassInfo, testPackage);
            }
        }
    }
    
    /**
     * Extract identifier from an expression (handles simple identifiers and chained calls).
     */
    private String extractIdentifierFromExpression(TSNode node, String sourceCode) {
        if (node == null) {
            return null;
        }
        
        try {
            String nodeType = node.getType();
            
            if ("identifier".equals(nodeType)) {
                int startByte = node.getStartByte();
                int endByte = node.getEndByte();
                return sourceCode.substring(startByte, endByte);
            } else if ("method_invocation".equals(nodeType) || "field_access".equals(nodeType)) {
                // For chained calls like r.createStackedValueList(...), we don't know the type
                // so we can't verify it. Return null to skip type checking.
                return null;
            }
        } catch (Exception e) {
            // Handle Tree-sitter null node exceptions
            return null;
        }
        
        return null;
    }
    
    /**
     * Check if a variable is of the target class type.
     * If the variable type doesn't specify a package, the matching behavior depends on
     * whether the test declares a package:
     * - If the test has no package declaration, we assume the test is in the same package
     *   as the target class (so a variable of type "IntsList" matches target "com.example.IntsList")
     * - If the test declares a different package, then the types don't match
     */
    private boolean isTargetClassType(String variableName, Map<String, String> variableTypes, 
                                     TargetClassInfo targetClassInfo, String testPackage) {
        String varType = variableTypes.get(variableName);
        if (varType == null) {
            // Variable not found, might be a parameter or field - be conservative and allow it
            return true;
        }
        
        // Check if the variable type matches the target class name
        if (!varType.equals(targetClassInfo.className)) {
            return false;
        }
        
        // If the target class has no package, any variable with matching simple name is okay
        if (targetClassInfo.packageName.isEmpty()) {
            return true;
        }
        
        // If test has no package declaration, assume the test is in the same package as the target.
        // This implements the requirement: "If the test class does not mention the package 
        // of the target class, assume as target package the package of the test class."
        // In this case, testPackage is empty, so we assume it matches the target package.
        if (testPackage.isEmpty()) {
            return true;
        }
        
        // Both have packages - they should match
        return targetClassInfo.packageName.equals(testPackage);
    }
}
