package org.tori.metrics.sfc;

import java.util.Objects;

/**
 * Intermediate representation of a field that contributes to state field coverage.
 *
 * <p>A {@code TargetField} captures the fully-qualified name of a field together with
 * a flag indicating whether this entry represents an <em>iterable variant</em> of the
 * field (i.e. the field is iterable/traversed and therefore carries a {@code "+"} label
 * in the coverage computation).
 *
 * <p>Language-specific implementations (e.g. {@link StateFieldCoverageJava}) produce
 * {@code TargetField} instances that the language-agnostic {@link StateFieldCoverage}
 * abstract class uses to compute the actual coverage score.
 */
public final class TargetField {

    private final String qualifiedName;
    private final boolean iterableVariant;

    /**
     * Creates a non-iterable {@code TargetField}.
     *
     * @param qualifiedName fully-qualified field name (e.g. {@code "com.example.IntsList.size"})
     */
    public TargetField(String qualifiedName) {
        this(qualifiedName, false);
    }

    /**
     * Creates a {@code TargetField} with an explicit iterable-variant flag.
     *
     * @param qualifiedName   fully-qualified field name
     * @param iterableVariant {@code true} if this entry represents the iterable (+) variant
     */
    public TargetField(String qualifiedName, boolean iterableVariant) {
        this.qualifiedName = Objects.requireNonNull(qualifiedName, "qualifiedName must not be null");
        this.iterableVariant = iterableVariant;
    }

    /**
     * Returns the fully-qualified name of the field, without any iterable suffix.
     *
     * @return fully-qualified field name
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Returns {@code true} if this entry represents the iterable (+) variant of the field.
     *
     * @return {@code true} for iterable-variant entries
     */
    public boolean isIterableVariant() {
        return iterableVariant;
    }

    /**
     * Returns the label used in coverage computation.
     * For regular fields this is the {@link #getQualifiedName() qualified name};
     * for iterable-variant fields it is the qualified name followed by {@code "+"}.
     *
     * @return coverage label
     */
    public String toLabel() {
        return iterableVariant ? qualifiedName + "+" : qualifiedName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetField)) return false;
        TargetField that = (TargetField) o;
        return iterableVariant == that.iterableVariant && qualifiedName.equals(that.qualifiedName);
    }

    @Override
    public int hashCode() {
        // 31 is a conventional prime used in hash code computation (good distribution, JIT-friendly)
        return 31 * qualifiedName.hashCode() + (iterableVariant ? 1 : 0);
    }

    /**
     * Returns the same value as {@link #toLabel()}.
     */
    @Override
    public String toString() {
        return toLabel();
    }
}
