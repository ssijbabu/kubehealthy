package com.kuberhealthy.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents the result of a health check execution
 */
public class CheckResult {
    
    @JsonProperty("checkName")
    private String checkName;
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("ok")
    private boolean ok;
    
    @JsonProperty("errors")
    private List<String> errors;
    
    @JsonProperty("runDuration")
    private long runDurationMillis;
    
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp;

    public CheckResult() {
        this.uuid = UUID.randomUUID().toString();
        this.errors = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public CheckResult(String checkName, boolean ok) {
        this();
        this.checkName = checkName;
        this.ok = ok;
    }

    public String getCheckName() {
        return checkName;
    }

    public void setCheckName(String checkName) {
        this.checkName = checkName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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

    public long getRunDurationMillis() {
        return runDurationMillis;
    }

    public void setRunDurationMillis(long runDurationMillis) {
        this.runDurationMillis = runDurationMillis;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "CheckResult{" +
                "checkName='" + checkName + '\'' +
                ", uuid='" + uuid + '\'' +
                ", ok=" + ok +
                ", errors=" + errors +
                ", runDuration=" + runDurationMillis + "ms" +
                ", timestamp=" + timestamp +
                '}';
    }
}