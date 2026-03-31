package com.navasmart.vda5050.server.callback;

import com.navasmart.vda5050.model.ActionState;
import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.Factsheet;

import java.util.List;

public interface Vda5050ServerAdapter {

    void onStateUpdate(String vehicleId, AgvState state);

    default void onNodeReached(String vehicleId, String nodeId, int sequenceId) {}

    default void onActionStateChanged(String vehicleId, ActionState actionState) {}

    default void onOrderCompleted(String vehicleId, String orderId) {}

    default void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {}

    default void onConnectionStateChanged(String vehicleId, String connectionState) {}

    default void onVehicleTimeout(String vehicleId, String lastSeenTimestamp) {}

    default void onFactsheetReceived(String vehicleId, Factsheet factsheet) {}

    default void onErrorReported(String vehicleId, Error error) {}

    default void onErrorCleared(String vehicleId, Error error) {}
}
