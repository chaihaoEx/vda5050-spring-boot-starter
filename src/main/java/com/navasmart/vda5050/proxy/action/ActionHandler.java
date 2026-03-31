package com.navasmart.vda5050.proxy.action;

import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.proxy.callback.ActionResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ActionHandler {

    Set<String> getSupportedActionTypes();

    CompletableFuture<ActionResult> execute(String vehicleId, Action action);

    default void cancel(String vehicleId, String actionId) {}

    default void pause(String vehicleId, String actionId) {}

    default void resume(String vehicleId, String actionId) {}

    default boolean canBePaused() { return false; }
}
