package com.amalitech.qa.client;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * API client for making HTTP requests using Rest Assured with automatic authentication,
 * logging, and Allure evidence capture.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class ApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);
    
    private final RequestSpecification baseRequestSpec;
    private final ResponseSpecification baseResponseSpec;
    private final AuthenticationHandler authHandler;
    
    /**
     * Constructs a new ApiClient with the specified base URL and authentication handler.
     * 
     * @param baseUrl the base URL for API requests
     * @param authHandler the authentication handler for managing tokens
     */
    public ApiClient(String baseUrl, AuthenticationHandler authHandler) {
        this.authHandler = authHandler;
        
        // Set base URL in auth handler for authentication calls
        if (authHandler != null) {
            authHandler.setBaseUrl(baseUrl);
        }
        
        // Initialize base request specification
        this.baseRequestSpec = new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured())  // Automatic Allure evidence capture
                .log(LogDetail.ALL)  // Log all request details
                .build();
        
        // Initialize base response specification
        this.baseResponseSpec = new ResponseSpecBuilder()
                .log(LogDetail.ALL)  // Log all response details
                .build();
        
        logger.info("ApiClient initialized with base URL: {}", baseUrl);
    }
    
    /**
     * Provides access to Rest Assured's fluent API with base configuration applied.
     * Automatically injects authentication headers if available.
     * 
     * @return a RequestSpecification with base configuration
     */
    public RequestSpecification given() {
        RequestSpecification spec = RestAssured.given().spec(baseRequestSpec);
        
        // Inject authentication headers if token is available
        if (authHandler != null && authHandler.getAuthToken() != null) {
            Map<String, String> authHeaders = authHandler.getAuthHeaders();
            spec.headers(authHeaders);
            logger.debug("Authentication headers injected");
        }
        
        return spec;
    }
    
    /**
     * Performs a GET request to the specified endpoint.
     * 
     * @param endpoint the API endpoint path
     * @return the Response object
     */
    public Response get(String endpoint) {
        logger.info("GET request to endpoint: {}", endpoint);
        return given()
                .when()
                .get(endpoint)
                .then()
                .spec(baseResponseSpec)
                .extract()
                .response();
    }
    
    /**
     * Performs a POST request with a request body.
     * 
     * @param endpoint the API endpoint path
     * @param requestBody the request body object
     * @return the Response object
     */
    public Response post(String endpoint, Object requestBody) {
        logger.info("POST request to endpoint: {}", endpoint);
        return given()
                .body(requestBody)
                .when()
                .post(endpoint)
                .then()
                .spec(baseResponseSpec)
                .extract()
                .response();
    }
    
    /**
     * Performs a PUT request with a request body.
     * 
     * @param endpoint the API endpoint path
     * @param requestBody the request body object
     * @return the Response object
     */
    public Response put(String endpoint, Object requestBody) {
        logger.info("PUT request to endpoint: {}", endpoint);
        return given()
                .body(requestBody)
                .when()
                .put(endpoint)
                .then()
                .spec(baseResponseSpec)
                .extract()
                .response();
    }
    
    /**
     * Performs a PATCH request with a request body.
     * 
     * @param endpoint the API endpoint path
     * @param requestBody the request body object
     * @return the Response object
     */
    public Response patch(String endpoint, Object requestBody) {
        logger.info("PATCH request to endpoint: {}", endpoint);
        return given()
                .body(requestBody)
                .when()
                .patch(endpoint)
                .then()
                .spec(baseResponseSpec)
                .extract()
                .response();
    }
    
    /**
     * Performs a DELETE request to the specified endpoint.
     * 
     * @param endpoint the API endpoint path
     * @return the Response object
     */
    public Response delete(String endpoint) {
        logger.info("DELETE request to endpoint: {}", endpoint);
        return given()
                .when()
                .delete(endpoint)
                .then()
                .spec(baseResponseSpec)
                .extract()
                .response();
    }
    
}
