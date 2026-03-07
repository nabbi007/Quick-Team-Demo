# Design Document: QuickPoll API Testing Framework

## Overview

The QuickPoll API Testing Framework is a straightforward, maintainable test automation solution built with Rest Assured and Allure reporting. The framework provides automated testing capabilities across functional, contract, security, performance, and negative testing scenarios for the QuickPoll API.

### Design Goals

- **Simplicity**: Keep the design straightforward and easy to understand
- **Maintainability**: Follow SOLID and DRY principles to ensure code remains easy to modify and extend
- **Leverage Built-in Features**: Use Rest Assured's built-in capabilities rather than custom wrappers
- **Comprehensive Coverage**: Support functional, contract, security, performance, and negative testing
- **Clear Reporting**: Generate detailed, evidence-rich test reports using Allure
- **Flexibility**: Support multiple environments and configurable test execution

### Technology Stack

- **Java 17**: Primary programming language
- **Rest Assured**: HTTP client library for API testing with built-in request/response specifications
- **JUnit 5 (Jupiter)**: Test execution framework
- **jqwik**: Property-based testing library with JUnit 5 integration
- **Allure**: Test reporting framework
- **Maven**: Build and dependency management
- **JSON Schema Validator**: Contract validation (built into Rest Assured)

## Architecture

### High-Level Architecture

The framework follows a simplified layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                      Test Layer                              │
│  (Functional, Contract, Security, Performance, Negative)     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Service Layer                              │
│     (API Client, Test Data Manager, Performance Monitor)     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Support Layer                              │
│        (Config Manager, Auth Handler, Test Helpers)          │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                 Infrastructure Layer                         │
│        (Rest Assured, Allure, JUnit 5, Maven)                │
└─────────────────────────────────────────────────────────────┘
```

### Design Principles

**Simplicity First**
- Leverage Rest Assured's built-in RequestSpecification and ResponseSpecification
- Use Rest Assured's built-in logging and filtering capabilities
- Avoid custom wrappers when built-in features suffice

**Single Responsibility Principle (SRP)**
- Each class has one clear responsibility (e.g., ApiClient handles HTTP communication, ConfigurationManager handles configuration)
- Test classes focus only on test logic, delegating technical operations to service and support layers

**Open/Closed Principle (OCP)**
- Base classes allow extension without modification
- New test types can be added by extending BaseTest without changing existing code

**DRY Principle**
- Common HTTP operations are centralized in ApiClient
- Reusable assertions are extracted to TestHelper
- Configuration values are defined once in configuration files

**Dependency Inversion Principle (DIP)**
- High-level test classes depend on abstractions not concrete implementations
- Configuration and dependencies are injected rather than hard-coded

### Package Structure

```
src/test/java/com/amalitech/qa/
├── config/
│   ├── ConfigurationManager.java
│   └── TestConfiguration.java
├── client/
│   ├── ApiClient.java
│   └── AuthenticationHandler.java
├── models/
│   ├── request/
│   │   ├── CreatePollRequest.java
│   │   └── UpdatePollRequest.java
│   └── response/
│       ├── PollResponse.java
│       └── ErrorResponse.java
├── services/
│   ├── TestDataManager.java
│   └── PerformanceMonitor.java
├── utils/
│   └── TestHelper.java
├── base/
│   └── BaseTest.java
└── tests/
    ├── functional/
    │   ├── PollCrudTests.java
    │   └── VotingTests.java
    ├── contract/
    │   └── SchemaValidationTests.java
    ├── security/
    │   ├── AuthenticationTests.java
    │   └── AuthorizationTests.java
    ├── performance/
    │   └── ResponseTimeTests.java
    └── negative/
        └── ErrorHandlingTests.java

src/test/resources/
├── config/
│   ├── dev.properties
│   ├── staging.properties
│   └── prod.properties
├── schemas/
│   ├── poll-response-schema.json
│   └── error-response-schema.json
├── testdata/
│   ├── valid-polls.json
│   └── invalid-polls.json
└── allure.properties
```

## Components and Interfaces

### 1. ConfigurationManager

**Responsibility**: Load and provide access to environment-specific configuration settings.

**Interface**:
```java
public class ConfigurationManager {
    private static ConfigurationManager instance;
    private Properties properties;
    
