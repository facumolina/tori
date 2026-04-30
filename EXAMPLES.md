## Example Metric Configuration Files

**state_field_coverage.properties**:
```properties
target_class=src/test/resources/IntsList.java
exec_level=assert
```

**state_field_coverage_test_method.properties**:
```properties
target_class=src/test/resources/IntsList.java
exec_level=test_method
```

**state_field_coverage_iterable.properties**:
```properties
target_class=src/test/resources/IntsList.java
exec_level=assert
iterable_field_tracking=true
```

**state_field_coverage_multiple_classes.properties**:
```properties
target_class=src/test/resources/IntsList.java,src/test/resources/Person.java
exec_level=assert
iterable_field_tracking=false
```

**checked_vars_test_class.properties**:
```properties
exec_level=test_class
```

## Metrics Examples

### Example 1: Analyze all test methods

```bash
./gradlew run --args="-t src/test/resources/CalculatorTest.java"
```

Output:
```
Test Method: testAddition
  - assertEquals(5, result);
  - assertTrue(result > 0);

Test Method: testSubtraction
  - assertEquals(2, result);
  - assertNotNull(calc);

Test Method: testMultiplication
  - assertEquals(20, result);
  - assertFalse(result < 0);
...
```

### Example 2: Analyze a specific test method

```bash
./gradlew run --args="-t src/test/resources/CalculatorTest.java -m testAddition"
```

Output:
```
Test Method: testAddition
  - assertEquals(5, result);
  - assertTrue(result > 0);
```

### Example 3: Use StateFieldCoverage metric with assert level

```bash
./gradlew run --args="-t src/test/resources/IntsListTest.java -m testMultipleFields --metric org.tori.metrics.StateFieldCoverage --metric-config src/test/resources/state_field_coverage.properties"
```

Output:
```
Target class: src/test/resources/IntsList.java
Total target fields: 4 [next, item, size, header]
Execution level: assert

Test Method: testMultipleFields
  - assertTrue(l.getHeader() != null && l.getSize() == 2); [score: 0.50, accessed fields: 2 [size, header]]
```

### Example 4: Use StateFieldCoverage metric with test_method level

```bash
./gradlew run --args="-t src/test/resources/IntsListTest.java -m testAllFields --metric org.tori.metrics.StateFieldCoverage --metric-config src/test/resources/state_field_coverage_test_method.properties"
```

Output:
```
Target class: src/test/resources/IntsList.java
Total target fields: 4 [next, item, size, header]
Execution level: test_method

Test Method: testAllFields
  All assertions [score: 1.00, accessed fields: 4 [next, item, size, header]]
  Total assertions: 1
```

### Example 5: Use StateFieldCoverage metric with test_class level

```bash
./gradlew run --args="-t src/test/resources/IntsListTest.java --metric org.tori.metrics.StateFieldCoverage --metric-config src/test/resources/state_field_coverage_test_class.properties"
```

Output:
```
Target class: src/test/resources/IntsList.java
Total target fields: 4 [next, item, size, header]
Execution level: test_class

Test Class Assessment:
  All assertions [score: 1.00, accessed fields: 4 [next, item, size, header]]
  Total assertions: 11
```

### Example 6: Use StateFieldCoverage with iterable field tracking

```bash
./gradlew run --args="-t src/test/resources/IntsListTest.java -m testCheckSize --metric org.tori.metrics.StateFieldCoverage --metric-config src/test/resources/state_field_coverage_iterable.properties"
```

Output:
```
Target class: src/test/resources/IntsList.java
Total target fields: 6 [com.example.IntsList.Node.item, com.example.IntsList.header, com.example.IntsList.Node.item+, com.example.IntsList.Node.next+, com.example.IntsList.Node.next, com.example.IntsList.size]
Execution level: assert
Iterable field tracking: enabled

Test Method: testCheckSize
  - assertTrue(l.checkSize()); [score: 0.67, accessed fields: 4 [com.example.IntsList.header, com.example.IntsList.Node.next+, com.example.IntsList.Node.next, com.example.IntsList.size]]
```

In this example, the `checkSize()` method iterates over the list using a while loop, which accesses the `next` field. With iterable field tracking enabled, both the regular label (`next`) and the special iteration label (`next+`) are covered. The method accesses 4 out of 6 target fields (including special labels), achieving a score of 0.67.

### Example 7: Use CheckedVarsMetric with test_class level

```bash
./gradlew run --args="-t src/test/resources/CalculatorTest.java --metric org.tori.metrics.CheckedVarsMetric --metric-config src/test/resources/checked_vars_test_class.properties"
```

Output:
```
Test Class Assessment:
  All assertions [score: 1.00]
  Total assertions: 7
```