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
import java.util.Set;

/**
 * Java-specific implementation of {@link StateFieldCoverage}.
 *
 * <p>This class handles all Java-specific concerns for computing state field coverage:
 * <ul>
 *   <li>Parsing Java source files using Tree-sitter</li>
 *   <li>Identifying Java fields and their types (including inheritance hierarchies)</li>
 *   <li>Resolving method bodies and their field accesses</li>
 *   <li>Package and import resolution for cross-file class references</li>
 * </ul>
 *
 * <p>It also implements the {@link Metric} interface so it can be used directly in the
 * tori framework. The core coverage computation is inherited from {@link StateFieldCoverage}.
 */
public class StateFieldCoverageJava extends StateFieldCoverage {

    private final TSParser parser;
    private final TSLanguage javaLanguage;
    private final Map<String, Set<String>> classFieldsCache;
    private final Map<String, Map<String, Set<String>>> methodFieldAccessCache;
    private final Map<String, Set<String>> iterableFieldsCache;
    private final Map<String, Set<String>> methodIterationCache;

    public StateFieldCoverageJava() {
        super();
        this.javaLanguage = new TreeSitterJava();
        this.parser = new TSParser();
        parser.setLanguage(javaLanguage);
        this.classFieldsCache = new HashMap<>();
        this.methodFieldAccessCache = new HashMap<>();
        this.iterableFieldsCache = new HashMap<>();
        this.methodIterationCache = new HashMap<>();
    }

    // -------------------------------------------------------------------------
    // StateFieldCoverage abstract method implementations
    // -------------------------------------------------------------------------

    @Override
    protected void validateTargetClassPaths(List<String> paths) {
        for (String classPath : paths) {
            Path path = Paths.get(classPath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Target class file does not exist: " + classPath);
            }
            if (!classPath.endsWith(".java")) {
                throw new IllegalArgumentException(
                        "Target class must be a Java file (.java extension): " + classPath);
            }
        }
    }

    @Override
    protected Set<TargetField> computeAllTargetFields(List<String> classPaths) {
        return toTargetFields(getAllFieldsInTargetClassesInternal(classPaths));
    }

    @Override
    protected Set<TargetField> computeAccessedFields(String testCase, String oracle,
                                                      List<String> classPaths) {
        return toTargetFields(getFieldsAccessedByOracleInTargetClassesInternal(testCase, oracle, classPaths));
    }