    public static ConfigurationManager getInstance();
    public String getBaseUrl();
    public int getConnectionTimeout();
    public int getResponseTimeout();
    public String getAuthUsername();
    public String getAuthPassword();
    public int getPerformanceThreshold(String endpointType);
    public String getEnvironment();
    public void loadConfiguration(String environment);
}
```

**Key Design Decisions**:
- Singleton pattern ensures single configuration instance across all tests
- Lazy initialization for efficient resource usage
- Environment-specific property files (dev.properties, staging.properties, prod.properties)
- Type-safe getter methods with appropriate return types

### 2. ApiClient

**Responsibility**: Provide reusable HTTP operations using Rest Assured's fluent API with automatic authentication and logging.

**Interface**:
```java
public class ApiClient {
    private RequestSpecification baseRequestSpec;
    private ResponseSpecification baseResponseSpec;
    private AuthenticationHandler authHandler;
    
    public ApiClient(AuthenticationHandler authHandler);
    
    // Direct access to Rest Assured's fluent API
    public RequestSpecification given();
    
    // Convenience methods that use the fluent API
    public Response get(String endpoint);
    public Response get(String endpoint, Map<String, ?> queryParams);
    public Response post(String endpoint, Object requestBody);
    public Response put(String endpoint, Object requestBody);
    public Response patch(String endpoint, Object requestBody);
    public Response delete(String endpoint);
    public Response delete(String endpoint, String resourceId);
}
```

**Key Design Decisions**:
- Use Rest Assured's RequestSpecification for base configuration (base URL, headers, logging)
- Use Rest Assured's ResponseSpecification for common response expectations
- Expose `given()` method for direct access to Rest Assured's fluent API when needed
- Automatic authentication header injection via RequestSpecification filters
- Automatic request/response logging using Rest Assured's built-in logging
- Automatic Allure attachment using Rest Assured's built-in Allure integration
- No custom serialization/deserialization - use Rest Assured's built-in JSON handling
- Convenience methods wrap the fluent API for common operations

### 3. AuthenticationHandler

**Responsibility**: Manage authentication tokens, credentials, and authorization headers.

**Interface**:
```java
public class AuthenticationHandler {
    private String currentToken;
    private Map<String, String> userCredentials;
    
    public String authenticate(String username, String password);
    public String getAuthToken();
    public void setAuthToken(String token);
    public boolean isTokenValid();
    public void clearToken();
    public Map<String, String> getAuthHeaders();
    public void loginAsUser(String role);
}
```

**Key Design Decisions**:
- Stateful token management for session-based authentication
- Support for multiple user roles (admin, user, guest)
- Token validation to detect expiration
- Encapsulation of authentication logic away from test code

### 4. TestDataManager

**Responsibility**: Create, manage, and clean up test data with isolation and uniqueness guarantees.

**Interface**:
```java
public class TestDataManager {
    private List<String> createdResourceIds;
    private ApiClient apiClient;
    
    public TestDataManager(ApiClient apiClient);
    
    public String createTestPoll(Map<String, Object> pollData);
    public String createTestPollWithDefaults();
    public void createPrerequisiteData(String testScenario);
    public void cleanupTestData();
    public void cleanupResource(String resourceId);
    public String generateUniqueId();
    public Map<String, Object> loadTestDataFromFile(String filename);
}
```

**Key Design Decisions**:
- Tracks created resources for automatic cleanup
- Guaranteed cleanup even on test failure (via @AfterEach hooks)
- Unique ID generation to prevent data conflicts
- Support for loading test data from external files

### 5. Schema Validation (Built into Rest Assured)

**Responsibility**: Validate API response structures against JSON schemas using Rest Assured's built-in capabilities.

**Usage Pattern**:
```java
// Rest Assured's built-in schema validation
given()
    .when()
    .get("/polls/{id}")
    .then()
    .assertThat()
    .body(matchesJsonSchemaInClasspath("schemas/poll-response-schema.json"));
