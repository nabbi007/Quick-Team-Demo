package com.amalitech.qa.services;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitors and validates API performance metrics, particularly response times.
 * Tracks response times for historical analysis.
 * 
 * @author QuickPoll API Testing Framework
 * @version 1.0.0
 */
public class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    private final Map<String, List<Long>> responseTimeHistory;
    
    /**
     * Constructs a new PerformanceMonitor.
     */
    public PerformanceMonitor() {
        this.responseTimeHistory = new HashMap<>();
    }
    
    /**
     * Records a response time for historical tracking.
     * 
     * @param endpoint the endpoint identifier
     * @param response the API response
     */
    public void recordResponseTime(String endpoint, Response response) {
        long responseTime = response.getTime();
        
        responseTimeHistory.computeIfAbsent(endpoint, k -> new ArrayList<>()).add(responseTime);
        logger.debug("Response time recorded for {}: {} ms", endpoint, responseTime);
    }
}
