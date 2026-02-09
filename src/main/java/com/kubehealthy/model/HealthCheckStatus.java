package com.kuberhealthy.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the status of a health check
 */
public class HealthCheckStatus {
    
    @JsonProperty("state")
    private CheckState state;
    
    @JsonProperty("ok")
    private boolean ok;
    
    @JsonProperty("errors")
    private List<String> errors;
    
    @JsonProperty("lastRun")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastRun;
    
    @JsonProperty("lastSuccess")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastSuccess;
    
    @JsonProperty("consecutiveFailures")
    private int consecutiveFailures;
    
    @JsonProperty("currentCheckUUID")
    private String currentCheckUUID;

    public HealthCheckStatus() {
        this.state = CheckState.NEW;
        this.ok = false;
        this.errors = new ArrayList<>();
        this.consecutiveFailures = 0;
    }

    public CheckState getState() {
        return state;
    }

    public void setState(CheckState state) {
        this.state = state;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public Instant getLastRun() {
        return lastRun;
    }

    public void setLastRun(Instant lastRun) {
        this.lastRun = lastRun;
    }

    public Instant getLastSuccess() {
        return lastSuccess;
    }

    public void setLastSuccess(Instant lastSuccess) {
        this.lastSuccess = lastSuccess;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public String getCurrentCheckUUID() {
        return currentCheckUUID;
    }

    public void setCurrentCheckUUID(String currentCheckUUID) {
        this.currentCheckUUID = currentCheckUUID;
    }

    @Override
    public String toString() {
        return "HealthCheckStatus{" +
                "state=" + state +
                ", ok=" + ok +
                ", errors=" + errors +
                ", lastRun=" + lastRun +
                ", consecutiveFailures=" + consecutiveFailures +
                '}';
    }

    /**
     * Possible states for a health check
     */
    public enum CheckState {
        NEW,
        RUNNING,
        COMPLETED,
        FAILED,
        TIMEOUT
    }
}