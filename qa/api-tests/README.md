# QuickPoll API Testing Framework

A comprehensive, maintainable API testing framework for the QuickPoll API built with Rest Assured, JUnit 5, and Allure reporting.

## Features

- **Rest Assured Integration**: Leverages Rest Assured's powerful fluent API with RequestSpecification and ResponseSpecification
- **JUnit 5**: Modern test execution framework with lifecycle hooks and annotations
- **Allure Reporting**: Detailed test reports with automatic evidence capture
- **Property-Based Testing**: Uses jqwik for comprehensive property validation
- **Multi-Environment Support**: Easily switch between dev, staging, and production environments
- **Automatic Cleanup**: Test data is automatically cleaned up after test execution
- **Performance Monitoring**: Built-in response time tracking and threshold validation
- **Schema Validation**: JSON schema validation for contract testing
- **SOLID & DRY Principles**: Clean, maintainable code following best practices

## Architecture

The framework follows a layered architecture:

```
Test Layer (Functional, Contract, Security, Performance, Negative)
    ↓
Service Layer (API Client, Test Data Manager, Performance Monitor)
    ↓
Support Layer (Config Manager, Auth Handler, Test Helpers)
    ↓
Infrastructure Layer (Rest Assured, Allure, JUnit 5, Maven)
```

## Project Structure

```
src/test/java/com/amalitech/qa/
├── config/              # Configuration management
├── client/              # API client and authentication
├── models/              # Request and response models
│   ├── request/
│   └── response/
├── services/            # Test data and performance monitoring
├── utils/               # Test helpers and utilities
├── base/                # Base test class
└── tests/               # Test implementations
    ├── functional/
    ├── contract/
    ├── security/
    ├── performance/
    └── negative/

src/test/resources/
├── config/              # Environment-specific properties
├── schemas/             # JSON schemas for validation
├── testdata/            # Test data files
└── allure.properties    # Allure configuration
```

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- QuickPoll API running locally or accessible endpoint

## Setup

1. Clone the repository
2. Update the base URL in `src/test/resources/config/dev.properties` to point to your QuickPoll API
3. Install dependencies:
   ```bash
   mvn clean install
   ```

## Running Tests

### Run all tests (default dev environment)
```bash
mvn clean test
```

### Run tests for specific environment
```bash
mvn clean test -Pstaging
mvn clean test -Pprod
```

### Run specific test class
```bash
mvn test -Dtest=PollCrudTests
```

### Run tests with specific tag
```bash
mvn test -Dgroups=functional
mvn test -Dgroups=security
mvn test -Dgroups=smoke
```

### Run tests excluding a tag
```bash
mvn test -DexcludedGroups=slow
```

## Generating Reports

### Generate Allure report
```bash
mvn allure:report
```

### Generate and open Allure report
```bash
mvn allure:serve
```

### Full CI pipeline command
```bash
mvn clean test allure:report
```

## Configuration

### Environment Configuration

Configuration files are located in `src/test/resources/config/`:
- `dev.properties` - Development environment
- `staging.properties` - Staging environment
- `prod.properties` - Production environment

Each configuration file contains:
- Base URL
- Connection and response timeouts
- Authentication credentials
- Performance thresholds per endpoint type

### Switching Environments

Use Maven profiles to switch environments:
```bash
mvn test -Pdev      # Development (default)
mvn test -Pstaging  # Staging
mvn test -Pprod     # Production
```

Or set the environment via system property:
```bash
mvn test -Denv=staging
```

## Writing Tests

### Basic Test Structure

```java
@Epic("QuickPoll API")
@Feature("Poll Management")
@Tag("functional")
public class MyTests extends BaseTest {
    
    @Test
    @DisplayName("Test description")
    @Severity(SeverityLevel.CRITICAL)
    public void testSomething() {
        // Arrange
        String pollId = testDataManager.createTestPollWithDefaults();
        
        // Act
        Response response = apiClient.get("/polls/" + pollId);
        
        // Assert
        TestHelper.assertStatusCode(response, 200);
        TestHelper.assertResponseNotNull(response, "id");
    }
}
```

### Using the API Client

The ApiClient provides convenient methods for HTTP operations:

```java
// GET request
Response response = apiClient.get("/polls");
Response response = apiClient.get("/polls", queryParams);

// POST request
Response response = apiClient.post("/polls", requestBody);

// PUT request
Response response = apiClient.put("/polls/" + id, requestBody);

// PATCH request
Response response = apiClient.patch("/polls/" + id, requestBody);

// DELETE request
Response response = apiClient.delete("/polls/" + id);

// Direct access to Rest Assured's fluent API
Response response = apiClient.given()
    .queryParam("status", "active")
    .when()
    .get("/polls")
    .then()
    .statusCode(200)
    .extract()
    .response();
```