    @Override
    protected List<String> resolveFallbackClassPaths(String testCase) {
        String className = extractTargetClassName(testCase);
        if (className == null || className.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> fallback = new ArrayList<>();
        // Note: The hardcoded 'src/test/resources/' path is intentional as this metric is designed
        // to work with test resources. For production use, always provide a configuration file.
        fallback.add("src/test/resources/" + className + ".java");
        return fallback;
    }

    @Override
    protected void clearCaches() {
        this.classFieldsCache.clear();
        this.methodFieldAccessCache.clear();
        this.iterableFieldsCache.clear();
        this.methodIterationCache.clear();
    }

    // -------------------------------------------------------------------------
    // Internal Java field/method analysis (all work with Set<String> FQNs)
    // -------------------------------------------------------------------------

    private Set<String> getAllFieldsInTargetClassesInternal(List<String> classPaths) {
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

    private Set<String> getFieldsAccessedByOracleInTargetClassesInternal(String testCase, String oracle,
                                                                           List<String> classPaths) {
        Set<String> allAccessedFields = new HashSet<>();
        for (String classPath : classPaths) {
            Set<String> classAccessedFields = getFieldsAccessedByOracle(testCase, oracle, classPath);
            allAccessedFields.addAll(classAccessedFields);
        }
        return allAccessedFields;
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

        Set<String> visitedClasses = new HashSet<>();
        Set<String> fields = getAllFieldsInClassRecursive(classPath, visitedClasses, true);

        classFieldsCache.put(classPath, fields);
        return fields;
    }

    private Set<String> getAllFieldsInClassRecursive(String classPath, Set<String> visitedClasses,
                                                      boolean isRootClass) {
        return getAllFieldsInClassRecursive(classPath, visitedClasses, isRootClass, null, null);
    }

    private Set<String> getAllFieldsInClassRecursive(String classPath, Set<String> visitedClasses,
                                                      boolean isRootClass,
                                                      String concreteClassName,
                                                      String concretePackageName) {
        if (visitedClasses.contains(classPath)) {
            return new HashSet<>();
        }
        visitedClasses.add(classPath);

        Set<String> fields = new HashSet<>();

        Path path = Paths.get(classPath);
        if (Files.exists(path)) {
            try {
                String classSource = Files.readString(path);

                TSTree tree = parser.parseString(null, classSource);
                TSNode rootNode = tree.getRootNode();
                String packageName = extractPackageName(rootNode, classSource);

                if (concreteClassName != null) {
                    fields = extractFieldsFromClassWithOverride(classSource, concreteClassName,
                            concretePackageName);
                } else {
                    fields = extractFieldsFromClass(classSource);
                }

                String classNameForParent = concreteClassName;
                String packageNameForParent = concretePackageName;
                if (isRootClass || concreteClassName == null) {
                    classNameForParent = findTopLevelClassName(rootNode, classSource);
                    packageNameForParent = packageName;
                }

                Map<String, String> fieldTypes = extractFieldTypes(rootNode, classSource, packageName, "");

                Set<String> referencedClassPaths = findReferencedClassPaths(classPath, classSource,
                        fieldTypes, isRootClass);

                for (String referencedClassPath : referencedClassPaths) {
                    Set<String> referencedFields = getAllFieldsInClassRecursive(referencedClassPath,
                            new HashSet<>(visitedClasses), false);
                    fields.addAll(referencedFields);
                }

                String superclassName = findTopLevelSuperclassName(rootNode, classSource);
                if (superclassName != null) {
                    Map<String, String> imports = extractImports(rootNode, classSource);
                    String parentClassPath = resolveClassPath(superclassName, packageName, imports,
                            path.normalize());
                    if (parentClassPath != null) {
                        Set<String> parentFields = getAllFieldsInClassRecursive(parentClassPath,
                                visitedClasses, false, classNameForParent, packageNameForParent);
                        fields.addAll(parentFields);

                        if (includeConcreteParentClassFields && !isClassAbstract(parentClassPath)) {
                            Set<String> parentOwnFields = getAllFieldsInClassRecursive(parentClassPath,
                                    new HashSet<>(), true, null, null);
                            fields.addAll(parentOwnFields);
                        }
                    }
                }

                if (iterableFieldTrackingEnabled) {
                    Set<String> iterableFields = identifyIterableFields(classPath, classSource, fields);
                    iterableFieldsCache.put(classPath, iterableFields);

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

    private String findTopLevelSuperclassName(TSNode rootNode, String sourceCode) {
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("class_declaration".equals(child.getType())) {
                try {
                    TSNode superclassNode = child.getChildByFieldName("superclass");
                    if (superclassNode != null) {
                        int superChildCount = superclassNode.getChildCount();
                        for (int j = 0; j < superChildCount; j++) {
                            TSNode superChild = superclassNode.getChild(j);
                            if ("type_identifier".equals(superChild.getType())) {
                                return StateFieldCoverageUtils.byteSubstring(sourceCode, superChild.getStartByte(),
                                        superChild.getEndByte());
                            }
                        }
                    }
                } catch (Exception e) {
                    // No superclass or unable to extract it
                }
                return null;
            }
        }
        return null;
    }

    private boolean isClassAbstract(String classPath) {
        Path path = Paths.get(classPath);
        if (!Files.exists(path)) {
            return false;
        }
        try {
            String classSource = Files.readString(path);
            TSTree tree = parser.parseString(null, classSource);
            TSNode rootNode = tree.getRootNode();
            int childCount = rootNode.getChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = rootNode.getChild(i);
                if ("class_declaration".equals(child.getType())) {
                    int classChildCount = child.getChildCount();
                    for (int j = 0; j < classChildCount; j++) {
                        TSNode classChild = child.getChild(j);
                        if ("modifiers".equals(classChild.getType())) {
                            int modCount = classChild.getChildCount();
                            for (int k = 0; k < modCount; k++) {
                                TSNode modifier = classChild.getChild(k);
                                if ("abstract".equals(modifier.getType())) {
                                    return true;
                                }
                            }
                            break;
                        }
                    }
                    return false;
                }
            }
        } catch (IOException e) {
            // Cannot read file, treat as non-abstract
        }
        return false;
    }

    private String findTopLevelClassName(TSNode rootNode, String sourceCode) {
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("class_declaration".equals(child.getType())) {
                TSNode nameNode = child.getChildByFieldName("name");
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    return StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                }
                return null;
            }
        }
        return null;
    }

    private Set<String> findReferencedClassPaths(String classPath, String classSource,
                                                   Map<String, String> fieldTypes, boolean isRootClass) {
        Set<String> referencedPaths = new HashSet<>();
        Path currentPath = Paths.get(classPath).normalize();
        Path directory = currentPath.getParent();

        if (directory == null) {
            return referencedPaths;
        }

        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        String packageName = extractPackageName(rootNode, classSource);
        Map<String, String> imports = extractImports(rootNode, classSource);

        Set<String> innerClassNames = extractInnerClassNames(classSource);

        Set<String> typeNames = new HashSet<>();
        for (String typeName : fieldTypes.values()) {
            String baseTypeName = typeName.replace("[]", "");
            if (!isPrimitiveOrStandardType(baseTypeName)) {
                typeNames.add(baseTypeName);
            }
        }

        for (String typeName : typeNames) {
            if (innerClassNames.contains(typeName)) {
                continue;
            }

            String resolvedPath = resolveClassPath(typeName, packageName, imports, currentPath);
            if (resolvedPath != null && Files.exists(Paths.get(resolvedPath))) {
                referencedPaths.add(resolvedPath);
                lastLoadedDependencyClasses.add(typeName);
            } else {
                lastFailedDependencyClasses.add(typeName);
            }
        }

        return referencedPaths;
    }

    private boolean isPrimitiveOrStandardType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return true;
        }

        Set<String> primitives = Set.of(
                "int", "long", "short", "byte", "char", "boolean", "float", "double", "void");
        if (primitives.contains(typeName)) {
            return true;
        }

        Set<String> standardTypes = Set.of(
                "String", "Integer", "Long", "Short", "Byte", "Character",
                "Boolean", "Float", "Double", "Object", "List", "Set",
                "Map", "Collection", "ArrayList", "HashSet", "HashMap");
        return standardTypes.contains(typeName);
    }

    private Set<String> extractInnerClassNames(String classSource) {
        Set<String> innerClassNames = new HashSet<>();
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        findInnerClassNames(rootNode, classSource, innerClassNames, false);
        return innerClassNames;
    }

