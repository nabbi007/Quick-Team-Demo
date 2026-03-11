# QuickPoll API Testing Framework - Project Architecture

## Table of Contents
1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Architecture Layers](#architecture-layers)
5. [Component Flow](#component-flow)
6. [File Connections](#file-connections)
7. [Test Execution Flow](#test-execution-flow)
8. [Key Design Patterns](#key-design-patterns)

---

## Overview

This is a comprehensive REST API testing framework for the QuickPoll application, built using Java 17, Rest Assured, JUnit 5, and Allure reporting. The framework follows SOLID principles and implements a layered architecture for maintainability, scalability, and reusability.

The framework tests a polling API that allows users to create polls, vote on polls, manage departments, and handle user authentication.

---

## Technology Stack

### Core Dependencies
- **Java 17**: Programming language
- **Maven**: Build and dependency management
- **Rest Assured 5.3.2**: HTTP client for API testing
- **JUnit 5.10.1**: Test execution framework
- **Allure 2.24.0**: Test reporting and evidence capture
- **SLF4J 2.0.9 + Logback 1.4.11**: Logging framework
- **Jakarta Validation API 3.0.2**: Bean validation

### Build Plugins
- **Maven Compiler Plugin**: Java compilation
- **Maven Surefire Plugin**: Test execution
- **Maven Clean Plugin**: Cleanup old test results
- **Allure Maven Plugin**: Report generation

---

## Project Structure

```
quickpoll-api-tests/
├── pom.xml                          # Maven configuration
├── README.md                        # User documentation
├── PROJECT_ARCHITECTURE.md          # This file
├── docker-compose.yml               # Docker setup for API
├── Dockerfile                       # Container configuration
│
└── src/test/
    ├── java/com/amalitech/qa/
    │   ├── base/                    # Base test infrastructure
    │   │   └── BaseTest.java           # Abstract base class for all tests
    │   │
    │   ├── client/                  # HTTP client and authentication
    │   │   ├── ApiClient.java       # Rest Assured wrapper
    │   │   └── AuthenticationHandler.java  # Token management
    │   │
    │   ├── config/                  # Configuration management
    │   │   ├── ConfigurationManager.java   # Singleton config loader
    │   │   └── ConfigurationException.java # Config error handling
    │   │
    │   ├── exceptions/              # Custom exceptions
    │   │   ├── AuthenticationException.java
    │   │   └── TestSetupException.java
    │   │
    │   ├── models/                  # Data models
    │   │   ├── request/             # Request DTOs
    │   │   │   ├── CreatePollRequest.java
    │   │   │   ├── LoginRequest.java
    │   │   │   ├── RegisterRequest.java
    │   │   │   ├── VoteRequest.java
    │   │   │   ├── CreateDepartmentRequest.java
    │   │   │   ├── UpdateUserRequest.java
    │   │   │   └── AddEmailsRequest.java
    │   │   └── TestUser.java        # Test user model
    │   │
    │   ├── services/                # Business logic services
    │   │   ├── TestDataManager.java         # Test data lifecycle
    │   │   ├── UserRegistrationService.java # User management
    │   │   └── PerformanceMonitor.java      # Response time tracking
    │   │
    │   ├── utils/                   # Utility classes
    │   │   ├── TestHelper.java      # Common assertions
    │   │   ├── SchemaValidator.java # JSON schema validation
    │   │   └── SecurityTestHelper.java # Security test utilities
    │   │
    │   └── tests/                   # Test implementations
    │       ├── functional/          # CRUD and business logic tests
    │       │   ├── PollCrudTests.java
    │       │   ├── AuthenticationTests.java
    │       │   ├── UserManagementTests.java
    │       │   └── DepartmentTests.java
    │       │
    │       ├── contract/            # API contract tests
    │       │   └── SchemaValidationTests.java
    │       │
    │       ├── security/            # Security tests
    │       │   ├── AuthorizationTests.java
    │       │   └── InjectionTests.java
    │       │
    │       ├── performance/         # Performance tests
    │       │   └── ResponseTimeTests.java
    │       │
    │       └── negative/            # Error handling tests
    │           ├── InvalidInputTests.java
    │           └── BoundaryTests.java
    │
    └── resources/
        ├── config/                  # Environment configurations
        │   ├── dev.properties       # Development settings
        │   └── prod.properties      # Production settings
        │
        ├── schemas/                 # JSON schemas for validation
        │   ├── poll-response-schema.json
        │   ├── vote-response-schema.json
        │   ├── error-response-schema.json
        │   └── paginated-poll-response-schema.json
        │
        └── allure.properties        # Allure configuration
```

---

## Architecture Layers

The framework follows a **4-layer architecture** that separates concerns and promotes reusability:

```
┌─────────────────────────────────────────────────────────────┐
│                      TEST LAYER                              │
│  (Functional, Contract, Security, Performance, Negative)     │
│  - PollCrudTests, AuthenticationTests, etc.                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER                             │
│  - TestDataManager: Test data lifecycle                      │
│  - UserRegistrationService: User management                  │
│  - PerformanceMonitor: Response time tracking                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    SUPPORT LAYER                             │
│  - ApiClient: HTTP operations                                │
│  - AuthenticationHandler: Token management                   │
│  - ConfigurationManager: Environment config                  │
│  - TestHelper: Common assertions                             │
│  - SchemaValidator: Contract validation                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                 INFRASTRUCTURE LAYER                         │
│  - Rest Assured: HTTP client                                 │
│  - JUnit 5: Test execution                                   │
│  - Allure: Reporting                                         │
│  - Maven: Build system                                       │
└─────────────────────────────────────────────────────────────┘
```

### Layer 1: Test Layer
**Purpose**: Contains actual test implementations organized by test type

**Components**:
- `functional/`: CRUD operations, business logic validation
- `contract/`: API schema validation
- `security/`: Authentication, authorization, injection tests
- `performance/`: Response time validation
- `negative/`: Error handling, boundary conditions

**Responsibilities**:
- Define test scenarios
- Arrange test data
- Execute API calls via services
- Assert expected outcomes
- Use Allure annotations for reporting

---

### Layer 2: Service Layer
**Purpose**: Provides reusable business logic for test operations

**Components**:

1. **TestDataManager**
   - Creates test data (polls, users, departments)
   - Tracks created resources
   - Automatic cleanup after tests
   - Prevents test data pollution

2. **UserRegistrationService**
   - Registers test users with unique credentials
   - Manages user lifecycle
   - Stores credentials for authentication
   - Cleanup of test users

3. **PerformanceMonitor**
   - Records response times
   - Validates against thresholds
   - Calculates averages
   - Attaches metrics to Allure reports

**Responsibilities**:
- Encapsulate complex test operations
- Manage test data lifecycle
- Provide high-level APIs for tests
- Handle resource cleanup

---

### Layer 3: Support Layer
**Purpose**: Provides foundational utilities and infrastructure

**Components**:

1. **ApiClient**
   - Wraps Rest Assured
   - Provides HTTP methods (GET, POST, PUT, PATCH, DELETE)
   - Automatic authentication header injection
   - Allure evidence capture
   - Request/response logging

2. **AuthenticationHandler**
   - Manages authentication tokens
   - Handles login API calls
   - Token expiration tracking
   - Provides auth headers

3. **ConfigurationManager**
   - Singleton pattern
   - Loads environment-specific properties
   - Supports dev, staging, prod environments
   - Thread-safe initialization

4. **TestHelper**
   - Common assertion methods
   - Status code validation
   - Response field validation
   - Error message validation
   - Collection size assertions

5. **SchemaValidator**
   - JSON schema validation
   - Contract testing support
   - Schema file management

**Responsibilities**:
- Provide reusable utilities
- Handle cross-cutting concerns
- Manage configuration
- Simplify common operations

---

### Layer 4: Infrastructure Layer
**Purpose**: External frameworks and tools

**Components**:
- **Rest Assured**: HTTP client with fluent API
- **JUnit 5**: Test lifecycle, annotations, assertions
- **Allure**: Test reporting, evidence capture
- **Maven**: Dependency management, build lifecycle
- **SLF4J + Logback**: Logging infrastructure

---

## Component Flow

### 1. Test Initialization Flow

```
Maven Execution
    ↓
JUnit 5 Test Runner
    ↓
@BeforeAll (BaseTest.setupSuite)
    ↓
ConfigurationManager.getInstance()
    ├── Load environment (dev/staging/prod)
    ├── Read properties file
    └── Validate required properties
    ↓
RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
    ↓
@BeforeEach (BaseTest.setupTest)
    ↓
Initialize Components:
    ├── AuthenticationHandler (token management)
    ├── ApiClient (HTTP operations)
    ├── TestDataManager (data lifecycle)
    ├── UserRegistrationService (user management)
    └── PerformanceMonitor (metrics tracking)
    ↓
Test Execution
```

### 2. API Request Flow

```
Test Method
    ↓
apiClient.post("/api/polls", pollData)
    ↓
ApiClient.given()
    ├── Apply base RequestSpecification
    │   ├── Set base URI
    │   ├── Set content type (JSON)
    │   ├── Add Allure filter
    │   └── Enable logging
    ├── Check AuthenticationHandler for token
    └── Inject Authorization header if token exists
    ↓
Rest Assured HTTP Request
    ├── Log request details
    ├── Send HTTP request
    └── Capture request in Allure
    ↓
API Server (QuickPoll)
    ↓
Rest Assured HTTP Response
    ├── Log response details
    ├── Apply ResponseSpecification
    └── Capture response in Allure
    ↓
Extract Response object
    ↓
Return to Test Method
    ↓
TestHelper.assertStatusCode(response, 201)
```

### 3. Authentication Flow

```
Test needs authentication
    ↓
UserRegistrationService.registerTestUser()
    ├── Generate unique email (timestamp-based)
    ├── Create RegisterRequest
    ├── POST /api/auth/register
    ├── Extract token from response
    ├── Create TestUser object
    └── Track for cleanup
    ↓
AuthenticationHandler.authenticateWithTestUser(testUser)
    ├── Extract credentials from TestUser
    ├── Create LoginRequest
    ├── POST /api/auth/login
    ├── Extract token from response
    ├── Store token in AuthenticationHandler
    └── Set token expiration (1 hour)
    ↓
Subsequent API calls
    ↓
ApiClient.given()
    ├── Check AuthenticationHandler.getAuthToken()
    ├── Get auth headers (Bearer token)
    └── Inject into request
    ↓
Authenticated API Request
```

### 4. Test Data Management Flow

```
Test creates data
    ↓
testDataManager.createTestPollWithDefaults()
    ├── Build poll data (question, options, etc.)
    ├── POST /api/polls
    ├── Extract poll ID from response
    └── trackResource("poll", pollId)
        └── Add to createdResourceIds list
    ↓
Test executes operations
    ↓
@AfterEach (BaseTest.teardownTest)
    ↓
testDataManager.cleanupTestData()
    ├── Iterate through createdResourceIds
    ├── For each resource:
    │   ├── Determine resource type
    │   ├── Get cleanup endpoint
    │   ├── DELETE /api/{resource-type}/{id}
    │   └── Log cleanup result
    ├── Clear createdResourceIds list
    └── Clear resourceTypeMap
    ↓
userRegistrationService.cleanupTestUsers()
    ├── Iterate through createdUsers
    ├── For each user:
    │   ├── DELETE /api/users/{userId}
    │   └── Log cleanup result
    └── Clear createdUsers list
    ↓
authHandler.clearToken()
```

### 5. Configuration Loading Flow

```
ConfigurationManager.getInstance()
    ↓
Check if instance exists (Double-checked locking)
    ↓
Create new instance (if null)
    ↓
Constructor
    ↓
Get environment from system property
    └── System.getProperty("env", "dev")
    ↓
loadConfiguration(environment)
    ↓
Build config file path
    └── "config/{env}.properties"
    ↓
Load from classpath
    └── getClass().getClassLoader().getResourceAsStream()
    ↓
Parse properties file
    ↓
validateRequiredProperties()
    ├── Check base.url
    ├── Check connection.timeout
    └── Check response.timeout
    ↓
Configuration ready
    ↓
Tests access via:
    ├── config.getBaseUrl()
    ├── config.getPerformanceThreshold("get")
    └── config.getProperty("custom.key")
```

---

## File Connections

### How Files Work Together

#### 1. BaseTest.java (Foundation)
**Connects to**:
- `ConfigurationManager`: Loads environment settings
- `ApiClient`: Creates HTTP client instance
- `AuthenticationHandler`: Manages authentication
- `TestDataManager`: Handles test data lifecycle
- `UserRegistrationService`: Manages test users
- `PerformanceMonitor`: Tracks response times

**Used by**: All test classes (PollCrudTests, AuthenticationTests, etc.)

**Purpose**: Provides common setup/teardown and shared components

---

#### 2. ApiClient.java (HTTP Operations)
**Connects to**:
- `AuthenticationHandler`: Gets auth token and headers
- `Rest Assured`: Wraps HTTP operations
- `Allure`: Captures request/response evidence
- `SLF4J Logger`: Logs HTTP operations

**Used by**:
- All test classes (via BaseTest)
- `TestDataManager`: Creates test data
- `UserRegistrationService`: Registers users
- `AuthenticationHandler`: Login API calls

**Purpose**: Centralized HTTP client with automatic auth injection

---

#### 3. AuthenticationHandler.java (Token Management)
**Connects to**:
- `Rest Assured`: Makes login API calls
- `LoginRequest`: Request model
- `TestUser`: User credentials
- `SLF4J Logger`: Logs auth operations

**Used by**:
- `ApiClient`: Gets auth headers
- `BaseTest`: Initializes and clears tokens
- Test classes: Authenticates users

**Purpose**: Manages authentication tokens and login flow

---

#### 4. ConfigurationManager.java (Settings)
**Connects to**:
- Properties files: `config/dev.properties`, `config/prod.properties`
- `SLF4J Logger`: Logs configuration loading

**Used by**:
- `BaseTest`: Gets base URL and settings
- `PerformanceMonitor`: Gets performance thresholds
- Any component needing environment-specific config

**Purpose**: Singleton configuration provider

---

#### 5. TestDataManager.java (Data Lifecycle)
**Connects to**:
- `ApiClient`: Creates and deletes resources
- `SLF4J Logger`: Logs data operations

**Used by**:
- All test classes: Creates test data
- `BaseTest`: Cleanup in @AfterEach

**Purpose**: Creates, tracks, and cleans up test data

---

#### 6. UserRegistrationService.java (User Management)
**Connects to**:
- `ApiClient`: Registers and deletes users
- `RegisterRequest`: Registration model
- `TestUser`: User data model
- `SLF4J Logger`: Logs user operations

**Used by**:
- Test classes: Creates test users
- `BaseTest`: Cleanup in @AfterEach

**Purpose**: Manages test user lifecycle

---

#### 7. TestHelper.java (Assertions)
**Connects to**:
- `JUnit 5 Assertions`: Delegates assertions
- `Rest Assured Response`: Validates responses
- `Allure @Step`: Annotates assertion steps
- `SLF4J Logger`: Logs assertions

**Used by**: All test classes for common assertions

**Purpose**: Reusable assertion methods

---

#### 8. Test Classes (e.g., PollCrudTests.java)
**Extends**: `BaseTest`

**Uses**:
- `apiClient`: HTTP operations
- `testDataManager`: Test data creation
- `TestHelper`: Assertions
- `Allure annotations`: Reporting
- `JUnit 5 annotations`: Test lifecycle

**Purpose**: Implements actual test scenarios

---

## Test Execution Flow

### Complete Test Lifecycle

```
1. Maven Command
   └── mvn clean test -Pdev

2. Maven Surefire Plugin
   └── Discovers test classes (*Test.java, *Tests.java)

3. JUnit 5 Test Engine
   └── Loads test classes

4. @BeforeAll (Once per test class)
   ├── ConfigurationManager.getInstance()
   ├── Load environment configuration
   └── Configure Rest Assured defaults

5. @BeforeEach (Before each test method)
   ├── Create AuthenticationHandler
   ├── Create ApiClient
   ├── Create TestDataManager
   ├── Create UserRegistrationService
   └── Create PerformanceMonitor

6. @Test Method Execution
   ├── Arrange: Setup test data
   │   └── testDataManager.createTestPollWithDefaults()
   ├── Act: Execute API call
   │   └── apiClient.post("/api/polls", data)
   ├── Assert: Validate response
   │   └── TestHelper.assertStatusCode(response, 201)
   └── Allure: Capture evidence

7. @AfterEach (After each test method)
   ├── testDataManager.cleanupTestData()
   ├── userRegistrationService.cleanupTestUsers()
   └── authHandler.clearToken()

8. @AfterAll (Once per test class)
   └── Final cleanup (if needed)

9. Allure Results
   └── Write to target/allure-results/

10. Maven Allure Plugin
    └── mvn allure:report
    └── Generate HTML report
```

---

## Key Design Patterns

### 1. Singleton Pattern
**Where**: `ConfigurationManager`

**Why**: Ensures single configuration instance across all tests

**Implementation**:
```java
private static volatile ConfigurationManager instance;

public static ConfigurationManager getInstance() {
    if (instance == null) {
        synchronized (ConfigurationManager.class) {
            if (instance == null) {
                instance = new ConfigurationManager();
            }
        }
    }
    return instance;
}
```

---

### 2. Builder Pattern
**Where**: Rest Assured `RequestSpecBuilder`, `ResponseSpecBuilder`

**Why**: Fluent API for building complex request/response specifications

**Implementation**:
```java
this.baseRequestSpec = new RequestSpecBuilder()
    .setBaseUri(baseUrl)
    .setContentType(ContentType.JSON)
    .setAccept(ContentType.JSON)
    .addFilter(new AllureRestAssured())
    .log(LogDetail.ALL)
    .build();
```

---

### 3. Template Method Pattern
**Where**: `BaseTest` abstract class

**Why**: Defines test lifecycle skeleton, subclasses implement specific tests

**Implementation**:
```java
public abstract class BaseTest {
    @BeforeAll
    public static void setupSuite() { /* common setup */ }
    
    @BeforeEach
    public void setupTest() { /* common setup */ }
    
    // Subclasses implement @Test methods
    
    @AfterEach
    public void teardownTest() { /* common cleanup */ }
}
```

---

### 4. Facade Pattern
**Where**: `ApiClient`, `TestHelper`

**Why**: Simplifies complex Rest Assured API into simple methods

**Implementation**:
```java
public Response get(String endpoint) {
    return given()
        .when()
        .get(endpoint)
        .then()
        .spec(baseResponseSpec)
        .extract()
        .response();
}
```

---

### 5. Strategy Pattern
**Where**: Environment configuration (dev, staging, prod)

**Why**: Different configurations for different environments

**Implementation**:
```java
// Maven profiles select strategy
mvn test -Pdev      // Uses dev.properties
mvn test -Pstaging  // Uses staging.properties
mvn test -Pprod     // Uses prod.properties
```

---

### 6. Dependency Injection
**Where**: Constructor injection throughout

**Why**: Loose coupling, easier testing, better maintainability

**Implementation**:
```java
public ApiClient(String baseUrl, AuthenticationHandler authHandler) {
    this.authHandler = authHandler;
    // ...
}
```

---

## Data Flow Example: Creating a Poll

Let's trace a complete flow from test to API and back:

```
1. Test Method (PollCrudTests.java)
   testCreatePoll() {
       Map<String, Object> pollData = new HashMap<>();
       pollData.put("question", "What is your favorite framework?");
       pollData.put("options", Arrays.asList("JUnit", "TestNG"));
       
       Response response = apiClient.post("/api/polls", pollData);
   }

2. ApiClient.post()
   ├── Calls given() to get RequestSpecification
   ├── Checks AuthenticationHandler for token
   ├── Injects Authorization header if token exists
   ├── Sets request body (pollData)
   └── Executes POST request

3. Rest Assured
   ├── Applies RequestSpecification (base URI, content type, etc.)
   ├── Adds Allure filter (captures request)
   ├── Logs request details
   ├── Sends HTTP POST to http://localhost:8080/api/polls
   └── Receives HTTP response

4. Allure Filter
   ├── Captures request details (headers, body, URL)
   └── Captures response details (status, headers, body)

5. Response Extraction
   ├── Applies ResponseSpecification (logging)
   ├── Logs response details
   └── Returns Response object

6. Back to Test Method
   TestHelper.assertStatusCode(response, 201);
   ├── Extracts status code from response
   ├── Compares with expected (201)
   └── Throws AssertionError if mismatch

7. Test Data Tracking
   String pollId = response.jsonPath().getString("id");
   testDataManager.trackResource("poll", pollId);
   ├── Adds pollId to createdResourceIds list
   └── Maps pollId to resource type "poll"

8. Test Completes
   @AfterEach triggers cleanup
   ├── testDataManager.cleanupTestData()
   │   └── DELETE /api/polls/{pollId}
   └── Test data removed from API

9. Allure Report
   ├── Test result written to target/allure-results/
   ├── Includes request/response evidence
   ├── Includes logs and attachments
   └── mvn allure:report generates HTML
```

---

## Environment Configuration

### Configuration Files

**Location**: `src/test/resources/config/`

**Files**:
- `dev.properties`: Development environment
- `prod.properties`: Production environment

**Example** (`dev.properties`):
```properties
# API Configuration
base.url=http://localhost:8080

# Timeouts
connection.timeout=10000
response.timeout=30000

# Performance Thresholds (milliseconds)
performance.threshold.get=2000
performance.threshold.post=3000
performance.threshold.put=3000
performance.threshold.delete=2000
performance.threshold.list=5000
```

### Switching Environments

**Via Maven Profile**:
```bash
mvn test -Pdev      # Development (default)
mvn test -Pprod     # Production
```

**Via System Property**:
```bash
mvn test -Denv=dev
```

**In Code**:
```java
ConfigurationManager config = ConfigurationManager.getInstance();
String baseUrl = config.getBaseUrl();
int threshold = config.getPerformanceThreshold("get");
```

---

## Logging

### Configuration

**Framework**: SLF4J + Logback

**Configuration File**: `src/test/resources/logback.xml` (if present)

### Usage in Code

```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);

logger.info("Creating test poll");
logger.debug("Poll ID: {}", pollId);
logger.warn("Cleanup failed for resource: {}", resourceId);
logger.error("Authentication failed", exception);
```

### Log Levels
- **ERROR**: Critical failures
- **WARN**: Recoverable issues (e.g., cleanup failures)
- **INFO**: Important operations (e.g., test setup, API calls)
- **DEBUG**: Detailed information (e.g., token injection, resource tracking)

---

## Allure Reporting

### Annotations

```java
@Epic("QuickPoll API")              // High-level feature group
@Feature("Poll Management")          // Specific feature
@Story("Create Poll")                // User story
@Severity(SeverityLevel.CRITICAL)    // Test importance
@DisplayName("Create a new poll")    // Human-readable name
@Description("Verify poll creation") // Detailed description
@Step("Assert status code is 201")   // Step in test flow
```

### Evidence Capture

**Automatic**:
- HTTP requests (headers, body, URL)
- HTTP responses (status, headers, body)
- Request/response timing

**Manual**:
```java
Allure.addAttachment("Poll Data", pollData.toString());
performanceMonitor.attachResponseTimeToReport(response);
```

### Report Generation

```bash
# Generate report
mvn allure:report

# Generate and open in browser
mvn allure:serve
```

**Report Location**: `target/site/allure-maven-plugin/`

---

## Test Categories (Tags)

Tests are organized using JUnit 5 `@Tag` annotations:

```java
@Tag("functional")   // CRUD operations
@Tag("contract")     // Schema validation
@Tag("security")     // Auth/authz tests
@Tag("performance")  // Response time tests
@Tag("negative")     // Error handling
@Tag("smoke")        // Critical tests
```

### Running Specific Tags

```bash
# Run only functional tests
mvn test -Dgroups=functional

# Run security tests
mvn test -Dgroups=security

# Exclude slow tests
mvn test -DexcludedGroups=slow
```

---

## Best Practices Implemented

### 1. Test Isolation
- Each test creates its own data
- Automatic cleanup prevents pollution
- No dependencies between tests

### 2. DRY (Don't Repeat Yourself)
- Common operations in BaseTest
- Reusable assertions in TestHelper
- Shared services (TestDataManager, etc.)

### 3. SOLID Principles
- **Single Responsibility**: Each class has one purpose
- **Open/Closed**: Extend BaseTest, don't modify
- **Liskov Substitution**: Test classes interchangeable
- **Interface Segregation**: Focused interfaces
- **Dependency Inversion**: Depend on abstractions

### 4. Separation of Concerns
- Tests focus on scenarios
- Services handle business logic
- Utilities provide helpers
- Configuration isolated

### 5. Fail-Safe Cleanup
- Cleanup in @AfterEach always runs
- Try-catch prevents cleanup failures from breaking tests
- Logs cleanup issues without throwing

### 6. Comprehensive Logging
- All operations logged
- Different log levels for different severity
- Helps debugging test failures

### 7. Evidence Capture
- Allure automatically captures requests/responses
- Performance metrics attached
- Screenshots/attachments when needed

---

## Troubleshooting Guide

### Common Issues

**1. Tests fail with connection timeout**
- Check API is running: `docker-compose up`
- Verify base URL in `config/dev.properties`
- Check network connectivity

**2. Authentication failures**
- Verify API authentication endpoint is working
- Check token format in response
- Review AuthenticationHandler logs

**3. Test data not cleaned up**
- Check @AfterEach is executing
- Review cleanup logs for errors
- Verify DELETE endpoints work

**4. Allure report not generating**
- Ensure tests ran: `mvn clean test`
- Check `target/allure-results/` exists
- Run: `mvn allure:serve`

**5. Configuration not loading**
- Verify properties file exists in `src/test/resources/config/`
- Check file name matches environment
- Review ConfigurationManager logs

---

## Summary

This framework provides a robust, maintainable structure for API testing with:

- **Layered architecture** for separation of concerns
- **Automatic test data management** for isolation
- **Comprehensive reporting** with Allure
- **Multi-environment support** via configuration
- **Reusable components** following SOLID principles
- **Fail-safe cleanup** to prevent pollution
- **Performance monitoring** built-in
- **Security testing** capabilities

The flow from test to API and back is streamlined through well-defined components that work together seamlessly, making it easy to write, maintain, and debug tests.
