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
     */
    private Set<String> getFieldsAccessedByOracle(String testCase, String oracle, String classPath) {
        Set<String> accessedFields = new HashSet<>();
        
        // Get all fields in the target class (with FQN)
        Set<String> allFields = getAllFieldsInClass(classPath);
        
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
        findDirectFieldAccesses(rootNode, oracle, accessedFields, allFields);
        
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
}
