# tori
tori is a test oracle inspector for Java

## Overview

Tori is a command-line tool that uses Tree-sitter to parse JUnit test suites and identify test oracles (assertion statements). It can analyze entire test files or focus on specific test methods.

## Requirements

- Java 17 or higher
- Gradle 8.5 or higher (included via Gradle Wrapper)

## Building the Project

```bash
./gradlew build
```

To create a standalone executable JAR with all dependencies:

```bash
./gradlew fatJar
```

The fat JAR will be created at `build/libs/tori-1.0.0-all.jar`.

## Running Tests

To run the test suite:

```bash
./gradlew test
```

To run tests with detailed output:

```bash
./gradlew test --info
```

To view the HTML test report after running tests, open the following file in your browser:

```
build/reports/tests/test/index.html
```

The test suite includes tests that verify the correct number of assertions are recovered from example test files located in `src/test/resources/`.

## Usage

### Using Gradle

Analyze all test methods in a file:
```bash
./gradlew run --args="-t <path-to-test-file.java>"
```

Analyze a specific test method:
```bash
./gradlew run --args="-t <path-to-test-file.java> -m <test-method-name>"
```

### Using the Standalone JAR

After building the fat JAR:

Analyze all test methods:
```bash
java -jar build/libs/tori-1.0.0-all.jar -t <path-to-test-file.java>
```

Analyze a specific test method:
```bash
java -jar build/libs/tori-1.0.0-all.jar -t <path-to-test-file.java> -m <test-method-name>
```

### Command-Line Options

- `-t, --test-file <FILE>`: Path to the test file (required)
- `-m, --test-method <METHOD>`: Name of the specific test method to analyze (optional)
- `--metric <METRIC_CLASS>`: Class name of the metric to use for oracle assessment (optional)
- `-mc, --metric-config <CONFIG_FILE>`: Path to the properties file with metric configuration (optional)

## Metrics

Tori supports metrics that can assess the quality of test oracles. Currently, two metrics are available:

### StateFieldCoverage

Measures the proportion of fields in a target class that are accessed by test assertions. This metric helps assess how comprehensively an oracle tests the state of an object.

**Configuration properties:**
- `target_class`: Path to the target class file(s). Multiple classes can be specified as comma-separated values (required)
- `exec_level`: Execution level - `assert`, `test_method`, or `test_class` (optional, default: `assert`)
- `iterable_field_tracking`: Enable or disable iterable field tracking (optional, default: `true`)

**Multiple Target Classes:**

You can specify multiple target classes by separating their paths with commas. When multiple classes are specified, the metric will consider the fields from all classes when computing the coverage score.

Example configuration:
```properties
target_class=src/test/resources/IntsList.java, src/test/resources/Person.java
```

Note: When a class is specified as a target class, the fields of all reachable classes from it (including inner classes) are automatically considered. If you specify both a parent class and a subclass that is already reachable from the parent, the fields will be the same as only specifying the parent class (no duplication).

**Iterable Field Tracking:**

When enabled (default), the metric distinguishes between iterable and non-iterable fields:

- **Non-iterable fields** (e.g., `IntsList.header`, `IntsList.size`): Only one target label is considered (the field name). This label is covered if the assertion accesses the field.

- **Iterable fields** (e.g., `IntsList.Node.next`, `IntsList.Node.item`): Two target labels are considered:
  - Regular label (e.g., `IntsList.Node.next`): Covered if the assertion accesses the field
  - Special iteration label (e.g., `IntsList.Node.next+`): Covered if code reachable from the assertion iterates over the field in a loop or recursive method

Iterable fields include:
- Collection types (arrays, List, Set, Map, etc.)
- Recursive fields (fields whose type matches their containing class, e.g., `next` in a `Node` class)
- Other fields in classes containing recursive fields (e.g., `item` in a `Node` class)

Example: The `checkSize()` method in `IntsList` iterates over the list using a while loop that accesses the `next` field. With iterable field tracking enabled, this covers both `next` (regular access) and `next+` (iteration detected).

### CheckedVarsMetric

Measures the proportion of variables declared in a test that are actually checked in assertions. This metric evaluates how comprehensively an oracle tests the declared variables.

**Configuration properties:**
- `exec_level`: Execution level - `assert`, `test_method`, or `test_class` (optional, default: `assert`)

### Execution Levels

Metrics can be executed at three different levels:

- **assert** (default): The metric is computed individually for each assertion statement
- **test_method**: The metric is computed treating all assertions in a test method as a single one (computes the union of accessed fields or checked variables)
- **test_class**: The metric is computed treating all assertions in a test class as a single one (computes the union across all methods). Note: This level is not allowed when analyzing a specific test method.

### Example Metric Configuration Files

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
target_class=src/test/resources/IntsList.java, src/test/resources/Person.java
exec_level=assert
iterable_field_tracking=false
```

**checked_vars_test_class.properties**:
```properties
exec_level=test_class
```

## Examples

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

## Supported Assertions

Tori recognizes common JUnit assertion methods, including:
- `assertEquals`, `assertNotEquals`
- `assertTrue`, `assertFalse`
- `assertNull`, `assertNotNull`
- `assertSame`, `assertNotSame`
- `assertArrayEquals`
- `assertThrows`, `assertDoesNotThrow`
- `assertAll`
- `assertTimeout`, `assertTimeoutPreemptively`
- And more...

## How It Works

1. **Parsing**: Tori uses Tree-sitter's Java grammar to parse the test file into an Abstract Syntax Tree (AST)
2. **Analysis**: It traverses the AST to find method declarations and identify assertion statements within them
3. **Reporting**: It outputs the method names along with all assertion statements found in each method

## Project Structure

```
tori/
├── build.gradle              # Gradle build configuration
├── settings.gradle           # Gradle settings
├── gradle.properties         # Gradle properties
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/
│   │           └── tori/
│   │               ├── Main.java                    # Entry point
│   │               ├── TestOracleInspector.java    # Tree-sitter parser logic
│   │               ├── MethodOracles.java          # Data model
│   │               └── metrics/
│   │                   ├── Metric.java              # Metric interface
│   │                   ├── ExecutionLevel.java      # Execution level enum
│   │                   ├── StateFieldCoverage.java  # State field coverage metric
│   │                   └── CheckedVarsMetric.java   # Checked variables metric
│   └── test/
│       ├── java/
│       │   └── org/
│       │       └── tori/
│       │           ├── TestOracleInspectorTest.java              # Test suite
│       │           └── metrics/
│       │               ├── StateFieldCoverageTest.java            # StateFieldCoverage tests
│       │               ├── StateFieldCoverageExecutionLevelTest.java
│       │               ├── CheckedVarsMetricTest.java             # CheckedVarsMetric tests
│       │               └── CheckedVarsMetricExecutionLevelTest.java
│       └── resources/
│           ├── CalculatorTest.java                  # Example test file
│           ├── StringUtilsTest.java                 # Example test file
│           ├── IntsListTest.java                    # Example test file
│           ├── IntsList.java                        # Example target class
│           ├── state_field_coverage.properties      # Metric config examples
│           ├── state_field_coverage_test_method.properties
│           ├── state_field_coverage_test_class.properties
│           ├── checked_vars_test_method.properties
│           └── checked_vars_test_class.properties
```

## License

This project is open source.