```

**Key Design Decisions**:
- Use Rest Assured's built-in `json-schema-validator` module
- No custom SchemaValidator class needed
- Schema files stored in `src/test/resources/schemas/`
- Direct integration with Rest Assured's assertion chain
- Detailed mismatch reporting provided by the library

### 6. PerformanceMonitor

**Responsibility**: Measure, validate, and report API response times.

**Interface**:
```java
public class PerformanceMonitor {
    private Map<String, List<Long>> responseTimeHistory;
    
    public long measureResponseTime(Response response);
    public void validateResponseTime(Response response, long thresholdMs);
    public void validateResponseTime(Response response, String endpointType);
    public void attachResponseTimeToReport(Response response);
    public double calculateAverageResponseTime(String endpoint);
    public void reportPerformanceViolation(String endpoint, long actualTime, long threshold);
}
```

**Key Design Decisions**:
- Automatic response time extraction from Rest Assured Response
- Configurable thresholds per endpoint type
- Historical tracking for trend analysis
- Automatic Allure attachment for visibility

### 7. Allure Evidence (Built into Rest Assured)

**Responsibility**: Capture and attach test evidence to Allure reports using Rest Assured's built-in integration.

**Usage Pattern**:
```java
// Rest Assured automatically attaches requests/responses to Allure when configured
given()
    .filter(new AllureRestAssured())  // Automatic evidence capture
    .when()
    .get("/polls")
    .then()
    .statusCode(200);

// Manual Allure attachments when needed
Allure.addAttachment("Test Data", "application/json", testDataJson);
```

**Key Design Decisions**:
- Use Rest Assured's `AllureRestAssured` filter for automatic request/response capture
- No custom EvidenceCollector class needed
- Use Allure's API directly for custom attachments
- Automatic formatting and readability provided by the library

### 8. TestHelper

**Responsibility**: Provide reusable assertion and validation methods.

**Interface**:
```java
public class TestHelper {
    public static void assertStatusCode(Response response, int expectedCode);
    public static void assertResponseContains(Response response, String key, Object expectedValue);
    public static void assertResponseNotNull(Response response, String key);
    public static void assertErrorMessage(Response response, String expectedMessage);
    public static void assertCollectionSize(Response response, String jsonPath, int expectedSize);
    public static void assertDateFormat(String dateString, String format);
    public static void assertValidUUID(String uuid);
}
```

**Key Design Decisions**:
- Static utility methods for easy access
- Descriptive assertion failures with context
- Common validation patterns extracted from test code
- Integration with JUnit 5 assertions

### 9. BaseTest

**Responsibility**: Provide common setup, teardown, and initialization for all test classes.

**Interface**:
```java
public abstract class BaseTest {
    protected ApiClient apiClient;
    protected TestDataManager testDataManager;
    protected PerformanceMonitor performanceMonitor;
    protected ConfigurationManager config;
    
    @BeforeAll
    public static void setupSuite();
    
    @BeforeEach
    public void setupTest();
    
    @AfterEach
    public void teardownTest();
    
    @AfterAll
    public static void teardownSuite();
}
```

**Key Design Decisions**:
- Abstract class allows extension by all test classes
- JUnit 5 lifecycle hooks for proper setup/teardown
- Shared component initialization (ApiClient, TestDataManager, etc.)
- Protected access to components for test class usage
- No parallel execution concerns - simplified lifecycle management

## Data Models

### Request Models

Request models represent the structure of data sent to the API.

**CreatePollRequest**:
```java
public class CreatePollRequest {
    private String question;
    private List<String> options;
    private String createdBy;
    private LocalDateTime expiresAt;
    
    // Constructors, getters, setters, builder pattern
}
```

**UpdatePollRequest**:
```java
public class UpdatePollRequest {
    private String question;
    private List<String> options;
    private LocalDateTime expiresAt;
    
    // Constructors, getters, setters, builder pattern
}
```

**VoteRequest**:
```java
public class VoteRequest {
    private String pollId;
    private int optionIndex;
    private String voterId;
    
