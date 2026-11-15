package com.fitt.shared.config;

import com.fitt.identity.service.exception.EmailAlreadyExistsException;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import com.fitt.workouts.service.exception.WorkoutSessionNotFoundException;
import com.fitt.workouts.service.exception.ExerciseInUseException;
import com.fitt.measurements.service.exception.BodyWeightEntryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import com.fitt.identity.service.exception.GoalNotFoundException;
import com.fitt.workouts.service.exception.ExerciseEntryNotFoundException;
import com.fitt.workouts.service.exception.SetEntryNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

// Extends ResponseEntityExceptionHandler so framework exceptions (404 for an unknown
// path, 405, 415, a missing request parameter, a path variable of the wrong type,
// and so on) keep their proper 4xx status and RFC 7807 body. Without it the
// catch-all Exception handler below would swallow them and answer 500.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(WorkoutSessionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleWorkoutSessionNotFound(WorkoutSessionNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Workout Session Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(WorkoutPlanNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleWorkoutPlanNotFound(WorkoutPlanNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Workout Plan Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ExerciseNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleExerciseNotFound(ExerciseNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Exercise Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(ExerciseEntryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleExerciseEntryNotFound(ExerciseEntryNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Exercise Entry Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(SetEntryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleSetEntryNotFound(SetEntryNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Set Entry Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(GoalNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleGoalNotFound(GoalNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Goal Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // 409 because the exercise is valid and owned, but a plan or logged session
    // references it, so deleting it would break those rows.
    @ExceptionHandler(ExerciseInUseException.class)
    public ResponseEntity<ProblemDetail> handleExerciseInUse(ExerciseInUseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Exercise In Use");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(BodyWeightEntryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBodyWeightEntryNotFound(
            BodyWeightEntryNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(BiometricsNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBiometricsNotFound(
            BiometricsNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Biometrics Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // Covers bad input from workouts domain: deleting a fixed exercise, bad entry IDs, etc.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail("Malformed request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad Request");
        problem.setDetail("Validation failed");

        // One message per field. A field with several violations keeps the first one
        // seen rather than the last, so the response is stable between runs.
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // 409 because the request is valid but conflicts with existing state.
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // Safety net for races the service-level checks cannot close, such as two
    // simultaneous first-time writes that both pass an exists check and then hit a
    // unique constraint. A clean retryable 409 beats a 500. The detail is generic on
    // purpose, the driver message names tables and constraints.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with existing data, please retry");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Bad Credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    // Re-thrown so ExceptionTranslationFilter routes it to RestAccessDeniedHandler.
    // Without this the catch-all Exception handler swallows it and returns 500.
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    // Safety net. Logs the full trace so we can track down anything unexpected.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        problem.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}