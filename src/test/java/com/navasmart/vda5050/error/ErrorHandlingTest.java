package com.navasmart.vda5050.error;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Vda5050ErrorFactory} and {@link ErrorAggregator}.
 *
 * <p>Plain JUnit 5 tests with no Spring context needed.</p>
 */
class ErrorHandlingTest {

    private Vda5050ErrorFactory errorFactory;
    private ErrorAggregator errorAggregator;
    private VehicleContext ctx;

    @BeforeEach
    void setUp() {
        errorFactory = new Vda5050ErrorFactory();
        errorAggregator = new ErrorAggregator(errorFactory);
        ctx = new VehicleContext("TestMfg", "AGV001");
    }

    // --- Vda5050ErrorFactory tests ---

    @Test
    void createWarningHasCorrectLevelDescriptionAndType() {
        Error warning = errorFactory.createWarning("Low battery", "battery.low");

        assertThat(warning.getErrorLevel()).isEqualTo(ErrorLevel.WARNING.getValue());
        assertThat(warning.getErrorDescription()).isEqualTo("Low battery");
        assertThat(warning.getErrorType()).isEqualTo("battery.low");
        assertThat(warning.getErrorReferences()).isEmpty();
    }

    @Test
    void createFatalWithReferencesHasCorrectLevelAndMappedReferences() {
        Map<String, String> refs = Map.of(
                "orderId", "order-001",
                "nodeId", "node-5"
        );
        Error fatal = errorFactory.createFatal("Navigation failed", "nav.failed", refs);

        assertThat(fatal.getErrorLevel()).isEqualTo(ErrorLevel.FATAL.getValue());
        assertThat(fatal.getErrorDescription()).isEqualTo("Navigation failed");
        assertThat(fatal.getErrorType()).isEqualTo("nav.failed");
        assertThat(fatal.getErrorReferences()).hasSize(2);
        assertThat(fatal.getErrorReferences())
                .anyMatch(r -> "orderId".equals(r.getReferenceKey())
                        && "order-001".equals(r.getReferenceValue()));
        assertThat(fatal.getErrorReferences())
                .anyMatch(r -> "nodeId".equals(r.getReferenceKey())
                        && "node-5".equals(r.getReferenceValue()));
    }

    @Test
    void createFatalWithNullReferencesHasNoReferences() {
        Error fatal = errorFactory.createFatal("Critical error", "critical", null);

        assertThat(fatal.getErrorLevel()).isEqualTo(ErrorLevel.FATAL.getValue());
        assertThat(fatal.getErrorReferences()).isEmpty();
    }

    @Test
    void createErrorWithExplicitLevel() {
        Error error = errorFactory.createError(
                ErrorLevel.WARNING, "Sensor degraded", "sensor.degraded", null);

        assertThat(error.getErrorLevel()).isEqualTo("WARNING");
        assertThat(error.getErrorDescription()).isEqualTo("Sensor degraded");
        assertThat(error.getErrorType()).isEqualTo("sensor.degraded");
    }

    // --- ErrorAggregator tests ---

    @Test
    void addWarningAddsToVehicleContext() {
        assertThat(ctx.getAgvState().getErrors()).isEmpty();

        errorAggregator.addWarning(ctx, "Obstacle detected", "obstacle.warn", null);

        assertThat(ctx.getAgvState().getErrors()).hasSize(1);
        assertThat(ctx.getAgvState().getErrors().get(0).getErrorLevel()).isEqualTo("WARNING");
        assertThat(ctx.getAgvState().getErrors().get(0).getErrorDescription()).isEqualTo("Obstacle detected");
    }

    @Test
    void addFatalErrorAddsToVehicleContext() {
        errorAggregator.addFatalError(ctx, "Motor failure", "motor.fail");

        assertThat(ctx.getAgvState().getErrors()).hasSize(1);
        assertThat(ctx.getAgvState().getErrors().get(0).getErrorLevel()).isEqualTo("FATAL");
        assertThat(ctx.getAgvState().getErrors().get(0).getErrorType()).isEqualTo("motor.fail");
    }

    @Test
    void clearAllErrorsRemovesAllErrors() {
        errorAggregator.addWarning(ctx, "Warning 1", "warn.1", null);
        errorAggregator.addFatalError(ctx, "Fatal 1", "fatal.1");
        assertThat(ctx.getAgvState().getErrors()).hasSize(2);

        errorAggregator.clearAllErrors(ctx);

        assertThat(ctx.getAgvState().getErrors()).isEmpty();
    }

    @Test
    void hasFatalErrorReturnsTrueWhenFatalExists() {
        assertThat(errorAggregator.hasFatalError(ctx)).isFalse();

        errorAggregator.addWarning(ctx, "Just a warning", "warn.1", null);
        assertThat(errorAggregator.hasFatalError(ctx)).isFalse();

        errorAggregator.addFatalError(ctx, "Fatal error", "fatal.1");
        assertThat(errorAggregator.hasFatalError(ctx)).isTrue();
    }

    @Test
    void mergeExternalErrorsAddsToExistingErrors() {
        errorAggregator.addWarning(ctx, "Existing warning", "warn.1", null);

        Error externalError = new Error();
        externalError.setErrorLevel("WARNING");
        externalError.setErrorType("fms.error");
        externalError.setErrorDescription("FMS reported issue");

        errorAggregator.mergeExternalErrors(ctx, List.of(externalError));

        assertThat(ctx.getAgvState().getErrors()).hasSize(2);
        assertThat(ctx.getAgvState().getErrors().get(1).getErrorType()).isEqualTo("fms.error");
    }

    @Test
    void mergeExternalErrorsWithNullDoesNothing() {
        errorAggregator.addWarning(ctx, "Existing warning", "warn.1", null);

        errorAggregator.mergeExternalErrors(ctx, null);

        assertThat(ctx.getAgvState().getErrors()).hasSize(1);
    }

    @Test
    void addErrorDirectlyAddsPrebuiltError() {
        Error error = errorFactory.createWarning("Pre-built", "pre.built");
        errorAggregator.addError(ctx, error);

        assertThat(ctx.getAgvState().getErrors()).hasSize(1);
        assertThat(ctx.getAgvState().getErrors().get(0).getErrorType()).isEqualTo("pre.built");
    }
}
