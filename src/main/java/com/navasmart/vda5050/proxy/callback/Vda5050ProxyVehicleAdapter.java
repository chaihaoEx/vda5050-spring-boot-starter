package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Vda5050ProxyVehicleAdapter {

    CompletableFuture<NavigationResult> onNavigate(String vehicleId, Node targetNode,
                                                    List<Node> waypoints, List<Edge> edges);

    void onNavigationCancel(String vehicleId);

    CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action);

    void onActionCancel(String vehicleId, String actionId);

    default void onActionPause(String vehicleId, String actionId) {}

    default void onActionResume(String vehicleId, String actionId) {}

    void onPause(String vehicleId);

    void onResume(String vehicleId);

    void onOrderCancel(String vehicleId);

    default void onTeleopStart(String vehicleId) {}

    default void onTeleopStop(String vehicleId) {}
}