    // Constructors, getters, setters, builder pattern
}
```

### Response Models

Response models represent the structure of data received from the API.

**PollResponse**:
```java
public class PollResponse {
    private String id;
    private String question;
    private List<String> options;
    private List<Integer> votes;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    // Constructors, getters, setters
}
```

**ErrorResponse**:
```java
public class ErrorResponse {
    private int statusCode;
    private String message;
    private String timestamp;
    private String path;
    private List<String> details;
    
    // Constructors, getters, setters
}
```

**VoteResponse**:
```java
public class VoteResponse {
    private String pollId;
    private boolean success;
    private String message;
    private List<Integer> updatedVotes;
    
    // Constructors, getters, setters
}
```

### Model Design Decisions

- **Immutability**: Response models are immutable (final fields, no setters) to prevent accidental modification
- **Builder Pattern**: Request models use builder pattern for flexible object construction
- **Validation**: Request models include basic validation (e.g., @NotNull, @Size) for early error detection
- **Separation**: Clear separation between request and response models even when similar
- **No Custom Serialization**: Rely on Rest Assured's built-in JSON handling with standard Java types


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Configuration Loading

*For any* valid environment-specific configuration file, loading it through ConfigurationManager should make all configuration values accessible and correctly typed.

**Validates: Requirements 1.1, 1.2**

### Property 2: Automatic Authentication Header Injection

*For any* HTTP request made through ApiClient, authentication headers should be automatically attached to the request via RequestSpecification filters.

**Validates: Requirements 2.2**

### Property 3: Request Logging

*For any* HTTP request made through ApiClient, request details should be logged using Rest Assured's built-in logging capabilities.

**Validates: Requirements 2.3**

### Property 4: Response Logging

*For any* HTTP response received through ApiClient, response details should be logged using Rest Assured's built-in logging capabilities.

**Validates: Requirements 2.4**

### Property 5: Response Type Consistency

*For any* HTTP operation method in ApiClient (GET, POST, PUT, PATCH, DELETE), the return type should be a Rest Assured Response object.

**Validates: Requirements 2.5**

### Property 6: CRUD Status Code Validation

*For any* CRUD operation test, the test framework should validate that the HTTP status code matches the expected value.

**Validates: Requirements 3.5**

### Property 7: CRUD Response Body Validation

*For any* CRUD operation test, the test framework should validate that the response body content matches the expected data.

**Validates: Requirements 3.6**

### Property 8: Test Data Cleanup

*For any* test data created by TestDataManager, cleanup should be executed after test completion regardless of test outcome.

**Validates: Requirements 3.8**

### Property 9: Comprehensive Schema Validation

*For any* API response and corresponding JSON schema, Rest Assured's schema validator should validate the complete structure including required fields, data types, and field constraints, reporting detailed mismatches on failure.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

### Property 10: Multi-Role Authentication Support

*For any* user role defined in the system, AuthenticationHandler should be able to authenticate and provide appropriate credentials for that role.

**Validates: Requirements 5.7**

### Property 11: Response Time Measurement

*For any* API request executed through the framework, PerformanceMonitor should measure and record the response time in milliseconds.

**Validates: Requirements 6.1**

### Property 12: Performance Threshold Validation

*For any* API response time and configured threshold, PerformanceMonitor should correctly validate whether the response time is within the threshold.

**Validates: Requirements 6.2**

### Property 13: Performance Violation Reporting

*For any* response time that exceeds the configured threshold, PerformanceMonitor should report the violation in test results.

**Validates: Requirements 6.3**

### Property 14: Performance Metrics Attachment

*For any* performance measurement, PerformanceMonitor should attach the response time metrics to Allure Reporter.

**Validates: Requirements 6.5**

### Property 15: Configurable Performance Thresholds

*For any* endpoint type, the framework should support configuring and retrieving a specific performance threshold value.

**Validates: Requirements 6.6**

### Property 16: Average Response Time Calculation

*For any* set of response times for an endpoint, PerformanceMonitor should calculate the correct arithmetic mean.

**Validates: Requirements 6.7**

### Property 17: Error Status Code Validation

*For any* error scenario test, the test framework should validate that the error response status code matches the expected error code.

**Validates: Requirements 7.6**

### Property 18: Error Message Validation

*For any* error scenario test, the test framework should validate that error messages are present and meet consistency requirements.

**Validates: Requirements 7.7**

### Property 19: Unique Identifier Generation

*For any* set of identifiers generated by TestDataManager, all identifiers should be unique with no duplicates.

**Validates: Requirements 8.3**

### Property 20: Prerequisite Data Creation

*For any* test scenario requiring prerequisite data, TestDataManager should be able to create the necessary data dependencies.

**Validates: Requirements 8.4**

### Property 21: Cleanup on Failure

*For any* test execution that fails, TestDataManager should still execute all cleanup operations for data created during that test.

**Validates: Requirements 8.5**

### Property 22: External Test Data Loading

*For any* valid test data file (JSON or CSV), the framework should be able to load and parse the data correctly.

**Validates: Requirements 8.7**

### Property 23: Test Categorization in Reports

*For any* test with feature, severity, or test type metadata, Allure Reporter should categorize it correctly in the generated report.

**Validates: Requirements 9.2**

### Property 24: HTTP Evidence Collection

*For any* test execution involving HTTP requests and responses, Rest Assured's AllureRestAssured filter should attach both request and response details to the Allure report.

**Validates: Requirements 9.3, 9.4**

### Property 25: Failure Stack Trace Attachment

*For any* test failure, JUnit 5 with Allure integration should attach the stack trace to the Allure report.

**Validates: Requirements 9.6**

### Property 26: Test Statistics Calculation

*For any* test execution, Allure Reporter should calculate and display accurate pass/fail statistics.

**Validates: Requirements 9.8**

### Property 27: Individual Test Class Execution

*For any* individual test class in the framework, it should be executable independently without requiring other test classes.

**Validates: Requirements 12.1**

### Property 28: Test Suite Execution

*For any* test suite containing multiple test classes, all classes in the suite should be executable together.

**Validates: Requirements 12.2**

### Property 29: Test Filtering by Tags

*For any* tag or category applied to tests, the framework should be able to filter and execute only tests matching that tag using JUnit 5's @Tag annotation.

**Validates: Requirements 12.5**

### Property 30: Environment Configuration Switching

*For any* environment (dev, staging, production), ConfigurationManager should be able to load the appropriate configuration.

**Validates: Requirements 12.6**

### Property 31: CI/CD Exit Code Handling

*For any* test execution result (all pass, some fail, all fail), the framework should return the appropriate exit code (0 for success, non-zero for failure).

**Validates: Requirements 12.7**

## Error Handling

### Framework-Level Error Handling

**Configuration Errors**:
- Missing configuration files: Fail fast with clear error message indicating which file is missing
- Invalid configuration values: Validate on load and throw ConfigurationException with details
- Missing required properties: Fail fast with list of missing properties

**API Communication Errors**:
- Connection timeouts: Retry once, then fail with timeout details attached to report
- Network errors: Capture full error details and attach to Allure report
- Unexpected status codes: Log actual vs expected, attach full request/response to report
- Malformed responses: Catch JSON parsing errors, attach raw response to report

**Test Data Management Errors**:
- Data creation failures: Log error, skip dependent tests, attempt cleanup of partial data
- Cleanup failures: Log warning but don't fail test, report cleanup issues separately
- File loading errors: Fail fast with clear message about file location and format issues

**Schema Validation Errors**:
- Schema file not found: Fail fast with clear message about expected schema location
- Schema parsing errors: Fail fast with details about schema format issues
- Validation mismatches: Collect all mismatches and report together (not fail-fast)

**Authentication Errors**:
- Invalid credentials: Fail fast with clear message (without exposing credentials)
- Token expiration: Attempt re-authentication once, then fail if unsuccessful
- Authorization failures: Distinguish from authentication, report with context

### Test-Level Error Handling

**Assertion Failures**:
- Capture full context: actual value, expected value, comparison operation
- Attach relevant evidence: request, response, test data used
- Provide actionable error messages with debugging hints

**Unexpected Exceptions**:
- Catch all uncaught exceptions in BaseTest
- Attach full stack trace to Allure report
- Mark test as failed with exception details
- Ensure cleanup still executes

**Resource Cleanup**:
- Use try-finally blocks to guarantee cleanup execution
- Log cleanup failures separately from test failures
- Track cleanup success rate for monitoring

### Logging Strategy

**Log Levels**:
- ERROR: Framework failures, configuration issues, unexpected exceptions
- WARN: Cleanup failures, retry attempts, deprecated usage
- INFO: Test execution flow, API calls, validation results
- DEBUG: Detailed request/response data, internal state changes

**Log Destinations**:
- Console: Summary information for real-time monitoring
- Allure Report: Full details attached to each test
- Log Files: Complete execution log for debugging (optional)


## Testing Strategy

### Dual Testing Approach

The framework itself requires comprehensive testing to ensure reliability. We will employ both unit testing and property-based testing:

**Unit Tests**: Verify specific examples, edge cases, and integration points
**Property Tests**: Verify universal properties across all inputs through randomization

Both approaches are complementary and necessary for comprehensive coverage. Unit tests catch concrete bugs in specific scenarios, while property tests verify general correctness across a wide input space.

### Property-Based Testing Configuration

**Library Selection**: We will use **jqwik** for property-based testing in Java, which integrates seamlessly with JUnit 5 and provides powerful property testing capabilities.

**Test Configuration**:
- Minimum 100 iterations per property test (due to randomization)
- Each property test must reference its design document property
- Tag format: `@Tag("Feature: quickpoll-api-testing-framework") @Tag("Property-{number}")`

**Example Property Test Structure**:
```java
@Property
@Tag("Feature: quickpoll-api-testing-framework")
@Tag("Property-19")
void uniqueIdentifierGeneration(@ForAll("idCounts") int count) {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < count; i++) {
        ids.add(testDataManager.generateUniqueId());
    }
    assertEquals(count, ids.size(), "All generated IDs should be unique");
}

