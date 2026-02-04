package org.tori.metrics;

import org.treesitter.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Metric that measures the proportion of variables declared in the test that are actually checked in the assertion.
 * This metric evaluates how comprehensively an oracle tests the declared variables.
 */
public class CheckedVarsMetric implements Metric {
    private final TSParser parser;
    private final TSLanguage javaLanguage;

    public CheckedVarsMetric() {
        // Initialize Tree-sitter parser for Java
        this.javaLanguage = new TreeSitterJava();
        this.parser = new TSParser();
        parser.setLanguage(javaLanguage);
    }

    /**
     * Assess the quality of an oracle by computing the proportion of declared variables
     * that are checked in the assertion.
     *
     * @param testCase The full source code of the test case (method body)
     * @param oracle The oracle (assertion statement) to assess
     * @return The proportion of declared variables that are checked (0.0 to 1.0)
     */
    @Override
    public double assess(String testCase, String oracle) {
        // Extract declared variables from test case
        Set<String> declaredVars = extractDeclaredVariables(testCase);
        
        if (declaredVars.isEmpty()) {
            // If no variables are declared, return 0.0
            return 0.0;
        }
        
        // Extract variables used in the oracle
        Set<String> checkedVars = extractUsedVariables(oracle);
        
        // Count how many declared variables are checked
        int checkedCount = 0;
        for (String var : declaredVars) {
            if (checkedVars.contains(var)) {
                checkedCount++;
            }
        }
        
        // Return the proportion
        return (double) checkedCount / declaredVars.size();
    }

    /**
     * Extract all declared variables from the test case source code.
     */
    private Set<String> extractDeclaredVariables(String sourceCode) {
        Set<String> variables = new HashSet<>();
        
        TSTree tree = parser.parseString(null, sourceCode);
        TSNode rootNode = tree.getRootNode();
        
        findVariableDeclarations(rootNode, sourceCode, variables);
        
        return variables;
    }

    /**
     * Recursively find all variable declarations in the AST.
     */
    private void findVariableDeclarations(TSNode node, String sourceCode, Set<String> variables) {
        String nodeType = node.getType();
        
        // Check for local variable declarations
        if ("local_variable_declaration".equals(nodeType)) {
            extractVariableNames(node, sourceCode, variables);
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findVariableDeclarations(child, sourceCode, variables);
        }
    }

    /**
     * Extract variable names from a variable declaration node.
     */
    private void extractVariableNames(TSNode declarationNode, String sourceCode, Set<String> variables) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                // Get the identifier (variable name)
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
     * Extract all variables used in the oracle (assertion).
     */
    private Set<String> extractUsedVariables(String sourceCode) {
        Set<String> variables = new HashSet<>();
        
        TSTree tree = parser.parseString(null, sourceCode);
        TSNode rootNode = tree.getRootNode();
        
        findUsedIdentifiers(rootNode, sourceCode, variables);
        
        return variables;
    }

    /**
     * Recursively find all identifiers used in the AST.
     */
    private void findUsedIdentifiers(TSNode node, String sourceCode, Set<String> variables) {
        String nodeType = node.getType();
        
        // Add identifiers that are not part of method invocations (method names)
        if ("identifier".equals(nodeType)) {
            TSNode parent = node.getParent();
            if (parent != null) {
                String parentType = parent.getType();
                // Skip if this identifier is a method name in a method invocation
                if (!"method_invocation".equals(parentType) || 
                    (parent.getChildCount() > 0 && parent.getChild(0) != node)) {
                    int startByte = node.getStartByte();
                    int endByte = node.getEndByte();
                    String varName = sourceCode.substring(startByte, endByte);
                    variables.add(varName);
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findUsedIdentifiers(child, sourceCode, variables);
        }
    }
}
