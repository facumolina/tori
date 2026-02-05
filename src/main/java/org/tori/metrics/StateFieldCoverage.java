package org.tori.metrics;

import org.treesitter.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private String targetClassPath;
    private ExecutionLevel executionLevel;
    
    // Store detailed information for the last assessment
    private Set<String> lastTargetFields;
    private Set<String> lastAccessedFields;
    private boolean detailedReportingEnabled;
    
    public StateFieldCoverage() {
        this.javaLanguage = new TreeSitterJava();
        this.parser = new TSParser();
        parser.setLanguage(javaLanguage);
        this.classFieldsCache = new HashMap<>();
        this.methodFieldAccessCache = new HashMap<>();
        this.targetClassPath = null;
        this.executionLevel = ExecutionLevel.ASSERT;
        this.lastTargetFields = new HashSet<>();
        this.lastAccessedFields = new HashSet<>();
        this.detailedReportingEnabled = true;
    }
    
    /**
     * Configure the metric with properties.
     * Expected property: target_class - the path to the target class file
     * 
     * @param config Properties object containing metric configuration
     */
    @Override
    public void configure(Properties config) {
        this.targetClassPath = config.getProperty("target_class");
        if (this.targetClassPath == null || this.targetClassPath.isEmpty()) {
            throw new IllegalArgumentException("Configuration must contain 'target_class' property with the path to the target class");
        }
        
        // Configure execution level
        String execLevelValue = config.getProperty("exec_level");
        if (execLevelValue != null && !execLevelValue.isEmpty()) {
            this.executionLevel = ExecutionLevel.fromConfigValue(execLevelValue);
        }
        
        // Print target field information when configured
        if (detailedReportingEnabled) {
            Set<String> allFields = getAllFieldsInClass(targetClassPath);
            System.out.println("Target class: " + targetClassPath);
            System.out.println("Total target fields: " + allFields.size() + " " + allFields);
            System.out.println("Execution level: " + executionLevel.getConfigValue());
            System.out.println();
        }
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
        // Use configured target class path if available, otherwise extract from test case
        String classPath = targetClassPath;
        if (classPath == null || classPath.isEmpty()) {
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
            classPath = "src/test/resources/" + className + ".java";
        }
        
        // Get all fields in the target class
        Set<String> allFields = getAllFieldsInClass(classPath);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }
        
        // Get fields accessed by the oracle
        Set<String> accessedFields = getFieldsAccessedByOracle(testCase, oracle, classPath);
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
        
        // Use configured target class path if available
        String classPath = targetClassPath;
        if (classPath == null || classPath.isEmpty()) {
            String className = extractTargetClassName(testCase);
            if (className == null || className.isEmpty()) {
                lastTargetFields = new HashSet<>();
                lastAccessedFields = new HashSet<>();
                return 0.0;
            }
            classPath = "src/test/resources/" + className + ".java";
        }
        
        // Get all fields in the target class
        Set<String> allFields = getAllFieldsInClass(classPath);
        lastTargetFields = new HashSet<>(allFields);
        if (allFields.isEmpty()) {
            lastAccessedFields = new HashSet<>();
            return 0.0;
        }
        
        // Compute the union of all fields accessed by any oracle
        Set<String> unionAccessedFields = new HashSet<>();
        for (String oracle : oracles) {
            Set<String> oracleFields = getFieldsAccessedByOracle(testCase, oracle, classPath);
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
     */
    private Set<String> getAllFieldsInClass(String classPath) {
        if (classFieldsCache.containsKey(classPath)) {
            return classFieldsCache.get(classPath);
        }
        
        Set<String> fields = new HashSet<>();
        
        Path path = Paths.get(classPath);
        if (Files.exists(path)) {
            try {
                String classSource = Files.readString(path);
                fields = extractFieldsFromClass(classSource);
            } catch (IOException e) {
                // Failed to read class file
            }
        }
        
        classFieldsCache.put(classPath, fields);
        return fields;
    }
    
    /**
     * Extract all field names from a class, including inner classes.
     */
    private Set<String> extractFieldsFromClass(String classSource) {
        Set<String> fields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        findFieldDeclarations(rootNode, classSource, fields);
        
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
     * Get the fields accessed by an oracle statement.
     * This traces method calls to determine which fields are accessed.
     */
    private Set<String> getFieldsAccessedByOracle(String testCase, String oracle, String classPath) {
        Set<String> accessedFields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, oracle);
        TSNode rootNode = tree.getRootNode();
        
        // Find all method invocations in the oracle
        Set<String> methodCalls = new HashSet<>();
        findMethodInvocations(rootNode, oracle, methodCalls);
        
        // For each method call, determine which fields it accesses
        for (String methodName : methodCalls) {
            Set<String> methodFields = getFieldsAccessedByMethod(classPath, methodName);
            accessedFields.addAll(methodFields);
        }
        
        // Also check for direct field accesses in the oracle
        findDirectFieldAccesses(rootNode, oracle, accessedFields);
        
        return accessedFields;
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
     */
    private void findDirectFieldAccesses(TSNode node, String sourceCode, Set<String> fields) {
        String nodeType = node.getType();
        
        if ("field_access".equals(nodeType)) {
            TSNode fieldNode = node.getChildByFieldName("field");
            if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                int startByte = fieldNode.getStartByte();
                int endByte = fieldNode.getEndByte();
                String fieldName = sourceCode.substring(startByte, endByte);
                fields.add(fieldName);
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findDirectFieldAccesses(child, sourceCode, fields);
        }
    }
    
    /**
     * Get the fields accessed by a specific method in a class.
     * This analyzes the method body to determine which fields are accessed.
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
                accessedFields = extractFieldAccessFromMethod(classSource, methodName);
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
     * Extract which fields are accessed by a specific method.
     */
    private Set<String> extractFieldAccessFromMethod(String classSource, String methodName) {
        Set<String> accessedFields = new HashSet<>();
        
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        
        // First, get all fields in the class
        Set<String> allFields = new HashSet<>();
        findFieldDeclarations(rootNode, classSource, allFields);
        
        // Find the method declaration
        TSNode methodNode = findMethodDeclaration(rootNode, classSource, methodName);
        if (methodNode == null) {
            return accessedFields;
        }
        
        // Get local variables declared in the method
        Set<String> localVars = new HashSet<>();
        findLocalVariables(methodNode, classSource, localVars);
        
        // Find all identifier and field access nodes in the method
        findFieldAccessesInMethod(methodNode, classSource, accessedFields, allFields, localVars);
        
        return accessedFields;
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
     */
    private void findFieldAccessesInMethod(TSNode node, String sourceCode, Set<String> accessedFields, 
                                           Set<String> allFields, Set<String> localVars) {
        String nodeType = node.getType();
        
        // Look for explicit field access patterns (obj.field)
        if ("field_access".equals(nodeType)) {
            TSNode fieldNode = node.getChildByFieldName("field");
            if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                int startByte = fieldNode.getStartByte();
                int endByte = fieldNode.getEndByte();
                String fieldName = sourceCode.substring(startByte, endByte);
                // Only add if it's actually a field in the class
                if (allFields.contains(fieldName)) {
                    accessedFields.add(fieldName);
                }
            }
        } else if ("identifier".equals(nodeType)) {
            // Check if this identifier is a field (not a local variable or parameter)
            TSNode parent = node.getParent();
            if (parent != null && !isMethodName(node, parent)) {
                int startByte = node.getStartByte();
                int endByte = node.getEndByte();
                String name = sourceCode.substring(startByte, endByte);
                
                // It's a field if it's in allFields and NOT in localVars
                if (allFields.contains(name) && !localVars.contains(name)) {
                    accessedFields.add(name);
                }
            }
        }
        
        // Recursively process children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldAccessesInMethod(child, sourceCode, accessedFields, allFields, localVars);
        }
    }
    
    /**
     * Check if an identifier is a method name (to avoid counting method names as fields).
     */
    private boolean isMethodName(TSNode identifierNode, TSNode parent) {
        String parentType = parent.getType();
        return "method_invocation".equals(parentType) || "method_declaration".equals(parentType);
    }
}