@Provide
Arbitrary<Integer> idCounts() {
    return Arbitraries.integers().between(10, 1000);
}
```

### Unit Testing Strategy

**Focus Areas for Unit Tests**:

1. **Configuration Management**:
   - Test loading configuration from valid files
   - Test handling of missing configuration files
   - Test handling of invalid configuration values
   - Test environment switching (dev, staging, prod)

2. **API Client**:
   - Test each HTTP method (GET, POST, PUT, PATCH, DELETE) with mock server
   - Test authentication header injection via RequestSpecification
   - Test request/response logging using Rest Assured's logging
   - Test error handling for network failures

3. **Test Data Manager**:
   - Test data creation methods
   - Test cleanup execution after success
   - Test cleanup execution after failure
   - Test unique ID generation
   - Test loading data from files

4. **Performance Monitor**:
   - Test response time measurement
   - Test threshold validation
   - Test violation reporting
   - Test average calculation

5. **Authentication Handler**:
   - Test token management
   - Test multi-role support
   - Test token validation
   - Test token expiration handling

### Property-Based Testing Strategy

**Property Test Coverage**:

Each of the 31 correctness properties defined in this document must be implemented as a property-based test. Key property tests include:

1. **Configuration Loading (Property 1)**:
   - Generate random valid configuration files
   - Verify all values are accessible after loading

2. **Unique ID Generation (Property 19)**:
   - Generate large sets of IDs
   - Verify no duplicates exist

3. **Schema Validation (Property 9)**:
   - Generate random responses (valid and invalid)
   - Verify validation correctly identifies issues using Rest Assured's built-in validator

4. **Performance Threshold Validation (Property 12)**:
   - Generate random response times and thresholds
   - Verify correct pass/fail determination

### Integration Testing

**API Integration Tests**:
- Test against a real or mock QuickPoll API instance
- Verify end-to-end flows: create poll → vote → retrieve results
- Test error scenarios with actual API responses
- Validate schema against actual API responses using Rest Assured's schema validation

**Allure Integration Tests**:
- Execute sample tests and verify report generation
- Verify evidence attachment appears in reports via AllureRestAssured filter
- Verify categorization and statistics are correct

### Test Organization

**Test Package Structure**:
```
src/test/java/com/amalitech/qa/framework/
├── unit/
│   ├── config/
│   │   └── ConfigurationManagerTest.java
│   ├── client/
│   │   ├── ApiClientTest.java
│   │   └── AuthenticationHandlerTest.java
│   ├── services/
│   │   ├── TestDataManagerTest.java
│   │   └── PerformanceMonitorTest.java
│   └── utils/
│       └── TestHelperTest.java
├── property/
│   ├── ConfigurationPropertiesTest.java
│   ├── ApiClientPropertiesTest.java
│   ├── TestDataPropertiesTest.java
│   ├── SchemaValidationPropertiesTest.java
│   └── PerformancePropertiesTest.java
└── integration/
    ├── EndToEndApiTest.java
    └── AllureReportingTest.java
