package com.restorant.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

/** Bean Validation constraint for a Philippine mobile number in any spelling {@link PhoneNumbers} accepts. */
@Documented @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME) @Constraint(validatedBy = PhMobile.Validator.class)
public @interface PhMobile {
    String message() default PhoneNumbers.MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<PhMobile, String> {
        // Blank values are left to @NotBlank so each problem is reported once.
        @Override public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PhoneNumbers.isValid(value);
        }
    }
}
