package com.fitt.coach.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds "fitt.coach.*". The API key comes from the environment only and is blank
// by default, which leaves the coach switched off.
@ConfigurationProperties(prefix = "fitt.coach")
public class CoachProperties {

    private String apiKey = "";

    private String model = "claude-opus-5-5";

    // Upper bound on one answer, thinking included. A cost cap: a review is a
    // few hundred words.
    private long maxTokens = 8000;

    // How long one request to the model may take before the user gets an error.
    private long timeoutSeconds = 60;

    // Reviews one user may ask for inside the window. Every review costs money,
    // so this bounds what a single account can spend.
    private int dailyLimit = 5;

    // How many of the most recent finished sessions are sent.
    private int sessionsToReview = 8;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    public int getSessionsToReview() { return sessionsToReview; }
    public void setSessionsToReview(int sessionsToReview) { this.sessionsToReview = sessionsToReview; }
}
