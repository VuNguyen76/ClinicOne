package com.clinicone.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {})
@Size(max = IdempotencyKeys.MAX_LENGTH)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
        ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotencyKey {
    String message() default "Khóa chống trùng không được dài quá 80 ký tự";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
