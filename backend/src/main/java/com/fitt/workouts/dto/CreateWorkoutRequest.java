package com.fitt.workouts.dto;

public class CreateWorkoutRequest {

    private String name;

    protected CreateWorkoutRequest() {
        // Jackson needs this
    }

    public CreateWorkoutRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
