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

## Usage

### Using Gradle

Analyze all test methods in a file:
```bash
./gradlew run --args="<path-to-test-file.java>"
```

Analyze a specific test method:
```bash
./gradlew run --args="<path-to-test-file.java> <test-method-name>"
```

### Using the Standalone JAR

After building the fat JAR:

Analyze all test methods:
```bash
java -jar build/libs/tori-1.0.0-all.jar <path-to-test-file.java>
```

Analyze a specific test method:
```bash
java -jar build/libs/tori-1.0.0-all.jar <path-to-test-file.java> <test-method-name>
```

## Examples

### Example 1: Analyze all test methods

```bash
./gradlew run --args="examples/CalculatorTest.java"
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
./gradlew run --args="examples/CalculatorTest.java testAddition"
```

Output:
```
Test Method: testAddition
  - assertEquals(5, result);
  - assertTrue(result > 0);
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
│   └── main/
│       └── java/
│           └── org/
│               └── tori/
│                   ├── Main.java                    # Entry point
│                   ├── TestOracleInspector.java    # Tree-sitter parser logic
│                   └── MethodOracles.java          # Data model
└── examples/
    └── CalculatorTest.java   # Example test file
```

## License

This project is open source.

