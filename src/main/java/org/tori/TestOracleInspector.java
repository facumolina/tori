package org.tori;

import org.treesitter.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspector that uses Tree-sitter to parse Java test files and find assertion statements.
 */
public class TestOracleInspector {
    private final TSParser parser;
    private final TSLanguage javaLanguage;

    public TestOracleInspector() {
        // Initialize Tree-sitter parser for Java
        this.javaLanguage = new TreeSitterJava();
        this.parser = new TSParser();
        parser.setLanguage(javaLanguage);
    }

    /**
     * Find oracles (assertions) in the given source code.
     *
     * @param sourceCode The Java source code to parse
     * @param targetMethodName The specific method name to search (null for all methods)
     * @return List of MethodOracles containing method names and their assertions
     */
    public List<MethodOracles> findOracles(String sourceCode, String targetMethodName) {
        List<MethodOracles> results = new ArrayList<>();
        
        // Parse the source code
        TSTree tree = parser.parseString(null, sourceCode);
        TSNode rootNode = tree.getRootNode();

        // Find all method declarations
        findMethodsWithOracles(rootNode, sourceCode, targetMethodName, results);

        return results;
    }

    /**
     * Recursively traverse the AST to find methods and their assertions.
     */
    private void findMethodsWithOracles(TSNode node, String sourceCode, String targetMethodName, List<MethodOracles> results) {
        String nodeType = node.getType();

        // Check if this is a method declaration
        if ("method_declaration".equals(nodeType)) {
            processMethodDeclaration(node, sourceCode, targetMethodName, results);
        }

        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findMethodsWithOracles(child, sourceCode, targetMethodName, results);
        }
    }

    /**
     * Process a method declaration node to extract method name and assertions.
     */
    private void processMethodDeclaration(TSNode methodNode, String sourceCode, String targetMethodName, List<MethodOracles> results) {
        // Get method name
        String methodName = extractMethodName(methodNode, sourceCode);
        
        if (methodName == null) {
            return;
        }

        // If a target method is specified, skip methods that don't match
        if (targetMethodName != null && !methodName.equals(targetMethodName)) {
            return;
        }

        // Find all assertions in this method
        List<String> oracles = new ArrayList<>();
        findAssertions(methodNode, sourceCode, oracles);

        // Add to results
        results.add(new MethodOracles(methodName, oracles));
    }

    /**
     * Extract the method name from a method declaration node.
     */
    private String extractMethodName(TSNode methodNode, String sourceCode) {
        int childCount = methodNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodNode.getChild(i);
            if ("identifier".equals(child.getType())) {
                int startByte = child.getStartByte();
                int endByte = child.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        return null;
    }

    /**
     * Recursively find all assertion statements in a node.
     */
    private void findAssertions(TSNode node, String sourceCode, List<String> oracles) {
        String nodeType = node.getType();

        // Check if this is a method invocation (potential assertion)
        if ("method_invocation".equals(nodeType)) {
            String methodCall = extractMethodInvocation(node, sourceCode);
            if (methodCall != null && isAssertionMethod(methodCall)) {
                // Get the full assertion statement
                TSNode statement = findStatementNode(node);
                if (statement != null) {
                    int startByte = statement.getStartByte();
                    int endByte = statement.getEndByte();
                    String assertionText = sourceCode.substring(startByte, endByte).trim();
                    oracles.add(assertionText);
                }
            }
        }

        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findAssertions(child, sourceCode, oracles);
        }
    }

    /**
     * Extract the method name from a method invocation.
     */
    private String extractMethodInvocation(TSNode node, String sourceCode) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if ("identifier".equals(child.getType())) {
                int startByte = child.getStartByte();
                int endByte = child.getEndByte();
                return sourceCode.substring(startByte, endByte);
            }
        }
        return null;
    }

    /**
     * Check if a method name is an assertion method.
     */
    private boolean isAssertionMethod(String methodName) {
        // Common JUnit assertion methods
        return methodName.startsWith("assert") || methodName.equals("fail");
    }

    /**
     * Find the parent statement node (expression_statement) for a given node.
     */
    private TSNode findStatementNode(TSNode node) {
        TSNode current = node;
        while (current != null) {
            String type = current.getType();
            if ("expression_statement".equals(type) || "assert_statement".equals(type)) {
                return current;
            }
            current = current.getParent();
        }
        return node; // Fallback to the node itself
    }
}
