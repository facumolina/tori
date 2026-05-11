# tori

Tori (Test Oracle Inspector) is a static analysis tool for assessing the quality of test oracles. 
Given a target test file, it uses static analysis to identify assertion statements and compute metrics that evaluate how well the oracles test the behavior of the system under test. Tori is designed to be extensible, allowing users to implement custom metrics for oracle assessment.

---

## Setup

### Requirements

- Java 17 or higher
- Gradle 8.5 or higher (included via Gradle Wrapper)

### Local installation

To install tori locally, clone the repository and build the project using Gradle:

```bash
git clone https://github.com/facumolina/tori
cd tori
./gradlew build
./gradlew fatJar # Creates build/libs/tori-1.0.0-all.jar with all dependencies included
```

### Docker

We provide a `Dockerfile` that can be used to build a docker image with
tori and all its dependencies. To build and run the docker image,
execute the following commands:
```bash
docker build -t tori .
docker run -it tori
```

---
## Usage

To assess the test oracles in a test file, you will need to specify: 
- the path to the test file and optionally the specific test method you want to analyze, 
- the metric for oracle assessment you want to use along with its configuration.

Below we provide two examples using the StateFieldCoverage metric.

### Basic Example

The class [`IntsList`](src/test/resources/IntsList.java) is a simple implementation of a linked list of integers. 
The test class [`IntsListTest`](src/test/resources/IntsListTest.java) contains several test methods that test the behavior of `IntsList`.

To analyze oracles in all test methods of `IntsListTest` using the StateFieldCoverage metric with a specific configuration file, you can run the following command:

```bash
java -jar build/libs/tori-1.0.0-all.jar \
  -t src/test/resources/IntsListTest.java \
  -metric org.tori.metrics.StateFieldCoverage \
  -metric-config src/test/resources/state_field_coverage.properties
```

This will report, for each assertion in the test class, the proportion of fields in `IntsList` that are accessed by that assertion. The configuration file specifies `IntsList` as the target class and the execution level as `assert`, meaning the metric is computed individually for each assertion statement.

### Real Example

Here we provide an example of how to use tori to analyze a real test class from an open source project. 
These steps can be followed to analyze any test class from any project.

In this example, we will analyze the oracles in the `testGetMoreThanAvailable` test method the test class [`ByteArrayInputStreamWithPosTest`](https://github.com/apache/flink/blob/883ed7d37bcf5c7e38ceb01b3652a161bc26feae/flink-core/src/test/java/org/apache/flink/core/memory/ByteArrayInputStreamWithPosTest.java) from the popular open source project Apache Flink, using the StateFieldCoverage metric. The test class contains tests for the `ByteArrayInputStreamWithPos` class, which is a custom implementation of `ByteArrayInputStream` used in Flink.
Follow these steps:

1. Clone the `apache/flink` repository: 

```bash
mkdir -p tmp
cd tmp
git clone https://github.com/apache/flink tmp/flink
```

> [!Note]
> If you are using Docker, the apache flink code is already included, so you can skip the download step.

2. Create a metric configuration file specifying `ByteArrayInputStreamWithPos` as the target class and set the execution level to `test_method`, meaning the metric will be computed treating all assertions in the specified test method as a single one (computing the union of accessed fields across all assertions in the method):

```properties
target_class=tmp/flink/flink-core/src/main/java/org/apache/flink/core/memory/ByteArrayInputStreamWithPos.java
exec_level=test_method
```

3. Run tori with the following command:

```bash
java -jar build/libs/tori-1.0.0-all.jar \
  -t tmp/flink/flink-core/src/test/java/org/apache/flink/core/memory/ByteArrayInputStreamWithPosTest.java \
  -m testGetMoreThanAvailable \
  -metric org.tori.metrics.StateFieldCoverage \
  -metric-config apache_flink_test.properties
```

This will report, for the `testGetMoreThanAvailable` test method, the proportion of fields in `ByteArrayInputStreamWithPos` that are accessed by assertions in that method.
For this example, the state field coverage score is 0.75, meaning that 3 out of the 4 fields in `ByteArrayInputStreamWithPos` are accessed by assertions in the `testGetMoreThanAvailable` test method, 
and the only uncovered field is `mark`, which is not accessed by any assertion in that method.

### Command-Line Options

- `-t, --test-file <FILE>`: Path to the test file (required)
- `-m, --test-method <METHOD>`: Name of the specific test method to analyze (optional)
- `-metric <METRIC_CLASS>`: Class name of the metric to use for oracle assessment (optional)
- `-mc, -metric-config <CONFIG_FILE>`: Path to the properties file with metric configuration (optional)

## Metrics

Tori supports metrics that can assess the quality of test oracles. Currently, two metrics are available:

### StateFieldCoverage

Class to be specified: `org.tori.metrics.StateFieldCoverage`

Measures the proportion of fields in a target class that are accessed by test assertions. This metric helps assess how thoroughly an oracle tests the state of an object.

**Properties in configuration file:**
- `target_class`: Path to the target class file(s). Multiple classes can be specified as comma-separated values (required). If multiple classes are specified, the metric computes the coverage considering all fields across those classes (union).
- `exec_level`: Execution level - `assert`, `test_method`, or `test_class` (optional, default: `assert`)
- `iterable_field_tracking`: Enable or disable iterable field tracking (optional, default: `true`)

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

Class to be specified: `org.tori.metrics.CheckedVarsMetric`

Measures the proportion of variables declared in a test that are actually checked in assertions. This metric evaluates how comprehensively an oracle tests the declared variables.

**Properties in configuration file:**
- `exec_level`: Execution level - `assert`, `test_method`, or `test_class` (optional, default: `assert`)


## Supported Assertions

Tori recognizes all assert statements which name starts with `assert` and are statically imported from any class. This includes, but is not limited to, assertions from popular testing frameworks such as JUnit and TestNG. Examples of supported assertions include:

- `assertEquals`, `assertNotEquals`
- `assertTrue`, `assertFalse`
- `assertNull`, `assertNotNull`
- `assertSame`, `assertNotSame`
- `assertArrayEquals`
- `assertThrows`, `assertDoesNotThrow`
- `assertAll`
- `assertTimeout`, `assertTimeoutPreemptively`
- And more...

---
## Contact
If you experience any issues, please submit an issue or contact us at facundom@ucm.es!