package com.navasmart.vda5050.test;

import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.Factsheet;
import com.navasmart.vda5050.server.callback.Vda5050ServerAdapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test double for {@link Vda5050ServerAdapter} that records every callback
 * invocation in thread-safe collections.  Provides {@code CountDownLatch}-based
 * await methods so integration tests can block until an expected event arrives.
 */
public class MockServerAdapter implements Vda5050ServerAdapter {

    // ---- recorded callbacks ----

    public final CopyOnWriteArrayList<AgvState> stateUpdates = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> nodesReached = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> completedOrders = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> failedOrders = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<Error> reportedErrors = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<Error> clearedErrors = new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<String> connectionStates = new CopyOnWriteArrayList<>();

    // ---- latches ----

    private volatile CountDownLatch stateUpdateLatch = new CountDownLatch(1);
    private volatile CountDownLatch orderCompletedLatch = new CountDownLatch(1);
    private volatile CountDownLatch nodeReachedLatch = new CountDownLatch(1);

    // ---- Vda5050ServerAdapter ----

    @Override
    public void onStateUpdate(String vehicleId, AgvState state) {
        stateUpdates.add(state);
        stateUpdateLatch.countDown();
    }

    @Override
    public void onNodeReached(String vehicleId, String nodeId, int sequenceId) {
        nodesReached.add(nodeId);
        nodeReachedLatch.countDown();
    }

    @Override
    public void onOrderCompleted(String vehicleId, String orderId) {
        completedOrders.add(orderId);
        orderCompletedLatch.countDown();
    }

    @Override
    public void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {
        failedOrders.add(orderId);
    }

    @Override
    public void onConnectionStateChanged(String vehicleId, String connectionState) {
        connectionStates.add(connectionState);
    }

    @Override
    public void onErrorReported(String vehicleId, Error error) {
        reportedErrors.add(error);
    }

    @Override
    public void onErrorCleared(String vehicleId, Error error) {
        clearedErrors.add(error);
    }

    // ---- await helpers ----

    /**
     * Blocks until the specified number of state updates have been recorded,
     * or the timeout elapses.
     *
     * @param count   the number of state updates to wait for
     * @param timeout maximum time to wait
     * @param unit    time unit of the timeout argument
     * @return {@code true} if the required count was reached before the timeout
     */
    public boolean awaitStateUpdate(int count, long timeout, TimeUnit unit) throws InterruptedException {
        stateUpdateLatch = new CountDownLatch(count);
        return stateUpdateLatch.await(timeout, unit);
    }

    /**
     * Blocks until at least one order-completed callback has been recorded,
     * or the timeout elapses.
     *
     * @return {@code true} if the latch counted down before the timeout
     */
    public boolean awaitOrderCompleted(long timeout, TimeUnit unit) throws InterruptedException {
        return orderCompletedLatch.await(timeout, unit);
    }

    /**
     * Blocks until at least one node-reached callback has been recorded,
     * or the timeout elapses.
     *
     * @return {@code true} if the latch counted down before the timeout
     */
    public boolean awaitNodeReached(long timeout, TimeUnit unit) throws InterruptedException {
        return nodeReachedLatch.await(timeout, unit);
    }

    // ---- reset ----

    /**
     * Clears all recorded state and resets latches so the mock can be reused.
     */
    public void reset() {
        stateUpdates.clear();
        nodesReached.clear();
        completedOrders.clear();
        failedOrders.clear();
        reportedErrors.clear();
        clearedErrors.clear();
        connectionStates.clear();
        stateUpdateLatch = new CountDownLatch(1);
        orderCompletedLatch = new CountDownLatch(1);
        nodeReachedLatch = new CountDownLatch(1);
    }
}
