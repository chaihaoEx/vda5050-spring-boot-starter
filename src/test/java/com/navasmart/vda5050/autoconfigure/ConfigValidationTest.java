package com.navasmart.vda5050.autoconfigure;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for validation constraints on {@link Vda5050Properties} and its nested config classes.
 *
 * <p>Uses a Jakarta Bean Validation {@link Validator} directly, avoiding the need
 * for a Spring context or a running MQTT broker.</p>
 */
class ConfigValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void defaultPropertiesAreValid() {
        Vda5050Properties props = new Vda5050Properties();
        Set<ConstraintViolation<Vda5050Properties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }

    @Test
    void portZeroFailsValidation() {
        Vda5050Properties props = new Vda5050Properties();
        props.getMqtt().setPort(0);
        Set<ConstraintViolation<Vda5050Properties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("mqtt.port"));
    }

    @Test
    void portExceedsMaxFailsValidation() {
        Vda5050Properties props = new Vda5050Properties();
        props.getMqtt().setPort(70000);
        Set<ConstraintViolation<Vda5050Properties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("mqtt.port"));
    }

    @Test
    void blankHostFailsValidation() {
        Vda5050Properties props = new Vda5050Properties();
        props.getMqtt().setHost("   ");
        Set<ConstraintViolation<Vda5050Properties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("mqtt.host"));
    }

    @Test
    void negativeHeartbeatIntervalFailsValidation() {
        Vda5050Properties props = new Vda5050Properties();
        props.getProxy().setHeartbeatIntervalMs(-1);
        Set<ConstraintViolation<Vda5050Properties>> violations = validator.validate(props);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("proxy.heartbeatIntervalMs"));
    }
}
