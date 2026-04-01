package com.navasmart.vda5050.vehicle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleContextTest {

    private VehicleContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new VehicleContext("TestMfg", "SN001");
    }

    @Test
    void tryLock_acquiresLock_whenAvailable() throws InterruptedException {
        boolean acquired = ctx.tryLock(1, TimeUnit.SECONDS);
        try {
            assertThat(acquired).isTrue();
        } finally {
            if (acquired) {
                ctx.unlock();
            }
        }
    }

    @Test
    void tryLock_timesOut_whenLockHeld() throws InterruptedException {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch testDone = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            ctx.lock();
            try {
                lockHeld.countDown();
                testDone.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ctx.unlock();
            }
        });
        holder.start();

        lockHeld.await(2, TimeUnit.SECONDS);

        boolean acquired = ctx.tryLock(100, TimeUnit.MILLISECONDS);
        assertThat(acquired).isFalse();

        testDone.countDown();
        holder.join(2000);
    }

    @Test
    void getActionStartTimes_returnsUnmodifiableMap() {
        ctx.putActionStartTime("a1", 1000L);
        Map<String, Long> times = ctx.getActionStartTimes();

        assertThatThrownBy(() -> times.put("a2", 2000L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void putActionStartTime_addsEntry() {
        ctx.putActionStartTime("action-1", 12345L);

        assertThat(ctx.getActionStartTimes().get("action-1")).isEqualTo(12345L);
    }

    @Test
    void removeActionStartTime_removesEntry() {
        ctx.putActionStartTime("action-1", 12345L);
        ctx.removeActionStartTime("action-1");

        assertThat(ctx.getActionStartTimes()).doesNotContainKey("action-1");
    }

    @Test
    void clearActionStartTimes_clearsAll() {
        ctx.putActionStartTime("a1", 100L);
        ctx.putActionStartTime("a2", 200L);
        ctx.putActionStartTime("a3", 300L);

        ctx.clearActionStartTimes();

        assertThat(ctx.getActionStartTimes()).isEmpty();
    }
}
