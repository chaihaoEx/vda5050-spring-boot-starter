package com.navasmart.vda5050.proxy.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);

    private final Map<String, ActionHandler> handlerMap = new HashMap<>();

    @Autowired(required = false)
    public void registerHandlers(List<ActionHandler> handlers) {
        if (handlers == null) return;
        for (ActionHandler handler : handlers) {
            for (String actionType : handler.getSupportedActionTypes()) {
                handlerMap.put(actionType, handler);
                log.info("Registered action handler for type '{}': {}", actionType,
                        handler.getClass().getSimpleName());
            }
        }
    }

    public Optional<ActionHandler> getHandler(String actionType) {
        return Optional.ofNullable(handlerMap.get(actionType));
    }
}
