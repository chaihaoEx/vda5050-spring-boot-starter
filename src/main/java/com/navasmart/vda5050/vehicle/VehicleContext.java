package com.navasmart.vda5050.vehicle;

import com.navasmart.vda5050.model.AgvState;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.proxy.statemachine.ProxyClientState;
import org.eclipse.paho.client.mqttv3.MqttClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单车状态上下文容器，持有一辆 AGV 在 Proxy 模式和 Server 模式下的全部运行时状态。
 *
 * <h2>线程安全</h2>
 * <p>本类<b>不是</b>天然线程安全的。对 Proxy/Server 状态字段的读写必须在持有 {@link #lock()} 的前提下进行。
 * 典型用法：</p>
 * <pre>
 *   ctx.lock();
 *   try {
 *       // 读写 agvState、currentOrder 等字段
 *   } finally {
 *       ctx.unlock();
 *   }
 * </pre>
 * <p>Header ID 生成器（{@code nextStateHeaderId()} 等）基于 {@link AtomicInteger}，可在无锁场景下安全调用。</p>
 *
 * <h2>两套状态</h2>
 * <ul>
 *   <li><b>Proxy 模式状态</b>：{@code agvState}、{@code currentOrder}、{@code clientState} 等 —— 本应用模拟 AGV 向主控上报</li>
 *   <li><b>Server 模式状态</b>：{@code lastReceivedState}、{@code lastSentOrder}、{@code connectionState} 等 —— 本应用作为主控接收 AGV 上报</li>
 * </ul>
 *
 * <p>同一辆车可以同时开启 Proxy 和 Server 模式（由 {@code proxyMode} / {@code serverMode} 标志位控制）。</p>
 */
public class VehicleContext {

    // ============ 身份标识（不可变） ============

    /** 制造商名称，创建后不可变 */
    private final String manufacturer;

    /** 车辆序列号，创建后不可变 */
    private final String serialNumber;

    /** 车辆唯一标识，格式为 {@code manufacturer:serialNumber}，创建后不可变 */
    private final String vehicleId;

    // ============ 锁 ============

    /** 可重入锁，保护 Proxy/Server 模式下的可变状态字段 */
    private final ReentrantLock stateLock = new ReentrantLock();

    // ============ 模式标志 ============

    /** 是否启用 Proxy（AGV 代理）模式 */
    private boolean proxyMode;

    /** 是否启用 Server（主控）模式 */
    private boolean serverMode;

    // ============ Proxy 模式状态（需持锁访问） ============

    /** 当前 AGV 上报状态，Proxy 模式下由本应用维护并周期发布 */
    private AgvState agvState = new AgvState();

    /** 当前正在执行的 Order，为 null 表示无订单 */
    private Order currentOrder;

    /** Proxy 客户端状态机的当前状态 */
    private ProxyClientState clientState = ProxyClientState.IDLE;

    /** 当前正在驶向/已到达的节点在 Order 节点列表中的索引 */
    private int currentNodeIndex;

    /** 下一个需要停靠的节点索引 */
    private int nextStopIndex;

    /** 是否已到达当前航点 */
    private boolean reachedWaypoint;

    /** 当前导航开始时间（epoch 毫秒），0 表示未导航或导航已完成 */
    private long navigationStartTime;

    /** actionId → 动作开始时间（epoch 毫秒），需持锁访问 */
    private final Map<String, Long> actionStartTimes = new HashMap<>();

    /**
     * Proxy 模式专属 MQTT 客户端（每辆 Proxy 车辆独立），支持独立 LWT。
     * 非 Proxy 模式车辆此字段为 null。
     */
    private MqttClient proxyMqttClient;

    // ============ Server 模式状态（需持锁访问） ============

    /** 最后一次从 AGV 收到的状态消息 */
    private AgvState lastReceivedState;

    /** 最后一次下发给 AGV 的订单 */
    private Order lastSentOrder;

    /** AGV 连接状态，取值为 "ONLINE"、"OFFLINE" 或 "CONNECTIONBROKEN" */
    private String connectionState = "OFFLINE";

    /** 最后一次收到 AGV 消息的时间戳（epoch 毫秒） */
    private long lastSeenTimestamp;

    // ============ Header ID 生成器（AtomicInteger，无需持锁） ============

    /** state 消息头 ID 自增器 */
    private final AtomicInteger stateHeaderId = new AtomicInteger(0);

    /** connection 消息头 ID 自增器 */
    private final AtomicInteger connectionHeaderId = new AtomicInteger(0);

    /** order 消息头 ID 自增器 */
    private final AtomicInteger orderHeaderId = new AtomicInteger(0);

    /** instantActions 消息头 ID 自增器 */
    private final AtomicInteger instantActionsHeaderId = new AtomicInteger(0);

    /**
     * 创建车辆上下文。
     *
     * @param manufacturer 制造商名称
     * @param serialNumber 车辆序列号
     */
    public VehicleContext(String manufacturer, String serialNumber) {
        this.manufacturer = manufacturer;
        this.serialNumber = serialNumber;
        this.vehicleId = manufacturer + ":" + serialNumber;
    }

    /**
     * 获取状态锁。调用方必须在 {@code finally} 块中调用 {@link #unlock()}。
     */
    public void lock() { stateLock.lock(); }

    /**
     * 释放状态锁。
     */
    public void unlock() { stateLock.unlock(); }

    /**
     * 尝试在指定超时内获取状态锁。
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位
     * @return 是否成功获取锁
     * @throws InterruptedException 等待过程中被中断
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return stateLock.tryLock(timeout, unit);
    }

    /**
     * 生成下一个 state 消息头 ID（原子自增）。
     *
     * @return 新的 headerId
     */
    public int nextStateHeaderId() { return stateHeaderId.incrementAndGet(); }

    /**
     * 生成下一个 connection 消息头 ID（原子自增）。
     *
     * @return 新的 headerId
     */
    public int nextConnectionHeaderId() { return connectionHeaderId.incrementAndGet(); }

    /**
     * 生成下一个 order 消息头 ID（原子自增）。
     *
     * @return 新的 headerId
     */
    public int nextOrderHeaderId() { return orderHeaderId.incrementAndGet(); }

    /**
     * 生成下一个 instantActions 消息头 ID（原子自增）。
     *
     * @return 新的 headerId
     */
    public int nextInstantActionsHeaderId() { return instantActionsHeaderId.incrementAndGet(); }

    // Identity getters
    public String getManufacturer() { return manufacturer; }
    public String getSerialNumber() { return serialNumber; }
    public String getVehicleId() { return vehicleId; }

    // Mode flags
    public boolean isProxyMode() { return proxyMode; }
    public void setProxyMode(boolean proxyMode) { this.proxyMode = proxyMode; }

    public boolean isServerMode() { return serverMode; }
    public void setServerMode(boolean serverMode) { this.serverMode = serverMode; }

    // Proxy state
    public AgvState getAgvState() { return agvState; }
    public void setAgvState(AgvState agvState) { this.agvState = agvState; }

    public Order getCurrentOrder() { return currentOrder; }
    public void setCurrentOrder(Order currentOrder) { this.currentOrder = currentOrder; }

    public ProxyClientState getClientState() { return clientState; }
    public void setClientState(ProxyClientState clientState) { this.clientState = clientState; }

    public int getCurrentNodeIndex() { return currentNodeIndex; }
    public void setCurrentNodeIndex(int currentNodeIndex) { this.currentNodeIndex = currentNodeIndex; }

    public int getNextStopIndex() { return nextStopIndex; }
    public void setNextStopIndex(int nextStopIndex) { this.nextStopIndex = nextStopIndex; }

    public boolean isReachedWaypoint() { return reachedWaypoint; }
    public void setReachedWaypoint(boolean reachedWaypoint) { this.reachedWaypoint = reachedWaypoint; }

    public long getNavigationStartTime() { return navigationStartTime; }
    public void setNavigationStartTime(long navigationStartTime) { this.navigationStartTime = navigationStartTime; }

    public Map<String, Long> getActionStartTimes() { return Collections.unmodifiableMap(actionStartTimes); }

    public void putActionStartTime(String actionId, long startTime) { actionStartTimes.put(actionId, startTime); }

    public void removeActionStartTime(String actionId) { actionStartTimes.remove(actionId); }

    public void clearActionStartTimes() { actionStartTimes.clear(); }

    public MqttClient getProxyMqttClient() { return proxyMqttClient; }
    public void setProxyMqttClient(MqttClient proxyMqttClient) { this.proxyMqttClient = proxyMqttClient; }

    // Server state
    public AgvState getLastReceivedState() { return lastReceivedState; }
    public void setLastReceivedState(AgvState lastReceivedState) { this.lastReceivedState = lastReceivedState; }

    public Order getLastSentOrder() { return lastSentOrder; }
    public void setLastSentOrder(Order lastSentOrder) { this.lastSentOrder = lastSentOrder; }

    public String getConnectionState() { return connectionState; }
    public void setConnectionState(String connectionState) { this.connectionState = connectionState; }

    public long getLastSeenTimestamp() { return lastSeenTimestamp; }
    public void setLastSeenTimestamp(long lastSeenTimestamp) { this.lastSeenTimestamp = lastSeenTimestamp; }
}
