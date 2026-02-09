package com.kuberhealthy.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a health check configuration and state
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealthCheck {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("namespace")
    private String namespace;
    
    @JsonProperty("runInterval")
    private long runIntervalSeconds;
    
    @JsonProperty("timeout")
    private long timeoutSeconds;
    
    @JsonProperty("podSpec")
    private PodSpec podSpec;
    
    @JsonProperty("status")
    private HealthCheckStatus status;
    
    public HealthCheck() {
        this.status = new HealthCheckStatus();
    }
    
    public HealthCheck(String name, String namespace, long runIntervalSeconds, long timeoutSeconds) {
        this.name = name;
        this.namespace = namespace;
        this.runIntervalSeconds = runIntervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.status = new HealthCheckStatus();
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public long getRunIntervalSeconds() {
        return runIntervalSeconds;
    }

    public void setRunIntervalSeconds(long runIntervalSeconds) {
        this.runIntervalSeconds = runIntervalSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public PodSpec getPodSpec() {
        return podSpec;
    }

    public void setPodSpec(PodSpec podSpec) {
        this.podSpec = podSpec;
    }

    public HealthCheckStatus getStatus() {
        return status;
    }

    public void setStatus(HealthCheckStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "HealthCheck{" +
                "name='" + name + '\'' +
                ", namespace='" + namespace + '\'' +
                ", runInterval=" + runIntervalSeconds +
                ", timeout=" + timeoutSeconds +
                ", status=" + status +
                '}';
    }

    /**
     * Pod specification for the health check
     */
    public static class PodSpec {
        @JsonProperty("image")
        private String image;
        
        @JsonProperty("command")
        private List<String> command;
        
        @JsonProperty("args")
        private List<String> args;

        public PodSpec() {
            this.command = new ArrayList<>();
            this.args = new ArrayList<>();
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public List<String> getCommand() {
            return command;
        }

        public void setCommand(List<String> command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }
    }
}