    private void findInnerClassNames(TSNode node, String sourceCode, Set<String> innerClassNames,
                                      boolean insideClass) {
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            if (insideClass) {
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    String className = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(),
                            nameNode.getEndByte());
                    innerClassNames.add(className);
                }
            }
            insideClass = true;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findInnerClassNames(child, sourceCode, innerClassNames, insideClass);
        }
    }

    private Set<String> extractFieldsFromClass(String classSource) {
        Set<String> fields = new HashSet<>();
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        String packageName = extractPackageName(rootNode, classSource);
        findFieldDeclarationsWithContext(rootNode, classSource, fields, packageName, "");
        return fields;
    }

    private Set<String> extractFieldsFromClassWithOverride(String classSource, String overrideClassName,
                                                            String overridePackageName) {
        Set<String> fields = new HashSet<>();
        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();
        String packageName = overridePackageName != null ? overridePackageName
                : extractPackageName(rootNode, classSource);
        findFieldDeclarationsWithContextOverride(rootNode, classSource, fields, packageName, "",
                overrideClassName);
        return fields;
    }

    private void findFieldDeclarationsWithContextOverride(TSNode node, String sourceCode, Set<String> fields,
                                                           String packageName, String classContext,
                                                           String topLevelClassNameOverride) {
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                String className = (classContext.isEmpty() && topLevelClassNameOverride != null)
                        ? topLevelClassNameOverride
                        : StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());

                String newClassContext = classContext.isEmpty() ? className : classContext + "." + className;

                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if ("class_body".equals(child.getType())) {
                        findFieldDeclarationsWithContextOverride(child, sourceCode, fields, packageName,
                                newClassContext, topLevelClassNameOverride);
                    }
                }
                return;
            }
        } else if ("field_declaration".equals(nodeType)) {
            extractFieldNamesWithContext(node, sourceCode, fields, packageName, classContext);
            return;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldDeclarationsWithContextOverride(child, sourceCode, fields, packageName,
                    classContext, topLevelClassNameOverride);
        }
    }

    private void findFieldDeclarationsWithContext(TSNode node, String sourceCode, Set<String> fields,
                                                   String packageName, String classContext) {
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                String className = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                String newClassContext = classContext.isEmpty() ? className : classContext + "." + className;

                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if ("class_body".equals(child.getType())) {
                        findFieldDeclarationsWithContext(child, sourceCode, fields, packageName,
                                newClassContext);
                    }
                }
                return;
            }
        } else if ("field_declaration".equals(nodeType)) {
            extractFieldNamesWithContext(node, sourceCode, fields, packageName, classContext);
            return;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldDeclarationsWithContext(child, sourceCode, fields, packageName, classContext);
        }
    }

    private void extractFieldNamesWithContext(TSNode declarationNode, String sourceCode, Set<String> fields,
                                              String packageName, String classContext) {
        if (!includeStaticFields && isStaticField(declarationNode, sourceCode)) {
            return;
        }
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                TSNode identifier = child.getChild(0);
                if (identifier != null && "identifier".equals(identifier.getType())) {
                    String fieldName = StateFieldCoverageUtils.byteSubstring(sourceCode, identifier.getStartByte(),
                            identifier.getEndByte());
                    String fqn = packageName.isEmpty() ? classContext + "." + fieldName
                            : packageName + "." + classContext + "." + fieldName;
                    fields.add(fqn);
                }
            }
        }
    }

    private boolean isStaticField(TSNode declarationNode, String sourceCode) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("modifiers".equals(child.getType())) {
                int modChildCount = child.getChildCount();
                for (int j = 0; j < modChildCount; j++) {
                    TSNode modChild = child.getChild(j);
                    int startByte = modChild.getStartByte();
                    int endByte = modChild.getEndByte();
                    if (endByte > startByte
                            && "static".equals(StateFieldCoverageUtils.byteSubstring(sourceCode, startByte, endByte))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Set<String> getFieldsAccessedByOracle(String testCase, String oracle, String classPath) {
        Set<String> accessedFields = new HashSet<>();

        Set<String> allFields = getAllFieldsInClass(classPath);

        TargetClassInfo targetClassInfo = extractTargetClassInfo(classPath);
        if (targetClassInfo == null) {
            return accessedFields;
        }

        Map<String, String> variableTypes = extractVariableTypes(testCase);
        String testPackage = extractPackageNameFromTestCase(testCase);

        TSTree tree = parser.parseString(null, oracle);
        TSNode rootNode = tree.getRootNode();

        findMethodInvocationsWithTypeCheck(rootNode, oracle, variableTypes, targetClassInfo, testPackage,
                accessedFields, classPath, allFields);
        findDirectFieldAccessesWithTypeCheck(rootNode, oracle, accessedFields, allFields, variableTypes,
                targetClassInfo, testPackage);

        return accessedFields;
    }

    private Set<String> getFieldsAccessedByMethod(String classPath, String methodName) {
        if (methodFieldAccessCache.containsKey(classPath)
                && methodFieldAccessCache.get(classPath).containsKey(methodName)) {
            return methodFieldAccessCache.get(classPath).get(methodName);
        }

        // Cache an empty set before recursing to guard against circular inheritance
        methodFieldAccessCache.computeIfAbsent(classPath, k -> new HashMap<>())
                .put(methodName, new HashSet<>());

        Set<String> accessedFields = new HashSet<>();

        Path path = Paths.get(classPath);
        if (Files.exists(path)) {
            try {
                String classSource = Files.readString(path);
                TSTree tree = parser.parseString(null, classSource);
                TSNode rootNode = tree.getRootNode();
                String packageName = extractPackageName(rootNode, classSource);

                List<MethodContext> methods = findAllMethodsWithClassContext(rootNode, classSource, methodName,
                        packageName, "");
                if (!methods.isEmpty()) {
                    accessedFields = extractFieldAccessFromAllMethodsNamed(classSource, methodName);
                } else {
                    String superclassName = findTopLevelSuperclassName(rootNode, classSource);
                    if (superclassName != null) {
                        Map<String, String> imports = extractImports(rootNode, classSource);
                        String parentClassPath = resolveClassPath(superclassName, packageName, imports,
                                path.normalize());
                        if (parentClassPath != null && Files.exists(Paths.get(parentClassPath))) {
                            accessedFields = getFieldsAccessedByMethod(parentClassPath, methodName);
                        }
                    }
                }
            } catch (IOException e) {
                // Failed to read class file
            }
        }

        methodFieldAccessCache.get(classPath).put(methodName, accessedFields);
        return accessedFields;
    }

    private Set<String> extractFieldAccessFromAllMethodsNamed(String classSource, String methodName) {
        Set<String> allAccessedFields = new HashSet<>();

        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();

        Set<String> allFields = extractFieldsFromClass(classSource);
        String packageName = extractPackageName(rootNode, classSource);

        List<MethodContext> methods = findAllMethodsWithClassContext(rootNode, classSource, methodName,
                packageName, "");

        for (MethodContext methodContext : methods) {
            Set<String> relevantFields = new HashSet<>();
            for (String fqn : allFields) {
                if (StateFieldCoverageUtils.isFieldInClassOrInnerClass(fqn, methodContext.classContext)) {
                    relevantFields.add(fqn);
                }
            }

            Map<String, Set<String>> shortNameToFQN = new HashMap<>();
            for (String fqn : relevantFields) {
                String shortName = StateFieldCoverageUtils.extractShortFieldName(fqn);
                shortNameToFQN.computeIfAbsent(shortName, k -> new HashSet<>()).add(fqn);
            }

            Set<String> localVars = new HashSet<>();
            findLocalVariables(methodContext.methodNode, classSource, localVars);

            Set<String> methodFields = new HashSet<>();
            findFieldAccessesInMethod(methodContext.methodNode, classSource, methodFields, shortNameToFQN,
                    localVars);
            allAccessedFields.addAll(methodFields);

            if (iterableFieldTrackingEnabled) {
                Set<String> iteratedFields = findIteratedFields(methodContext.methodNode, classSource,
                        shortNameToFQN, localVars);
                for (String iteratedField : iteratedFields) {
                    allAccessedFields.add(iteratedField + "+");
                }
            }
        }

        return allAccessedFields;
    }

    private List<MethodContext> findAllMethodsWithClassContext(TSNode node, String sourceCode,
                                                                String methodName, String packageName,
                                                                String classContext) {
        List<MethodContext> methods = new ArrayList<>();
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                String className = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                String newClassContext = classContext.isEmpty()
                        ? (packageName.isEmpty() ? className : packageName + "." + className)
                        : classContext + "." + className;

                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    methods.addAll(findAllMethodsWithClassContext(child, sourceCode, methodName,
                            packageName, newClassContext));
                }
            }
            return methods;
        } else if ("method_declaration".equals(nodeType)) {
            if (!classContext.isEmpty()) {
                TSNode nameNode = node.getChildByFieldName("name");
                if (nameNode != null && "identifier".equals(nameNode.getType())) {
                    String name = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                    if (methodName.equals(name)) {
                        methods.add(new MethodContext(node, classContext));
                    }
                }
            }
            return methods;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            methods.addAll(findAllMethodsWithClassContext(child, sourceCode, methodName, packageName,
                    classContext));
        }

        return methods;
    }

    private void findLocalVariables(TSNode methodNode, String sourceCode, Set<String> localVars) {
        int childCount = methodNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = methodNode.getChild(i);
            findLocalVariableDeclarations(child, sourceCode, localVars);
        }
    }

    private void findLocalVariableDeclarations(TSNode node, String sourceCode, Set<String> localVars) {
        String nodeType = node.getType();

        if ("local_variable_declaration".equals(nodeType)) {
            extractVariableNames(node, sourceCode, localVars);
        } else if ("enhanced_for_statement".equals(nodeType) || "for_statement".equals(nodeType)) {
            TSNode initNode = node.getChild(0);
            if (initNode != null) {
                extractVariableNames(initNode, sourceCode, localVars);
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findLocalVariableDeclarations(child, sourceCode, localVars);
        }
    }

    private void extractVariableNames(TSNode declarationNode, String sourceCode, Set<String> variables) {
        int childCount = declarationNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = declarationNode.getChild(i);
            if ("variable_declarator".equals(child.getType())) {
                TSNode identifier = child.getChild(0);
                if (identifier != null && "identifier".equals(identifier.getType())) {
                    String varName = StateFieldCoverageUtils.byteSubstring(sourceCode, identifier.getStartByte(),
                            identifier.getEndByte());
                    variables.add(varName);
                }
            }
        }
    }

    private void findFieldAccessesInMethod(TSNode node, String sourceCode, Set<String> accessedFields,
                                           Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        String nodeType = node.getType();

        if ("field_access".equals(nodeType)) {
            TSNode fieldNode = node.getChildByFieldName("field");
            if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                String fieldName = StateFieldCoverageUtils.byteSubstring(sourceCode, fieldNode.getStartByte(), fieldNode.getEndByte());
                if (shortNameToFQN.containsKey(fieldName)) {
                    accessedFields.addAll(shortNameToFQN.get(fieldName));
                }
            }
        } else if ("identifier".equals(nodeType)) {
            TSNode parent = node.getParent();
            if (parent != null && !isMethodName(node, parent)) {
                String name = StateFieldCoverageUtils.byteSubstring(sourceCode, node.getStartByte(), node.getEndByte());
                if (shortNameToFQN.containsKey(name) && !localVars.contains(name)) {
                    accessedFields.addAll(shortNameToFQN.get(name));
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldAccessesInMethod(child, sourceCode, accessedFields, shortNameToFQN, localVars);
        }
    }

    private boolean isMethodName(TSNode identifierNode, TSNode parent) {
        String parentType = parent.getType();
        return "method_invocation".equals(parentType) || "method_declaration".equals(parentType);
    }

    private Set<String> identifyIterableFields(String classPath, String classSource, Set<String> allFields) {
        Set<String> iterableFields = new HashSet<>();

        TSTree tree = parser.parseString(null, classSource);
        TSNode rootNode = tree.getRootNode();

        String packageName = extractPackageName(rootNode, classSource);
        Map<String, String> fieldTypes = extractFieldTypes(rootNode, classSource, packageName, "");

        // Bridge renamed fields (concrete class override) to their types
        Map<String, String> simpleNameToType = new HashMap<>();
        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fqn = entry.getKey();
            String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
            simpleNameToType.put(simpleName, entry.getValue());
        }
        for (String accumulatedFQN : allFields) {
            if (!fieldTypes.containsKey(accumulatedFQN)) {
                String simpleName = accumulatedFQN.substring(accumulatedFQN.lastIndexOf('.') + 1);
                String fieldType = simpleNameToType.get(simpleName);
                if (fieldType != null) {
                    fieldTypes.put(accumulatedFQN, fieldType);
                }
            }
        }

        Set<String> recursiveFields = new HashSet<>();
        Set<String> classesWithRecursiveFields = new HashSet<>();

        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldFQN = entry.getKey();
            String fieldType = entry.getValue();

            if (!allFields.contains(fieldFQN)) {
                continue;
            }

            if (StateFieldCoverageUtils.isRecursiveField(fieldFQN, fieldType)) {
                recursiveFields.add(fieldFQN);
                String classContext = StateFieldCoverageUtils.extractClassContextFromFieldFQN(fieldFQN);
                classesWithRecursiveFields.add(classContext);
            }
        }

        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String fieldFQN = entry.getKey();
            String fieldType = entry.getValue();

            if (!allFields.contains(fieldFQN)) {
                continue;
            }

            if (isCollectionType(fieldType)) {
                iterableFields.add(fieldFQN);
            } else if (recursiveFields.contains(fieldFQN)) {
                iterableFields.add(fieldFQN);
            } else {
                String classContext = StateFieldCoverageUtils.extractClassContextFromFieldFQN(fieldFQN);
                if (classesWithRecursiveFields.contains(classContext)) {
                    iterableFields.add(fieldFQN);
                }
            }
        }

        return iterableFields;
    }

    private Map<String, String> extractFieldTypes(TSNode node, String sourceCode, String packageName,
                                                   String classContext) {
        Map<String, String> fieldTypes = new HashMap<>();
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                String className = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                String newClassContext = classContext.isEmpty()
                        ? (packageName.isEmpty() ? className : packageName + "." + className)
                        : classContext + "." + className;

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
            TSNode typeNode = null;
            List<String> fieldNames = new ArrayList<>();

            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = node.getChild(i);
                String childType = child.getType();

                if ("type_identifier".equals(childType) || "array_type".equals(childType)
                        || "generic_type".equals(childType) || "integral_type".equals(childType)
                        || "floating_point_type".equals(childType) || "boolean_type".equals(childType)) {
                    typeNode = child;
                } else if ("variable_declarator".equals(childType)) {
                    TSNode identifier = child.getChild(0);
                    if (identifier != null && "identifier".equals(identifier.getType())) {
                        fieldNames.add(StateFieldCoverageUtils.byteSubstring(sourceCode, identifier.getStartByte(),
                                identifier.getEndByte()));
                    }
                }
            }

            if (typeNode != null && !classContext.isEmpty()) {
                String typeName = extractTypeName(typeNode, sourceCode);
                for (String fieldName : fieldNames) {
                    fieldTypes.put(classContext + "." + fieldName, typeName);
                }
            }
            return fieldTypes;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            fieldTypes.putAll(extractFieldTypes(child, sourceCode, packageName, classContext));
        }

        return fieldTypes;
    }

    private String extractTypeName(TSNode typeNode, String sourceCode) {
        if ("type_identifier".equals(typeNode.getType())) {
            return StateFieldCoverageUtils.byteSubstring(sourceCode, typeNode.getStartByte(), typeNode.getEndByte());
        } else if ("array_type".equals(typeNode.getType())) {
            TSNode elementType = typeNode.getChild(0);
            if (elementType != null) {
                return extractTypeName(elementType, sourceCode) + "[]";
            }
        } else if ("generic_type".equals(typeNode.getType())) {
            TSNode identifier = typeNode.getChild(0);
            if (identifier != null && "type_identifier".equals(identifier.getType())) {
                return StateFieldCoverageUtils.byteSubstring(sourceCode, identifier.getStartByte(), identifier.getEndByte());
            }
        }
        return StateFieldCoverageUtils.byteSubstring(sourceCode, typeNode.getStartByte(), typeNode.getEndByte());
    }

    private boolean isCollectionType(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return false;
        }
        if (typeName.endsWith("[]")) {
            return true;
        }
        return typeName.equals("List") || typeName.equals("Set")
                || typeName.equals("Collection") || typeName.equals("ArrayList")
                || typeName.equals("LinkedList") || typeName.equals("HashSet")
                || typeName.equals("TreeSet") || typeName.equals("Vector")
                || typeName.equals("Stack") || typeName.equals("Queue")
                || typeName.equals("Deque") || typeName.equals("Map")
                || typeName.equals("HashMap") || typeName.equals("TreeMap");
    }

    private Set<String> findIteratedFields(TSNode methodNode, String sourceCode,
                                           Map<String, Set<String>> shortNameToFQN,
                                           Set<String> localVars) {
        Set<String> iteratedFields = new HashSet<>();
        findFieldsInLoops(methodNode, sourceCode, iteratedFields, shortNameToFQN, localVars);
        findFieldsInRecursiveCalls(methodNode, sourceCode, iteratedFields, shortNameToFQN, localVars);
        return iteratedFields;
    }

    private void findFieldsInLoops(TSNode node, String sourceCode, Set<String> iteratedFields,
                                   Map<String, Set<String>> shortNameToFQN, Set<String> localVars) {
        String nodeType = node.getType();

        if ("while_statement".equals(nodeType) || "for_statement".equals(nodeType)
                || "enhanced_for_statement".equals(nodeType) || "do_statement".equals(nodeType)) {
            Set<String> fieldsInLoop = new HashSet<>();
            findFieldAccessesInMethod(node, sourceCode, fieldsInLoop, shortNameToFQN, localVars);
            iteratedFields.addAll(fieldsInLoop);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findFieldsInLoops(child, sourceCode, iteratedFields, shortNameToFQN, localVars);
        }
    }

    private void findFieldsInRecursiveCalls(TSNode methodNode, String sourceCode,
                                            Set<String> iteratedFields,
                                            Map<String, Set<String>> shortNameToFQN,
                                            Set<String> localVars) {
        TSNode nameNode = methodNode.getChildByFieldName("name");
        if (nameNode == null || !"identifier".equals(nameNode.getType())) {
            return;
        }

        String methodName = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());

        if (containsMethodCall(methodNode, sourceCode, methodName)) {
            Set<String> fieldsInMethod = new HashSet<>();
            findFieldAccessesInMethod(methodNode, sourceCode, fieldsInMethod, shortNameToFQN, localVars);
            iteratedFields.addAll(fieldsInMethod);
        }
    }

    private boolean containsMethodCall(TSNode node, String sourceCode, String methodName) {
        String nodeType = node.getType();

        if ("method_invocation".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                String name = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
                if (methodName.equals(name)) {
                    return true;
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (containsMethodCall(child, sourceCode, methodName)) {
                return true;
            }
        }

        return false;
    }

    private String extractPackageName(TSNode rootNode, String sourceCode) {
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("package_declaration".equals(child.getType())) {
                int pkgChildCount = child.getChildCount();
                for (int j = 0; j < pkgChildCount; j++) {
                    TSNode pkgChild = child.getChild(j);
                    if ("scoped_identifier".equals(pkgChild.getType())
                            || "identifier".equals(pkgChild.getType())) {
                        return StateFieldCoverageUtils.byteSubstring(sourceCode, pkgChild.getStartByte(), pkgChild.getEndByte());
                    }
                }
            }
        }
        return "";
    }

    private Map<String, String> extractImports(TSNode rootNode, String sourceCode) {
        Map<String, String> imports = new HashMap<>();
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = rootNode.getChild(i);
            if ("import_declaration".equals(child.getType())) {
                int importChildCount = child.getChildCount();
                for (int j = 0; j < importChildCount; j++) {
                    TSNode importChild = child.getChild(j);
                    if ("scoped_identifier".equals(importChild.getType())) {
                        String fullImport = StateFieldCoverageUtils.byteSubstring(sourceCode, importChild.getStartByte(),
                                importChild.getEndByte());
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

    private String resolveClassPath(String typeName, String packageName, Map<String, String> imports,
                                    Path currentPath) {
        currentPath = currentPath.normalize();
        Path directory = currentPath.getParent();
        if (directory == null) {
            return null;
        }

        if (imports.containsKey(typeName)) {
            String fullyQualifiedName = imports.get(typeName);
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            if (lastDot >= 0) {
                String packagePath = fullyQualifiedName.substring(0, lastDot).replace('.', '/');
                String className = typeName + ".java";

                Path packageBasedRoot = computeSourceRootFromPackage(directory, packageName);
                if (packageBasedRoot != null) {
                    Path resolvedPath = packageBasedRoot.resolve(packagePath).resolve(className);
                    if (Files.exists(resolvedPath)) {
                        return resolvedPath.toString();
                    }
                }

                Path heuristicRoot = findSourceRoot(directory);
                if (heuristicRoot != null && !heuristicRoot.equals(packageBasedRoot)) {
                    Path resolvedPath = heuristicRoot.resolve(packagePath).resolve(className);
                    if (Files.exists(resolvedPath)) {
                        return resolvedPath.toString();
                    }
                }
            }
        }

        Path samePackagePath = directory.resolve(typeName + ".java");
        if (Files.exists(samePackagePath)) {
            return samePackagePath.toString();
        }

        if (packageName != null && !packageName.isEmpty()) {
            Path sourceRoot = computeSourceRootFromPackage(directory, packageName);
            if (sourceRoot != null) {
                String packagePath = packageName.replace('.', '/');
                Path classPath = sourceRoot.resolve(packagePath).resolve(typeName + ".java");
                if (Files.exists(classPath)) {
                    return classPath.toString();
                }
            }
        }

        return null;
    }

    private Path computeSourceRootFromPackage(Path directory, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        String[] parts = packageName.split("\\.");
        Path sourceRoot = directory;
        for (int i = parts.length - 1; i >= 0; i--) {
            if (sourceRoot == null) {
                return null;
            }
            String dirName = sourceRoot.getFileName() != null ? sourceRoot.getFileName().toString() : "";
            if (!parts[i].equals(dirName)) {
                return null;
            }
            sourceRoot = sourceRoot.getParent();
        }
        return sourceRoot;
    }

    private Path findSourceRoot(Path currentDir) {
        Path dir = currentDir;

        while (dir != null) {
            String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";

            if ("java".equals(dirName) || "resources".equals(dirName)) {
                return dir;
            } else if ("main".equals(dirName) || "test".equals(dirName)) {
                Path parent = dir.getParent();
                return parent != null ? parent : dir;
            } else if ("src".equals(dirName)) {
                return dir;
            }

            dir = dir.getParent();
        }

        return currentDir;
    }

    private TargetClassInfo extractTargetClassInfo(String classPath) {
        Path path = Paths.get(classPath);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            String classSource = Files.readString(path);
            TSTree tree = parser.parseString(null, classSource);
            TSNode rootNode = tree.getRootNode();

            String packageName = extractPackageName(rootNode, classSource);
            String className = extractClassNameFromDeclaration(rootNode, classSource);
            if (className == null) {
                return null;
            }

            Map<String, String> imports = extractImports(rootNode, classSource);
            return new TargetClassInfo(className, packageName, imports);
        } catch (IOException e) {
            return null;
        }
    }

    private String extractClassNameFromDeclaration(TSNode node, String sourceCode) {
        String nodeType = node.getType();

        if ("class_declaration".equals(nodeType)) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && "identifier".equals(nameNode.getType())) {
                return StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(), nameNode.getEndByte());
            }
        }

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

    private Map<String, String> extractVariableTypes(String testCase) {
        Map<String, String> variableTypes = new HashMap<>();
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();
        findVariableDeclarations(rootNode, testCase, variableTypes);
        return variableTypes;
    }

    private void findVariableDeclarations(TSNode node, String sourceCode,
                                          Map<String, String> variableTypes) {
        if (node == null) {
            return;
        }

        String nodeType = node.getType();

        if ("local_variable_declaration".equals(nodeType)) {
            TSNode typeNode = node.getChildByFieldName("type");
            if (typeNode != null) {
                String type = extractTypeFromNode(typeNode, sourceCode);

                int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = node.getChild(i);
                    if (child != null && "variable_declarator".equals(child.getType())) {
                        TSNode nameNode = child.getChildCount() > 0 ? child.getChild(0) : null;
                        if (nameNode != null && "identifier".equals(nameNode.getType())) {
                            String varName = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(),
                                    nameNode.getEndByte());
                            if (type != null) {
                                variableTypes.put(varName, type);
                            }
                        }
                    }
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findVariableDeclarations(child, sourceCode, variableTypes);
            }
        }
    }

    private String extractTypeFromNode(TSNode typeNode, String sourceCode) {
        if (typeNode == null) {
            return null;
        }

        String nodeType = typeNode.getType();

        if ("type_identifier".equals(nodeType)) {
            return StateFieldCoverageUtils.byteSubstring(sourceCode, typeNode.getStartByte(), typeNode.getEndByte());
        } else if ("generic_type".equals(nodeType)) {
            TSNode typeIdentifier = typeNode.getChildCount() > 0 ? typeNode.getChild(0) : null;
            if (typeIdentifier != null && "type_identifier".equals(typeIdentifier.getType())) {
                return StateFieldCoverageUtils.byteSubstring(sourceCode, typeIdentifier.getStartByte(), typeIdentifier.getEndByte());
            }
        }

        return null;
    }

    private String extractPackageNameFromTestCase(String testCase) {
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();
        return extractPackageName(rootNode, testCase);
    }

    private void findMethodInvocationsWithTypeCheck(TSNode node, String sourceCode,
                                                     Map<String, String> variableTypes,
                                                     TargetClassInfo targetClassInfo,
                                                     String testPackage,
                                                     Set<String> accessedFields,
                                                     String classPath,
                                                     Set<String> targetFields) {
        if (node == null) {
            return;
        }

        String nodeType = node.getType();

        if ("method_invocation".equals(nodeType)) {
            TSNode objectNode = node.getChildByFieldName("object");
            if (objectNode != null) {
                String variableName = extractIdentifierFromExpression(objectNode, sourceCode);
                boolean isObjectOfTargetClassType = (variableName == null
                        || isTargetClassType(variableName, variableTypes, targetClassInfo, testPackage));

                if (isObjectOfTargetClassType || !assertOnlyTargetClassMethods) {
                    TSNode nameNode = node.getChildByFieldName("name");
                    if (nameNode != null && "identifier".equals(nameNode.getType())) {
                        String methodName = StateFieldCoverageUtils.byteSubstring(sourceCode, nameNode.getStartByte(),
                                nameNode.getEndByte());

                        String methodClassPath = classPath;
                        if (!isObjectOfTargetClassType && variableName != null) {
                            String varType = variableTypes.get(variableName);
                            if (varType != null) {
                                String resolvedPath = resolveClassPath(varType,
                                        targetClassInfo.packageName, targetClassInfo.imports,
                                        Paths.get(classPath));
                                if (resolvedPath != null) {
                                    methodClassPath = resolvedPath;
                                } else {
                                    methodClassPath = null;
                                }
                            } else {
                                methodClassPath = null;
                            }
                        }

                        if (methodClassPath != null) {
                            Set<String> methodFields = getFieldsAccessedByMethod(methodClassPath, methodName);
                            for (String fqn : methodFields) {
                                if (targetFields.contains(fqn)) {
                                    accessedFields.add(fqn);
                                } else {
                                    String normalized = StateFieldCoverageUtils.mapFieldNameToFQN(StateFieldCoverageUtils.extractShortFieldName(fqn),
                                            targetFields);
                                    if (normalized != null) {
                                        accessedFields.add(normalized);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findMethodInvocationsWithTypeCheck(child, sourceCode, variableTypes, targetClassInfo,
                        testPackage, accessedFields, classPath, targetFields);
            }
        }
    }

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
            TSNode objectNode = node.getChildByFieldName("object");
            if (objectNode != null) {
                String variableName = extractIdentifierFromExpression(objectNode, sourceCode);
                if (variableName == null
                        || isTargetClassType(variableName, variableTypes, targetClassInfo, testPackage)) {
                    TSNode fieldNode = node.getChildByFieldName("field");
                    if (fieldNode != null && "identifier".equals(fieldNode.getType())) {
                        String fieldName = StateFieldCoverageUtils.byteSubstring(sourceCode, fieldNode.getStartByte(),
                                fieldNode.getEndByte());
                        String fqn = StateFieldCoverageUtils.mapFieldNameToFQN(fieldName, allFields);
                        if (fqn != null) {
                            fields.add(fqn);
                        }
                    }
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            if (child != null) {
                findDirectFieldAccessesWithTypeCheck(child, sourceCode, fields, allFields, variableTypes,
                        targetClassInfo, testPackage);
            }
        }
    }

    private String extractIdentifierFromExpression(TSNode node, String sourceCode) {
        if (node == null) {
            return null;
        }

        try {
            String nodeType = node.getType();

            if ("identifier".equals(nodeType)) {
                return StateFieldCoverageUtils.byteSubstring(sourceCode, node.getStartByte(), node.getEndByte());
            } else if ("method_invocation".equals(nodeType) || "field_access".equals(nodeType)) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private boolean isTargetClassType(String variableName, Map<String, String> variableTypes,
                                      TargetClassInfo targetClassInfo, String testPackage) {
        String varType = variableTypes.get(variableName);
        if (varType == null) {
            return true;
        }

        if (!varType.equals(targetClassInfo.className)) {
            return false;
        }

        if (targetClassInfo.packageName.isEmpty()) {
            return true;
        }

        if (testPackage.isEmpty()) {
            return true;
        }

        return targetClassInfo.packageName.equals(testPackage);
    }

    /**
     * Extract the name of the target class being tested from the test case by looking
     * for class instantiation expressions.
     */
    private String extractTargetClassName(String testCase) {
        TSTree tree = parser.parseString(null, testCase);
        TSNode rootNode = tree.getRootNode();

        Set<String> classNames = new HashSet<>();
        findClassInstantiations(rootNode, testCase, classNames);

        for (String className : classNames) {
            if (!className.endsWith("Test") && !className.equals("String")
                    && !className.equals("Integer") && !className.equals("Object")) {
                return className;
            }
        }

        return null;
    }

    private void findClassInstantiations(TSNode node, String sourceCode, Set<String> classNames) {
        String nodeType = node.getType();

        if ("object_creation_expression".equals(nodeType)) {
            TSNode typeNode = node.getChildByFieldName("type");
            if (typeNode != null) {
                String className = extractClassNameFromType(typeNode, sourceCode);
                if (className != null) {
                    classNames.add(className);
                }
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            findClassInstantiations(child, sourceCode, classNames);
        }
    }

    private String extractClassNameFromType(TSNode typeNode, String sourceCode) {
        if ("type_identifier".equals(typeNode.getType())) {
            return StateFieldCoverageUtils.byteSubstring(sourceCode, typeNode.getStartByte(), typeNode.getEndByte());
        }

        if ("generic_type".equals(typeNode.getType())) {
            TSNode identifier = typeNode.getChild(0);
            if (identifier != null && "type_identifier".equals(identifier.getType())) {
                return StateFieldCoverageUtils.byteSubstring(sourceCode, identifier.getStartByte(), identifier.getEndByte());
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Private inner classes
    // -------------------------------------------------------------------------

    private static class MethodContext {
        TSNode methodNode;
        String classContext;

        MethodContext(TSNode methodNode, String classContext) {
            this.methodNode = methodNode;
            this.classContext = classContext;
        }
    }

    private static class TargetClassInfo {
        String className;
        String packageName;
        Map<String, String> imports;

        TargetClassInfo(String className, String packageName, Map<String, String> imports) {
            this.className = className;
            this.packageName = packageName;
            this.imports = imports;
        }
    }

}