```

### Test Execution

**Local Development**:
```bash
# Run all framework tests
mvn test

# Run only unit tests
mvn test -Dgroups="unit"

# Run only property tests
mvn test -Dgroups="property"

# Run specific test class
mvn test -Dtest=ApiClientTest
```

**CI/CD Pipeline**:
```bash
# Run all tests with coverage
mvn clean test jacoco:report

# Generate Allure report
mvn allure:report

# Serve Allure report
mvn allure:serve
```

### Coverage Goals

**Code Coverage Targets**:
- Overall: 85% line coverage
- Critical components (ApiClient, TestDataManager, PerformanceMonitor): 95% line coverage
- Utility classes: 80% line coverage

**Property Coverage**:
- 100% of defined correctness properties must have corresponding property tests
- Each property test must run minimum 100 iterations

### Mocking Strategy

**Mock Usage**:
- Use Mockito for mocking external dependencies
- Mock HTTP responses for ApiClient unit tests using WireMock
- Mock file system for configuration loading tests
- Use WireMock for API integration tests

**Real Dependencies**:
- Use real Rest Assured library (not mocked)
- Use real Allure reporter (verify actual report generation)
- Use real JUnit 5 execution engine

### Continuous Improvement

**Test Maintenance**:
- Review and update tests when requirements change
- Add new property tests when new properties are identified
- Refactor tests to maintain DRY principles
- Monitor test execution time and optimize slow tests

**Quality Metrics**:
- Track test pass rate over time
- Monitor test execution duration
- Track code coverage trends
- Review property test failure patterns for insights


## Maven Configuration

### Project Structure

```
quickpoll-api-testing-framework/
├── pom.xml
├── src/
│   └── test/
│       ├── java/
│       │   └── com/amalitech/qa/
│       │       ├── config/
│       │       ├── client/
│       │       ├── models/
│       │       ├── services/
│       │       ├── utils/
│       │       ├── base/
│       │       └── tests/
│       └── resources/
│           ├── config/
│           ├── schemas/
│           ├── testdata/
│           └── allure.properties
├── target/
│   ├── test-classes/
│   ├── surefire-reports/
│   └── allure-results/
└── README.md
```

### Key Dependencies

**Core Testing Dependencies**:
```xml
<!-- Rest Assured for API testing -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.3.2</version>
    <scope>test</scope>