### Test Data Management

```java
// Create test poll with defaults
String pollId = testDataManager.createTestPollWithDefaults();

// Create test poll with custom data
Map<String, Object> pollData = new HashMap<>();
pollData.put("question", "My question?");
pollData.put("options", Arrays.asList("Option 1", "Option 2"));
String pollId = testDataManager.createTestPoll(pollData);

// Create prerequisite data for scenarios
testDataManager.createPrerequisiteData("voting");

// Load test data from file
Map<String, Object> data = testDataManager.loadTestDataFromFile("valid-polls.json");

// Cleanup is automatic via @AfterEach in BaseTest
```

### Performance Monitoring

```java
// Validate response time against threshold
performanceMonitor.validateResponseTime(response, 2000);

// Validate using configured threshold for endpoint type
performanceMonitor.validateResponseTime(response, "get");

// Attach response time to Allure report
performanceMonitor.attachResponseTimeToReport(response);

// Record and calculate average
performanceMonitor.recordResponseTime("/polls", response);
double avg = performanceMonitor.calculateAverageResponseTime("/polls");
```

### Schema Validation

```java
// Validate response against JSON schema
apiClient.given()
    .when()
    .get("/polls/" + pollId)
    .then()
    .assertThat()
    .body(matchesJsonSchemaInClasspath("schemas/poll-response-schema.json"));
```

### Authentication

```java
// Login as specific role
authHandler.loginAsUser("admin");
authHandler.loginAsUser("user");
authHandler.loginAsUser("guest");

// Manual authentication
authHandler.authenticate("username", "password");

// Check token validity
boolean valid = authHandler.isTokenValid();

// Clear authentication
authHandler.clearToken();
```

## Test Categories

Tests are organized by category using JUnit 5 tags:

- `@Tag("functional")` - Functional CRUD tests
- `@Tag("contract")` - Schema validation tests
- `@Tag("security")` - Authentication and authorization tests
- `@Tag("performance")` - Response time tests
- `@Tag("negative")` - Error handling tests
- `@Tag("smoke")` - Critical smoke tests
- `@Tag("property")` - Property-based tests

## Dependencies

- Rest Assured 5.3.2
- JUnit 5.10.1
- Allure 2.24.0
- jqwik 1.8.1 (property-based testing)
- Lombok 1.18.30
- SLF4J 2.0.9 + Logback 1.4.11
- WireMock 2.35.0 (for mocking)

## CI/CD Integration

The framework returns appropriate exit codes for CI/CD:
- Exit code 0: All tests passed
- Non-zero exit code: One or more tests failed

Example Jenkins/GitLab CI configuration:
```yaml
test:
  script:
    - mvn clean test
    - mvn allure:report
  artifacts:
    paths:
      - target/allure-results
      - target/surefire-reports
```

## Best Practices

1. **Extend BaseTest**: All test classes should extend BaseTest to inherit setup/teardown
2. **Use TestHelper**: Leverage TestHelper for common assertions
3. **Tag Your Tests**: Use @Tag annotations for test categorization
4. **Add Allure Annotations**: Use @Epic, @Feature, @Story, @Severity for better reporting
5. **Clean Test Data**: Let TestDataManager handle cleanup automatically
6. **Validate Performance**: Use PerformanceMonitor for response time checks
7. **Schema Validation**: Validate response structure with JSON schemas
8. **Descriptive Names**: Use @DisplayName for clear test descriptions

## Troubleshooting

### Tests fail with connection timeout
- Check that the QuickPoll API is running
- Verify the base URL in your environment configuration file
- Increase timeout values in the configuration if needed

### Allure report not generating
- Ensure allure-maven plugin is configured in pom.xml
- Check that test results are in target/allure-results
- Run `mvn allure:serve` to generate and open the report

### Authentication failures
- Verify credentials in the environment configuration file
- Check that the authentication endpoint is accessible
- Review logs for detailed error messages

## Contributing

1. Follow SOLID and DRY principles
2. Add Javadoc comments to all public classes and methods
3. Write tests for new functionality
4. Use meaningful variable and method names
5. Format code consistently

## License

Copyright © 2026 AmaliTech QA Team

## Contact

For questions or support, contact the QA team at qa@amalitech.com
