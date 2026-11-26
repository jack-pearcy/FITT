package com.fitt.workouts.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.Instant;

// A time a device reports for something that already happened: not before the
// year 2000, and at most 24 hours after the server's clock. The day of slack
// keeps a phone with a wrong clock working, and the lower bound keeps a nonsense
// year out of the DATETIME column. Null passes, the fields are optional.
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WithinTimeBounds.Validator.class)
public @interface WithinTimeBounds {

    String message() default "Time must be after 2000 and at most 24 hours ahead";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WithinTimeBounds, Instant> {

        static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
        static final Duration AHEAD = Duration.ofHours(24);

        @Override
        public boolean isValid(Instant value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            Instant now = context.getClockProvider().getClock().instant();
            return !value.isBefore(EARLIEST) && !value.isAfter(now.plus(AHEAD));
        }
    }
}
