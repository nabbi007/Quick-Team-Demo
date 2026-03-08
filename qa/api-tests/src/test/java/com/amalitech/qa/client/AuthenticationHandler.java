package com.amalitech.qa.client;

import com.amalitech.qa.exceptions.AuthenticationException;
import com.amalitech.qa.models.TestUser;
import com.amalitech.qa.models.request.LoginRequest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles authentication token management for API testing.
 * Manages authentication tokens and authorization headers.
 * Makes actual API calls for authentication instead of using hardcoded credentials.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class AuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationHandler.class);
    
    private String currentToken;
    private LocalDateTime tokenExpiration;
    private String baseUrl;
    
    /**
     * Constructs a new AuthenticationHandler.
     */
    public AuthenticationHandler() {
        // Base URL will be set when needed
    }
    
    /**
     * Sets the base URL for authentication API calls.
     * 
     * @param baseUrl the base URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    /**
     * Authenticates with the API using provided credentials.
     * Makes an actual API call to the login endpoint.
     * 
     * @param email User email
     * @param password User password
     * @return the authentication token
     * @throws AuthenticationException if authentication fails
     */
    public String authenticate(String email, String password) {
        logger.info("Authenticating user: {}", email);
        
        if (baseUrl == null) {
            throw new AuthenticationException("Base URL not set for authentication");
        }
        
        try {
            LoginRequest loginRequest = new LoginRequest(email, password);
            
            Response response = RestAssured.given()
                    .baseUri(baseUrl)
                    .contentType("application/json")
                    .body(loginRequest)
                    .when()
                    .post("/api/auth/login")
                    .then()
                    .extract()
                    .response();
            
            if (response.getStatusCode() == 401) {
                throw new AuthenticationException(
                    String.format("Authentication failed for user %s. Response: %s", 
                        email, response.getBody().asString())
                );
            }
            
            if (response.getStatusCode() != 200) {
                throw new AuthenticationException(
                    String.format("Unexpected status %d during authentication. Response: %s", 
                        response.getStatusCode(), response.getBody().asString())
                );
            }
            
            String token = response.jsonPath().getString("token");
            if (token == null || token.isEmpty()) {
                throw new AuthenticationException(
                    "Authentication response did not contain a token"
                );
            }
            
            setAuthToken(token);
            logger.info("Authentication successful for user: {}", email);
            return token;
            
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Exception during authentication for user: {}", email, e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticates using a TestUser object.
     * Convenience method that extracts credentials from TestUser.
     * 
     * @param testUser TestUser with credentials
     * @return the authentication token
     * @throws AuthenticationException if authentication fails
     */
    public String authenticateWithTestUser(TestUser testUser) {
        if (testUser == null) {
            throw new AuthenticationException("TestUser cannot be null");
        }
        
        logger.info("Authenticating with TestUser: {}", testUser.getEmail());
        return authenticate(testUser.getEmail(), testUser.getPassword());
    }
    
    /**
     * Gets the current authentication token.
     * 
     * @return the current token, or null if not authenticated
     */
    public String getAuthToken() {
        return currentToken;
    }
    
    /**
     * Sets the authentication token and updates expiration time.
     * 
     * @param token the authentication token
     */
    public void setAuthToken(String token) {
        this.currentToken = token;
        // Set token expiration to 1 hour from now
        this.tokenExpiration = LocalDateTime.now().plusHours(1);
        logger.debug("Auth token set with expiration: {}", tokenExpiration);
    }
    
    /**
     * Validates if the current token is still valid.
     * 
     * @return true if token is valid, false otherwise
     */
    public boolean isTokenValid() {
        if (currentToken == null || tokenExpiration == null) {
            return false;
        }
        
        boolean valid = LocalDateTime.now().isBefore(tokenExpiration);
        if (!valid) {
            logger.warn("Authentication token has expired");
        }
        return valid;
    }
    
    /**
     * Clears the current authentication token.
     */
    public void clearToken() {
        logger.debug("Clearing authentication token");
        this.currentToken = null;
        this.tokenExpiration = null;
    }
    
    /**
     * Gets authentication headers for API requests.
     * 
     * @return a map containing authentication headers
     */
    public Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        
        if (currentToken != null) {
            headers.put("Authorization", "Bearer " + currentToken);
        }
        
        return headers;
    }
}