</dependency>

<!-- JUnit 5 (Jupiter) for test execution -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>

<!-- Allure JUnit 5 integration -->
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-junit5</artifactId>
    <version>2.24.0</version>
    <scope>test</scope>
</dependency>

<!-- Rest Assured Allure integration -->
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-rest-assured</artifactId>
    <version>2.24.0</version>
    <scope>test</scope>
</dependency>
```

**JSON Processing**:
```xml
<!-- JSON Schema Validator (built into Rest Assured) -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>5.3.2</version>
    <scope>test</scope>
</dependency>
```

**Property-Based Testing**:
```xml
<!-- jqwik for property-based testing with JUnit 5 -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.1</version>
    <scope>test</scope>
</dependency>
```

**Utilities**:
```xml
<!-- Lombok for reducing boilerplate -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>

<!-- SLF4J for logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>

<!-- Logback for logging implementation -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.4.11</version>
</dependency>

<!-- WireMock for API mocking in tests -->
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <version>2.35.0</version>
    <scope>test</scope>
</dependency>
```

### Maven Plugins

**Surefire Plugin** (Test Execution with JUnit 5):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.1</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>
        <systemPropertyVariables>
            <allure.results.directory>${project.build.directory}/allure-results</allure.results.directory>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

**Allure Plugin** (Report Generation):
```xml
<plugin>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-maven</artifactId>
    <version>2.12.0</version>
    <configuration>
        <reportVersion>2.24.0</reportVersion>
        <resultsDirectory>${project.build.directory}/allure-results</resultsDirectory>
    </configuration>
