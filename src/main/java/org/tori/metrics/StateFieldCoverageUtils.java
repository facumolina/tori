package org.tori.metrics;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Utility methods shared across language-specific implementations of
 * {@link StateFieldCoverage}.
 *
 * <p>All methods are stateless and operate purely on strings, making them
 * reusable by any future language implementation (e.g., Python, C#).
 */
final class StateFieldCoverageUtils {

    private StateFieldCoverageUtils() {
        // utility class — no instances
    }

    // -------------------------------------------------------------------------
    // FQN string utilities
    // -------------------------------------------------------------------------

    /**
     * Returns the simple (short) field name from a fully-qualified field name.
     * For example, {@code "org.example.MyClass.myField"} returns {@code "myField"}.
     */
    static String extractShortFieldName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fqn.length() - 1) {
            return fqn.substring(lastDot + 1);
        }
        return fqn;
    }

    /**
     * Returns the class context (everything before the last {@code '.'}) from a
     * fully-qualified field name.
     * For example, {@code "org.example.MyClass.myField"} returns
     * {@code "org.example.MyClass"}.
     */
    static String extractClassContextFromFieldFQN(String fieldFQN) {
        int lastDot = fieldFQN.lastIndexOf('.');
        if (lastDot > 0) {
            return fieldFQN.substring(0, lastDot);
        }
        return "";
    }

    /**
     * Returns {@code true} if {@code fieldFQN} belongs directly to
     * {@code classContext} or to one of its inner classes (i.e. the field's
     * FQN starts with {@code classContext + "."}).
     */
    static boolean isFieldInClassOrInnerClass(String fieldFQN, String classContext) {
        if (classContext.isEmpty()) {
            return false;
        }
        String prefix = classContext + ".";
        return fieldFQN.startsWith(prefix) && fieldFQN.length() > prefix.length();
    }

    /**
     * Finds the fully-qualified field name whose simple name matches
     * {@code shortName}, or {@code null} if none is found.
     */
    static String mapFieldNameToFQN(String shortName, Set<String> allFields) {
        for (String fqn : allFields) {
            if (fqn.endsWith("." + shortName)) {
                return fqn;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the field's type matches the simple name of the
     * class that declares it, indicating that the field is a recursive
     * (self-referential) reference.
     */
    static boolean isRecursiveField(String fieldFQN, String fieldType) {
        String classContext = extractClassContextFromFieldFQN(fieldFQN);
        if (classContext.isEmpty()) {
            return false;
        }
        String simpleClassName = classContext.substring(classContext.lastIndexOf('.') + 1);
        return fieldType.equals(simpleClassName);
    }

    // -------------------------------------------------------------------------
    // Tree-sitter byte-offset helper
    // -------------------------------------------------------------------------

    /**
     * Extracts a substring using UTF-8 byte offsets as returned by Tree-sitter.
     *
     * <p>Tree-sitter reports positions as byte offsets in the UTF-8 encoding of
     * the source, while Java's {@link String#substring(int, int)} operates on
     * UTF-16 code-unit indices. This method bridges the two representations.
     */
    static String byteSubstring(String source, int startByte, int endByte) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }
}
