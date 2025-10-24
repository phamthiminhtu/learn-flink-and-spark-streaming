# Java Syntax Refresher

A comprehensive guide to Java syntax with examples from your Flink project.

---

## Table of Contents
1. [Basic Structure](#basic-structure)
2. [Variables and Data Types](#variables-and-data-types)
3. [Operators](#operators)
4. [Control Flow](#control-flow)
5. [Methods](#methods)
6. [Classes and Objects](#classes-and-objects)
7. [Access Modifiers](#access-modifiers)
8. [Collections](#collections)
9. [Exception Handling](#exception-handling)
10. [Modern Java Features](#modern-java-features)

---

## Basic Structure

### Package Declaration
```java
package com.streaming.flink.model;  // Must be first line (after comments)
```
- Groups related classes together
- Matches directory structure: `com/streaming/flink/model/`

### Import Statements
```java
import java.io.Serializable;           // Import single class
import java.util.*;                    // Import all classes from package (not recommended)
import org.apache.flink.api.common.*;  // Import multiple classes
```

### Class Declaration
```java
public class ClickstreamEvent {
    // Class body
}
```

### Main Method (Entry Point)
```java
public static void main(String[] args) throws Exception {
    // Program starts here
}
```

---

## Variables and Data Types

### Primitive Types
```java
// Integer types
byte    b = 127;           // 8-bit: -128 to 127
short   s = 32000;         // 16-bit: -32,768 to 32,767
int     i = 2147483647;    // 32-bit: -2^31 to 2^31-1
long    l = 9223372036L;   // 64-bit: -2^63 to 2^63-1 (note the L suffix)

// Floating point
float   f = 3.14f;         // 32-bit (note the f suffix)
double  d = 3.14159;       // 64-bit (default for decimals)

// Other
boolean flag = true;       // true or false
char    c = 'A';          // Single character (16-bit Unicode)
```

### Reference Types (Objects)
```java
String name = "John";              // String is a class, not primitive
String[] names = new String[5];    // Array of strings
Integer num = 42;                  // Wrapper class for int
Long timestamp = 1234567890L;      // Wrapper class for long
```

### Variable Declaration Patterns
```java
// Declaration
String eventId;

// Declaration + initialization
String eventId = "evt_123";

// Multiple variables
String eventId, userId, sessionId;

// Constants (final = cannot change)
final String KAFKA_TOPIC = "clickstream";
private static final long serialVersionUID = 1L;
```

### Type Inference (Java 10+)
```java
var name = "John";              // Compiler infers String
var count = 42;                 // Compiler infers int
var list = new ArrayList<>();   // Compiler infers ArrayList
```

---

## Operators

### Arithmetic Operators
```java
int a = 10, b = 3;

int sum = a + b;           // 13 (addition)
int diff = a - b;          // 7  (subtraction)
int product = a * b;       // 30 (multiplication)
int quotient = a / b;      // 3  (integer division)
int remainder = a % b;     // 1  (modulo)

// Increment/decrement
a++;    // a = a + 1 (post-increment)
++a;    // a = a + 1 (pre-increment)
b--;    // b = b - 1 (post-decrement)
--b;    // b = b - 1 (pre-decrement)

// Compound assignment
a += 5;  // a = a + 5
a -= 5;  // a = a - 5
a *= 5;  // a = a * 5
a /= 5;  // a = a / 5
```

### Comparison Operators
```java
boolean result;

result = (a == b);    // Equal to
result = (a != b);    // Not equal to
result = (a > b);     // Greater than
result = (a < b);     // Less than
result = (a >= b);    // Greater than or equal
result = (a <= b);    // Less than or equal
```

### Logical Operators
```java
boolean x = true, y = false;

boolean and = x && y;    // Logical AND (both must be true)
boolean or = x || y;     // Logical OR (at least one true)
boolean not = !x;        // Logical NOT (inverts)

// Short-circuit evaluation
if (x != null && x.length() > 0) {  // If x is null, x.length() not evaluated
    // Safe to use x
}
```

### String Concatenation
```java
String first = "Hello";
String last = "World";

String full = first + " " + last;              // "Hello World"
String message = "Count: " + 42;               // "Count: 42" (auto-converts)
String path = "s3a://" + bucket + "/" + key;   // Concatenation
```

### Ternary Operator
```java
// Syntax: condition ? valueIfTrue : valueIfFalse

int max = (a > b) ? a : b;           // Returns larger value
String status = (age >= 18) ? "Adult" : "Minor";

// From your code:
String servers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
```

---

## Control Flow

### If-Else Statements
```java
// Simple if
if (event != null) {
    processEvent(event);
}

// If-else
if (count > 100) {
    System.out.println("High");
} else {
    System.out.println("Low");
}

// If-else-if ladder
if (score >= 90) {
    grade = "A";
} else if (score >= 80) {
    grade = "B";
} else if (score >= 70) {
    grade = "C";
} else {
    grade = "F";
}

// From your code: ClickstreamEvent.java equals() method
if (this == o) return true;
if (o == null || getClass() != o.getClass()) return false;
```

### Switch Statements
```java
// Traditional switch
switch (deviceType) {
    case "mobile":
        System.out.println("Mobile device");
        break;  // Important! Without break, falls through to next case
    case "desktop":
        System.out.println("Desktop device");
        break;
    case "tablet":
        System.out.println("Tablet device");
        break;
    default:
        System.out.println("Unknown device");
}

// Java 14+ Switch expressions (more modern)
String message = switch (deviceType) {
    case "mobile" -> "Mobile device";
    case "desktop" -> "Desktop device";
    case "tablet" -> "Tablet device";
    default -> "Unknown device";
};
```

### For Loops
```java
// Traditional for loop
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}

// Enhanced for loop (for-each)
String[] names = {"Alice", "Bob", "Charlie"};
for (String name : names) {
    System.out.println(name);
}

// From your code: Iterating through window elements
for (ClickstreamEvent event : elements) {
    EnrichedClickstreamEvent enriched =
        EnrichedClickstreamEvent.fromEvent(event, windowStart, windowEnd);
    out.collect(enriched);
}
```

### While Loops
```java
// While loop (check condition first)
int count = 0;
while (count < 10) {
    System.out.println(count);
    count++;
}

// Do-while loop (execute at least once)
int num = 0;
do {
    System.out.println(num);
    num++;
} while (num < 10);
```

### Break and Continue
```java
// Break: exit loop entirely
for (int i = 0; i < 10; i++) {
    if (i == 5) break;  // Stops at 5
    System.out.println(i);
}

// Continue: skip to next iteration
for (int i = 0; i < 10; i++) {
    if (i % 2 == 0) continue;  // Skip even numbers
    System.out.println(i);      // Only prints odd numbers
}
```

---

## Methods

### Method Declaration
```java
// Syntax: accessModifier returnType methodName(parameters) { body }

public String getEventId() {
    return eventId;
}

public void setEventId(String eventId) {
    this.eventId = eventId;  // 'this' refers to current object
}

// Method with multiple parameters
public ClickstreamEvent(String eventId, String userId, String sessionId) {
    this.eventId = eventId;
    this.userId = userId;
    this.sessionId = sessionId;
}
```

### Return Types
```java
// Returns a value
public int calculateSum(int a, int b) {
    return a + b;
}

// Returns nothing (void)
public void printMessage(String message) {
    System.out.println(message);
    // No return statement needed (or use 'return;' to exit early)
}

// Returns an object
public ClickstreamEvent getEvent() {
    return new ClickstreamEvent();
}
```

### Method Overloading
Same method name, different parameters:
```java
// From your code: ClickstreamEvent constructors
public ClickstreamEvent() {
    // No-argument constructor
}

public ClickstreamEvent(String eventId, String userId, String sessionId,
                       String eventType, String pageUrl, Long timestamp,
                       String ipAddress, String userAgent, String referrer,
                       String country, String deviceType) {
    // All-arguments constructor
}
```

### Static Methods
```java
// Belongs to class, not instance
public static void configureS3Access(StreamExecutionEnvironment env) {
    // Can be called without creating an object:
    // ClickstreamProcessor.configureS3Access(env);
}

// Static methods can only access static fields
private static final String KAFKA_TOPIC = "clickstream";

public static String getTopic() {
    return KAFKA_TOPIC;  // OK: accessing static field
    // return this.eventId;  // ERROR: 'this' doesn't exist in static context
}
```

### Varargs (Variable Arguments)
```java
// Accept variable number of arguments
public void printAll(String... messages) {  // Note: ...
    for (String message : messages) {
        System.out.println(message);
    }
}

// Can be called with any number of arguments
printAll("Hello");
printAll("Hello", "World");
printAll("One", "Two", "Three");
```

---

## Classes and Objects

### Class Structure
```java
public class ClickstreamEvent implements Serializable {

    // 1. Static fields (class-level)
    private static final long serialVersionUID = 1L;

    // 2. Instance fields (object-level)
    private String eventId;
    private String userId;

    // 3. Constructors
    public ClickstreamEvent() {
        // Initialize object
    }

    // 4. Methods
    public String getEventId() {
        return eventId;
    }

    // 5. Nested classes (if any)
    public static class Builder {
        // Builder pattern
    }
}
```

### Creating Objects
```java
// Using new keyword
ClickstreamEvent event = new ClickstreamEvent();

// Using constructor with arguments
ClickstreamEvent event = new ClickstreamEvent(
    "evt_123",
    "user_456",
    "session_789",
    // ... more arguments
);

// Using builder pattern
KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
    .setBootstrapServers("localhost:9092")
    .setTopics("clickstream")
    .build();
```

### this Keyword
```java
public class Person {
    private String name;

    // 'this' refers to current object
    public void setName(String name) {
        this.name = name;  // this.name = instance field, name = parameter
    }

    // Call another constructor
    public Person() {
        this("Default Name");  // Calls constructor below
    }

    public Person(String name) {
        this.name = name;
    }
}
```

### Inheritance (extends)
```java
// Parent class
public class ClickstreamEvent {
    private String eventId;
    private String userId;
}

// Child class inherits from parent
public class EnrichedClickstreamEvent extends ClickstreamEvent {
    // Has all fields from ClickstreamEvent PLUS:
    private String eventTimestamp;
    private String eventTimestampStart;
    private String eventTimestampEnd;
}
```

### Implementing Interfaces
```java
// Interface defines contract
public interface Serializable {
    // Methods to implement
}

// Class implements interface
public class ClickstreamEvent implements Serializable {
    // Must implement all interface methods
}

// Multiple interfaces
public class MyClass implements Serializable, Comparable<MyClass> {
    // Implement methods from both interfaces
}
```

### Override Methods
```java
@Override  // Annotation: tells compiler you're overriding
public boolean equals(Object o) {
    if (this == o) return true;  // Same object reference
    if (o == null || getClass() != o.getClass()) return false;  // Null or different class

    ClickstreamEvent that = (ClickstreamEvent) o;  // Cast to correct type
    return Objects.equals(eventId, that.eventId) &&
           Objects.equals(userId, that.userId);
}

@Override
public String toString() {
    return "ClickstreamEvent{" +
            "eventId='" + eventId + '\'' +
            ", userId='" + userId + '\'' +
            '}';
}
```

---

## Access Modifiers

Controls who can access your class members:

| Modifier | Class | Package | Subclass | World |
|----------|-------|---------|----------|-------|
| `public` | ✓ | ✓ | ✓ | ✓ |
| `protected` | ✓ | ✓ | ✓ | ✗ |
| (no modifier) | ✓ | ✓ | ✗ | ✗ |
| `private` | ✓ | ✗ | ✗ | ✗ |

```java
public class Example {
    public String publicField;        // Accessible everywhere
    protected String protectedField;  // Accessible in package + subclasses
    String packageField;              // Accessible in package only
    private String privateField;      // Accessible in this class only

    // Best practice: Make fields private, provide public getters/setters
    private String eventId;

    public String getEventId() {      // Public getter
        return eventId;
    }

    public void setEventId(String eventId) {  // Public setter
        this.eventId = eventId;
    }
}
```

### Other Modifiers

```java
// final: cannot be changed
final String CONSTANT = "value";
public final void cannotOverride() { }
public final class CannotExtend { }

// static: belongs to class, not instance
static int counter = 0;
public static void staticMethod() { }

// abstract: must be implemented by subclass
public abstract class AbstractClass {
    public abstract void mustImplement();
}
```

---

## Collections

### Arrays (Fixed Size)
```java
// Declaration
int[] numbers;
String[] names;

// Initialization
int[] numbers = new int[5];           // Creates array of size 5 (all zeros)
String[] names = {"Alice", "Bob"};    // Array literal

// Access elements (zero-indexed)
numbers[0] = 10;        // Set first element
int first = numbers[0]; // Get first element

// Length property
int size = numbers.length;

// Iterate
for (int i = 0; i < numbers.length; i++) {
    System.out.println(numbers[i]);
}

// Enhanced for loop
for (int num : numbers) {
    System.out.println(num);
}
```

### ArrayList (Dynamic Size)
```java
import java.util.ArrayList;

// Create
ArrayList<String> list = new ArrayList<>();

// Add elements
list.add("Alice");
list.add("Bob");
list.add(0, "Charlie");  // Insert at index 0

// Access
String first = list.get(0);

// Size
int size = list.size();

// Remove
list.remove(0);           // Remove by index
list.remove("Bob");       // Remove by value

// Iterate
for (String name : list) {
    System.out.println(name);
}
```

### HashMap (Key-Value Pairs)
```java
import java.util.HashMap;

// Create
HashMap<String, Integer> ages = new HashMap<>();

// Put key-value pairs
ages.put("Alice", 25);
ages.put("Bob", 30);

// Get value by key
int aliceAge = ages.get("Alice");  // 25

// Check if key exists
if (ages.containsKey("Alice")) {
    // ...
}

// Iterate
for (String name : ages.keySet()) {
    int age = ages.get(name);
    System.out.println(name + ": " + age);
}

// Or iterate entries
for (Map.Entry<String, Integer> entry : ages.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

---

## Exception Handling

### Try-Catch
```java
try {
    // Code that might throw exception
    int result = 10 / 0;  // ArithmeticException
} catch (ArithmeticException e) {
    // Handle specific exception
    System.out.println("Cannot divide by zero");
} catch (Exception e) {
    // Handle any other exception
    System.out.println("Error: " + e.getMessage());
} finally {
    // Always executes (optional)
    System.out.println("Cleanup code");
}
```

### Try-with-Resources
```java
// Automatically closes resources (files, streams, etc.)
try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"))) {
    String line = reader.readLine();
} catch (IOException e) {
    e.printStackTrace();
}
// reader.close() called automatically
```

### Throwing Exceptions
```java
public void validateAge(int age) throws IllegalArgumentException {
    if (age < 0) {
        throw new IllegalArgumentException("Age cannot be negative");
    }
}

// Method signature declares it may throw exception
public void readFile() throws IOException {
    // IOException might be thrown, caller must handle
}

// From your code: main method
public static void main(String[] args) throws Exception {
    // If exception occurs, program terminates
}
```

---

## Modern Java Features

### Lambda Expressions (Java 8+)
```java
// Old way: Anonymous inner class
Comparator<String> comparator = new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.compareTo(b);
    }
};

// Lambda way
Comparator<String> comparator = (a, b) -> a.compareTo(b);

// From your code: timestamp assigner
.withTimestampAssigner((event, timestamp) -> event.getTimestamp())

// Lambda syntax variations:
// No parameters
() -> System.out.println("Hello")

// One parameter (parentheses optional)
event -> event.getUserId()
(event) -> event.getUserId()

// Multiple parameters
(a, b) -> a + b

// Multiple statements (need braces and explicit return)
(a, b) -> {
    int sum = a + b;
    return sum * 2;
}
```

### Method References (Java 8+)
```java
// Lambda
list.forEach(item -> System.out.println(item));

// Method reference
list.forEach(System.out::println);

// From your code:
.keyBy(ClickstreamEvent::getUserId)

// Types of method references:
// 1. Static method
ClassName::staticMethod

// 2. Instance method of object
object::instanceMethod

// 3. Instance method of class
ClassName::instanceMethod

// 4. Constructor
ClassName::new
```

### Streams API (Java 8+)
```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David");

// Filter and collect
List<String> filtered = names.stream()
    .filter(name -> name.startsWith("A"))
    .collect(Collectors.toList());

// Map (transform)
List<Integer> lengths = names.stream()
    .map(String::length)
    .collect(Collectors.toList());

// Count
long count = names.stream()
    .filter(name -> name.length() > 4)
    .count();

// From your code: filtering null events
events = events.filter(event -> event != null);
```

### Generics (Java 5+)
```java
// Generic class
public class Box<T> {
    private T content;

    public void set(T content) {
        this.content = content;
    }

    public T get() {
        return content;
    }
}

// Usage
Box<String> stringBox = new Box<>();
stringBox.set("Hello");
String value = stringBox.get();  // No casting needed

// Multiple type parameters
public class Pair<K, V> {
    private K key;
    private V value;
}

// From your code:
KafkaSource<ClickstreamEvent> source = ...
DataStream<ClickstreamEvent> events = ...
```

### Optional (Java 8+)
```java
// Avoiding null pointer exceptions
Optional<String> optional = Optional.ofNullable(getString());

// Check if value present
if (optional.isPresent()) {
    String value = optional.get();
}

// Or use functional style
optional.ifPresent(value -> System.out.println(value));

// Provide default value
String value = optional.orElse("default");
String value = optional.orElseGet(() -> computeDefault());
```

---

## Common String Operations

```java
String str = "Hello World";

// Length
int len = str.length();  // 11

// Character at index
char ch = str.charAt(0);  // 'H'

// Substring
String sub = str.substring(0, 5);  // "Hello"

// Contains
boolean contains = str.contains("World");  // true

// Equals (case sensitive)
boolean equals = str.equals("Hello World");  // true

// Equals ignore case
boolean equalsIgnore = str.equalsIgnoreCase("hello world");  // true

// Replace
String replaced = str.replace("World", "Java");  // "Hello Java"

// Split
String[] parts = str.split(" ");  // ["Hello", "World"]

// Trim whitespace
String trimmed = "  text  ".trim();  // "text"

// Upper/Lower case
String upper = str.toUpperCase();  // "HELLO WORLD"
String lower = str.toLowerCase();  // "hello world"

// Starts/Ends with
boolean starts = str.startsWith("Hello");  // true
boolean ends = str.endsWith("World");      // true

// Format string
String formatted = String.format("Name: %s, Age: %d", "Alice", 25);
```

---

## Common Patterns in Your Codebase

### 1. POJO Pattern
```java
public class ClickstreamEvent implements Serializable {
    // Private fields
    private String eventId;

    // No-arg constructor (required for frameworks)
    public ClickstreamEvent() { }

    // All-args constructor
    public ClickstreamEvent(String eventId, ...) {
        this.eventId = eventId;
    }

    // Getters and setters
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    // equals, hashCode, toString
    @Override
    public boolean equals(Object o) { ... }

    @Override
    public int hashCode() { ... }

    @Override
    public String toString() { ... }
}
```

### 2. Builder Pattern
```java
// Fluent API for object creation
KafkaSource<ClickstreamEvent> source = KafkaSource.<ClickstreamEvent>builder()
    .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
    .setTopics(KAFKA_TOPIC)
    .setStartingOffsets(OffsetsInitializer.earliest())
    .setValueOnlyDeserializer(new ClickstreamEventDeserializationSchema())
    .setProperty("group.id", "flink-clickstream-processor")
    .build();
```

### 3. Factory Method Pattern
```java
// Static method that creates object
public static EnrichedClickstreamEvent fromEvent(
    ClickstreamEvent event,
    long windowStart,
    long windowEnd
) {
    EnrichedClickstreamEvent enriched = new EnrichedClickstreamEvent();
    // ... populate fields
    return enriched;
}

// Usage:
EnrichedClickstreamEvent enriched =
    EnrichedClickstreamEvent.fromEvent(event, windowStart, windowEnd);
```

### 4. Anonymous Inner Class (Flink Pattern)
```java
.process(new ProcessWindowFunction<ClickstreamEvent, EnrichedClickstreamEvent, String, TimeWindow>() {
    @Override
    public void process(
        String key,
        Context context,
        Iterable<ClickstreamEvent> elements,
        Collector<EnrichedClickstreamEvent> out
    ) {
        // Implementation
    }
})
```

---

## Quick Reference

### Naming Conventions
```java
// Classes: PascalCase
public class ClickstreamEvent { }

// Methods/Variables: camelCase
public void getUserId() { }
private String eventId;

// Constants: UPPER_SNAKE_CASE
private static final String KAFKA_TOPIC = "clickstream";

// Packages: lowercase
package com.streaming.flink.model;
```

### Comments
```java
// Single-line comment

/*
 * Multi-line comment
 * Can span multiple lines
 */

/**
 * Javadoc comment (for documentation)
 * Used for classes, methods, fields
 * @param event The clickstream event
 * @return Enriched event with metadata
 */
public EnrichedClickstreamEvent enrich(ClickstreamEvent event) {
    // ...
}
```

---

## Practice Exercises

Try modifying your codebase:

1. **Add a new field** to `ClickstreamEvent` (e.g., `deviceBrand`)
   - Add private field
   - Update constructors
   - Add getter/setter
   - Update equals/hashCode/toString

2. **Create a utility method** in `ClickstreamProcessor`
   ```java
   private static boolean isValidEvent(ClickstreamEvent event) {
       return event != null && event.getEventId() != null;
   }
   ```

3. **Use enhanced filtering**
   ```java
   events = events
       .filter(event -> event != null)
       .filter(event -> event.getEventType().equals("click"));
   ```

4. **Add logging** with formatted strings
   ```java
   LOG.info("Processing event: {} for user: {}",
            event.getEventId(), event.getUserId());
   ```

---

## Additional Resources

- **Oracle Java Tutorials**: https://docs.oracle.com/javase/tutorial/
- **Java API Documentation**: https://docs.oracle.com/en/java/javase/11/docs/api/
- **Apache Flink Documentation**: https://nightlies.apache.org/flink/flink-docs-stable/

---

*This refresher covers the essential Java syntax you'll encounter in the Flink streaming project. Practice reading and modifying the code to reinforce these concepts!*