</plugin>
```

**Compiler Plugin**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>17</source>
        <target>17</target>
    </configuration>
</plugin>
```

### Dependency Version Compatibility

All dependency versions have been selected to ensure compatibility:
- Rest Assured 5.3.2 is compatible with JUnit 5
- Allure 2.24.0 is compatible with JUnit 5.10.1
- jqwik 1.8.1 works with Java 17 and JUnit 5
- No known conflicts between selected versions

### Build Profiles

**Development Profile**:
```xml
<profile>
    <id>dev</id>
    <activation>
        <activeByDefault>true</activeByDefault>
    </activation>
    <properties>
        <env>dev</env>
    </properties>
</profile>
```

**Staging Profile**:
```xml
<profile>
    <id>staging</id>
    <properties>
        <env>staging</env>
    </properties>
</profile>
```

**Production Profile**:
```xml
<profile>
    <id>prod</id>
    <properties>
        <env>prod</env>
    </properties>
</profile>
```

### Maven Commands

**Test Execution**:
```bash
# Run all tests (default dev environment)
mvn clean test

# Run tests for specific environment
mvn clean test -Pstaging

# Run specific test class
mvn test -Dtest=PollCrudTests

# Run tests with specific tag (JUnit 5)
mvn test -Dgroups=smoke

# Run tests excluding a tag
mvn test -DexcludedGroups=slow
```

**Report Generation**:
```bash
# Generate Allure report
mvn allure:report

# Generate and open Allure report
mvn allure:serve

# Clean previous results
mvn clean
```

**CI/CD Integration**:
```bash
# Full CI pipeline command
mvn clean test allure:report

# With coverage
mvn clean test jacoco:report allure:report
```

## Implementation Roadmap

### Phase 1: Foundation (Week 1)
1. Set up Maven project structure
2. Configure dependencies and plugins
3. Implement ConfigurationManager
4. Implement BaseTest with basic setup/teardown
5. Create initial configuration files

### Phase 2: Core Components (Week 2)
1. Implement ApiClient using Rest Assured's RequestSpecification and ResponseSpecification
2. Implement AuthenticationHandler
3. Implement TestHelper utilities
4. Configure Rest Assured's AllureRestAssured filter for evidence collection
5. Write unit tests for core components

### Phase 3: Service Layer (Week 3)
1. Implement TestDataManager
2. Implement PerformanceMonitor
3. Create request/response models
4. Set up JSON schema files for validation
5. Write unit tests for service layer

### Phase 4: Test Implementation (Week 4)
1. Implement functional test suite
2. Implement contract test suite
3. Implement security test suite
4. Implement performance test suite
5. Implement negative test suite

### Phase 5: Property Testing (Week 5)
1. Set up jqwik configuration with JUnit 5
2. Implement property tests for all 31 properties
3. Create custom generators for domain objects
4. Validate property test coverage

### Phase 6: Integration & Refinement (Week 6)
1. Integration testing with real API
2. Allure report customization
3. Documentation completion
4. Code review and refactoring
5. CI/CD pipeline setup

## Conclusion

This design provides a straightforward, maintainable, and scalable API testing framework for the QuickPoll API. By leveraging Rest Assured's built-in features (RequestSpecification, ResponseSpecification, AllureRestAssured filter, schema validation), using JUnit 5 for test execution, and employing property-based testing alongside traditional unit tests, the framework provides reliable automated testing capabilities across functional, contract, security, performance, and negative testing scenarios.

The simplified architecture ensures that components are easy to understand and extend as requirements evolve, while the comprehensive testing strategy ensures the framework itself is reliable and trustworthy.

