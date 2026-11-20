package com.fitt.coach.service;

// The language model behind the coach, kept behind an interface so the service
// and its tests do not depend on a provider or on the network.
public interface CoachModel {

    // False when no API key is configured. The feature is then off.
    boolean isAvailable();

    // Sends the instructions and the training summary, returns the review text.
    // Throws CoachFailedException when the model gives no usable answer.
    String review(String instructions, String trainingSummary);
}
