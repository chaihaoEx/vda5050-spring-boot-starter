package com.navasmart.vda5050.error;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ErrorAggregator {

    private final Vda5050ErrorFactory errorFactory;

    public ErrorAggregator(Vda5050ErrorFactory errorFactory) {
        this.errorFactory = errorFactory;
    }

    public void addError(VehicleContext ctx, Error error) {
        ctx.getAgvState().getErrors().add(error);
    }

    public void addWarning(VehicleContext ctx, String description, String errorType,
                           Map<String, String> references) {
        Error error = errorFactory.createError(ErrorLevel.WARNING, description, errorType, references);
        ctx.getAgvState().getErrors().add(error);
    }

    public void addFatalError(VehicleContext ctx, String description, String errorType) {
        Error error = errorFactory.createFatal(description, errorType, null);
        ctx.getAgvState().getErrors().add(error);
    }

    public void clearAllErrors(VehicleContext ctx) {
        ctx.getAgvState().getErrors().clear();
    }

    public boolean hasFatalError(VehicleContext ctx) {
        return ctx.getAgvState().getErrors().stream()
                .anyMatch(e -> ErrorLevel.FATAL.getValue().equals(e.getErrorLevel()));
    }

    public void mergeExternalErrors(VehicleContext ctx, List<Error> fmsErrors) {
        if (fmsErrors != null) {
            ctx.getAgvState().getErrors().addAll(fmsErrors);
        }
    }
}
