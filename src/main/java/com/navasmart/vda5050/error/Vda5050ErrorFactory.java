package com.navasmart.vda5050.error;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.ErrorReference;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Vda5050ErrorFactory {

    public Error createError(ErrorLevel level, String description, String errorType,
                             Map<String, String> references) {
        Error error = new Error();
        error.setErrorLevel(level.getValue());
        error.setErrorDescription(description);
        error.setErrorType(errorType);
        if (references != null) {
            List<ErrorReference> refs = new ArrayList<>();
            references.forEach((k, v) -> refs.add(new ErrorReference(k, v)));
            error.setErrorReferences(refs);
        }
        return error;
    }

    public Error createWarning(String description, String errorType) {
        return createError(ErrorLevel.WARNING, description, errorType, null);
    }

    public Error createFatal(String description, String errorType,
                             Map<String, String> references) {
        return createError(ErrorLevel.FATAL, description, errorType, references);
    }
}
