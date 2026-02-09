package com.kuberhealthy.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kuberhealthy.controller.HealthCheckController;
import com.kuberhealthy.model.HealthCheck;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP server for exposing health check status and metrics
 */
public class HealthCheckHttpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckHttpServer.class);
    private static final int DEFAULT_PORT = 8080;
    
    private final HealthCheckController controller;
    private final ObjectMapper objectMapper;
    private final int port;
    private Undertow server;
    
    public HealthCheckHttpServer(HealthCheckController controller) {
        this(controller, DEFAULT_PORT);
    }
    
    public HealthCheckHttpServer(HealthCheckController controller, int port) {
        this.controller = controller;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Start the HTTP server
     */
    public void start() {
        server = Undertow.builder()
            .addHttpListener(port, "0.0.0.0")
            .setHandler(this::handleRequest)
            .build();
        
        server.start();
        logger.info("HTTP server started on port {}", port);
    }
    
    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (server != null) {
            server.stop();
            logger.info("HTTP server stopped");
        }
    }
    
    private void handleRequest(HttpServerExchange exchange) {
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();
        
        logger.debug("Received {} request for {}", method, path);
        
        try {
            if ("GET".equals(method)) {
                switch (path) {
                    case "/healthz":
                    case "/health":
                        handleHealthEndpoint(exchange);
                        break;
                    case "/ready":
                    case "/readyz":
                        handleReadinessEndpoint(exchange);
                        break;
                    case "/metrics":
                        handleMetricsEndpoint(exchange);
                        break;
                    case "/status":
                        handleStatusEndpoint(exchange);
                        break;
                    case "/checks":
                        handleChecksEndpoint(exchange);
                        break;
                    default:
                        sendNotFound(exchange);
                }
            } else {
                sendMethodNotAllowed(exchange);
            }
        } catch (Exception e) {
            logger.error("Error handling request", e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }
    
    private void handleHealthEndpoint(HttpServerExchange exchange) throws Exception {
        boolean healthy = controller.isHealthy();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", healthy ? "healthy" : "unhealthy");
        response.put("timestamp", System.currentTimeMillis());
        
        if (healthy) {
            sendJsonResponse(exchange, StatusCodes.OK, response);
        } else {
            sendJsonResponse(exchange, StatusCodes.SERVICE_UNAVAILABLE, response);
        }
    }
    
    private void handleReadinessEndpoint(HttpServerExchange exchange) throws Exception {
        // Server is ready if it's running
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ready");
        response.put("timestamp", System.currentTimeMillis());
        
        sendJsonResponse(exchange, StatusCodes.OK, response);
    }
    
    private void handleMetricsEndpoint(HttpServerExchange exchange) throws Exception {
        List<HealthCheck> checks = controller.getAllHealthChecks();
        
        StringBuilder metrics = new StringBuilder();
        metrics.append("# HELP kuberhealthy_check_total Total number of health checks\n");
        metrics.append("# TYPE kuberhealthy_check_total gauge\n");
        metrics.append("kuberhealthy_check_total ").append(checks.size()).append("\n");
        
        metrics.append("# HELP kuberhealthy_check_ok Health check OK status (1=ok, 0=failed)\n");
        metrics.append("# TYPE kuberhealthy_check_ok gauge\n");
        
        for (HealthCheck check : checks) {
            String checkName = check.getName();
            int okValue = check.getStatus().isOk() ? 1 : 0;
            metrics.append("kuberhealthy_check_ok{check=\"").append(checkName).append("\"} ")
                   .append(okValue).append("\n");
        }
        
        metrics.append("# HELP kuberhealthy_check_failures Consecutive failures for health check\n");
        metrics.append("# TYPE kuberhealthy_check_failures gauge\n");
        
        for (HealthCheck check : checks) {
            String checkName = check.getName();
            int failures = check.getStatus().getConsecutiveFailures();
            metrics.append("kuberhealthy_check_failures{check=\"").append(checkName).append("\"} ")
                   .append(failures).append("\n");
        }
        
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseSender().send(metrics.toString());
    }
    
    private void handleStatusEndpoint(HttpServerExchange exchange) throws Exception {
        List<HealthCheck> checks = controller.getAllHealthChecks();
        
        Map<String, Object> response = new HashMap<>();
        response.put("healthy", controller.isHealthy());
        response.put("totalChecks", checks.size());
        response.put("failingChecks", controller.getFailingChecksCount());
        response.put("timestamp", System.currentTimeMillis());
        
        sendJsonResponse(exchange, StatusCodes.OK, response);
    }
    
    private void handleChecksEndpoint(HttpServerExchange exchange) throws Exception {
        List<HealthCheck> checks = controller.getAllHealthChecks();
        
        Map<String, Object> response = new HashMap<>();
        response.put("checks", checks);
        response.put("count", checks.size());
        
        sendJsonResponse(exchange, StatusCodes.OK, response);
    }
    
    private void sendJsonResponse(HttpServerExchange exchange, int statusCode, Object data) throws Exception {
        String json = objectMapper.writeValueAsString(data);
        
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.setStatusCode(statusCode);
        exchange.getResponseSender().send(json);
    }
    
    private void sendNotFound(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.NOT_FOUND);
        exchange.getResponseSender().send("Not Found");
    }
    
    private void sendMethodNotAllowed(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        exchange.getResponseSender().send("Method Not Allowed");
    }
    
    private void sendError(HttpServerExchange exchange, int statusCode, String message) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseSender().send(message);
    }
}