package com.navasmart.vda5050.test;

import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.proxy.callback.ActionResult;
import com.navasmart.vda5050.proxy.callback.NavigationResult;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyStateProvider;
import com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter;
import com.navasmart.vda5050.proxy.callback.VehicleStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test double that implements both {@link Vda5050ProxyVehicleAdapter} and
 * {@link Vda5050ProxyStateProvider}.  All callback invocations are recorded
 * in thread-safe collections so that test assertions can inspect them after
 * the fact.
 */
public class MockProxyAdapter implements Vda5050ProxyVehicleAdapter, Vda5050ProxyStateProvider {

    // ---- recorded calls ----

    public final CopyOnWriteArrayList<String> navigateCalls = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> actionExecuteCalls = new CopyOnWriteArrayList<>();
    public final AtomicBoolean pauseCalled = new AtomicBoolean(false);
    public final AtomicBoolean resumeCalled = new AtomicBoolean(false);
    public final AtomicBoolean orderCancelCalled = new AtomicBoolean(false);

    private volatile CountDownLatch navigateLatch = new CountDownLatch(1);

    // ---- Vda5050ProxyVehicleAdapter ----

    @Override
    public CompletableFuture<NavigationResult> onNavigate(String vehicleId, Node targetNode,
                                                          List<Node> waypoints, List<Edge> edges) {
        navigateCalls.add(vehicleId);
        navigateLatch.countDown();
        return CompletableFuture.completedFuture(NavigationResult.success());
    }

    @Override
    public void onNavigationCancel(String vehicleId) {
        // no-op for tests
    }

    @Override
    public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
        actionExecuteCalls.add(action.getActionId());
        return CompletableFuture.completedFuture(ActionResult.success());
    }

    @Override
    public void onActionCancel(String vehicleId, String actionId) {
        // no-op for tests
    }

    @Override
    public void onPause(String vehicleId) {
        pauseCalled.set(true);
    }

    @Override
    public void onResume(String vehicleId) {
        resumeCalled.set(true);
    }

    @Override
    public void onOrderCancel(String vehicleId) {
        orderCancelCalled.set(true);
    }

    // ---- Vda5050ProxyStateProvider ----

    @Override
    public VehicleStatus getVehicleStatus(String vehicleId) {
        VehicleStatus status = new VehicleStatus();
        status.setPositionInitialized(true);
        status.setX(1.0);
        status.setY(2.0);
        status.setTheta(0);
        status.setMapId("map1");
        status.setBatteryCharge(80.0);
        status.setEStop("NONE");
        return status;
    }

    @Override
    public Factsheet getFactsheet(String vehicleId) {
        Factsheet factsheet = new Factsheet();
        factsheet.setManufacturer("TestManufacturer");
        factsheet.setSerialNumber(vehicleId);
        factsheet.setVersion("2.0.0");
        return factsheet;
    }

    // ---- await helpers ----

    /**
     * Blocks until at least one {@code onNavigate} call has been recorded,
     * or the timeout elapses.
     *
     * @return {@code true} if the latch counted down before the timeout
     */
    public boolean awaitNavigate(long timeout, TimeUnit unit) throws InterruptedException {
        return navigateLatch.await(timeout, unit);
    }

    // ---- reset ----

    /**
     * Clears all recorded state so the mock can be reused across tests.
     */
    public void reset() {
        navigateCalls.clear();
        actionExecuteCalls.clear();
        pauseCalled.set(false);
        resumeCalled.set(false);
        orderCancelCalled.set(false);
        navigateLatch = new CountDownLatch(1);
    }
}
