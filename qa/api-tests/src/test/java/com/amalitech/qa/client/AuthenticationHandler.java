package com.amalitech.qa.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles authentication token management and multi-role support for API testing.
 * Manages authentication tokens, credentials, and authorization headers.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class AuthenticationHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationHandler.class);
    
    private String currentToken;
    private LocalDateTime tokenExpiration;
    private final Map<String, UserCredentials> userCredentials;
    private String currentRole;
    
    /**
     * Constructs a new AuthenticationHandler with predefined user roles.
     */
    public AuthenticationHandler() {
        this.userCredentials = new HashMap<>();
        initializeUserCredentials();
    }
    
    /**
     * Initializes user credentials for different roles.
     */
    private void initializeUserCredentials() {
        // Admin role
        userCredentials.put("admin", new UserCredentials("admin_user", "admin_password"));
        
        // Regular user role
        userCredentials.put("user", new UserCredentials("regular_user", "user_password"));
        
        // Guest role
        userCredentials.put("guest", new UserCredentials("guest_user", "guest_password"));
    }
    
    /**
     * Authenticates with the provided username and password.
     * In a real implementation, this would call the authentication endpoint.
     * 
     * @param username the username
     * @param password the password
     * @return the authentication token
     */
    public String authenticate(String username, String password) {
        logger.info("Authenticating user: {}", username);
        
        // In a real implementation, this would make an API call to get the token
        // For now, we'll generate a mock token
        String token = generateMockToken(username);
        setAuthToken(token);
        
        logger.info("Authentication successful for user: {}", username);
        return token;
    }
    
    /**
     * Generates a mock authentication token for testing purposes.
     * 
     * @param username the username
     * @return a mock token
     */
    private String generateMockToken(String username) {
        return String.format("Bearer_mock_token_%s_%d", username, System.currentTimeMillis());
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
        this.currentRole = null;
    }
    
    /**
     * Gets authentication headers for API requests.
     * 
     * @return a map containing authentication headers
     */
    public Map<String, String> getAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        
        if (currentToken != null) {
            headers.put("Authorization", currentToken);
        }
        
        return headers;
    }
    
    /**
     * Logs in as a specific user role and authenticates.
     * 
     * @param role the user role (admin, user, guest)
     * @throws IllegalArgumentException if role is not recognized
     */
    public void loginAsUser(String role) {
        UserCredentials credentials = userCredentials.get(role.toLowerCase());
        
        if (credentials == null) {
            throw new IllegalArgumentException(
                String.format("Unknown user role: %s. Available roles: admin, user, guest", role)
            );
        }
        
        logger.info("Logging in as role: {}", role);
        authenticate(credentials.getUsername(), credentials.getPassword());
        this.currentRole = role;
    }
    
    /**
     * Gets the current user role.
     * 
     * @return the current role, or null if not logged in
     */
    public String getCurrentRole() {
        return currentRole;
    }
    
    /**
     * Gets user credentials for a specific role.
     * 
     * @param role the user role
     * @return the user credentials, or null if role not found
     */
    public UserCredentials getCredentialsForRole(String role) {
        return userCredentials.get(role.toLowerCase());
    }
    
    /**
     * Inner class to store user credentials.
     */
    public static class UserCredentials {
        private final String username;
        private final String password;
        
        public UserCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
    }
}
