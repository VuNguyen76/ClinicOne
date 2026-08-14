package com.clinicone.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates an optional Vietnamese phone; combine with {@code @NotBlank} when required. */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "^(?:|0\\d{9})$")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface VietnamesePhone {
    String message() default "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
