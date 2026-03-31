package com.navasmart.vda5050.proxy.callback;

import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * VDA5050 Proxy 模式下最重要的用户接口，用于将 VDA5050 协议指令转发到实际车辆控制系统。
 *
 * <p>用户必须实现此接口并注册为 Spring Bean，框架会在订单执行、即时动作处理等环节
 * 自动回调对应方法。所有回调方法均在持有 {@code VehicleContext} 锁的情况下被调用（除异步回调外），
 * 实现时应避免长时间阻塞，耗时操作请通过 {@link CompletableFuture} 异步返回。</p>
 *
 * <p>线程安全：框架保证同一辆车的回调不会并发调用（通过 VehicleContext 的锁机制），
 * 但异步返回的 {@link CompletableFuture} 完成回调可能在任意线程上执行。</p>
 *
 * @see Vda5050ProxyStateProvider
 * @see NavigationResult
 * @see ActionResult
 */
public interface Vda5050ProxyVehicleAdapter {

    /**
     * 当订单执行器需要车辆导航到下一个节点时被调用。
     *
     * <p>调用时机：当前节点的所有动作执行完毕后，执行器调用此方法命令车辆开始移动。
     * 返回的 {@link CompletableFuture} 应在车辆到达目标节点后完成。</p>
     *
     * <p>如果导航失败，返回 {@link NavigationResult#failure(String)}，框架将记录 FATAL 级别错误
     * 并中止当前订单。</p>
     *
     * @param vehicleId  车辆标识符
     * @param targetNode 目标节点（下一个需要到达的节点）
     * @param waypoints  从目标节点到下一个已释放节点之间的所有途经节点（包含目标节点自身）
     * @param edges      对应的边列表，描述节点之间的路径约束（最大速度、轨迹等）
     * @return 导航结果的异步 Future；成功时返回 {@link NavigationResult#success()}，
     *         失败时返回 {@link NavigationResult#failure(String)}
     */
    CompletableFuture<NavigationResult> onNavigate(String vehicleId, Node targetNode,
                                                    List<Node> waypoints, List<Edge> edges);

    /**
     * 当需要取消当前导航时被调用。
     *
     * <p>调用时机：发生 FATAL 错误导致订单中止时，框架会先调用此方法取消正在进行的导航。
     * 实现时应立即停止车辆移动。</p>
     *
     * @param vehicleId 车辆标识符
     */
    void onNavigationCancel(String vehicleId);

    /**
     * 当需要执行一个动作（Action）时被调用。
     *
     * <p>调用时机：订单执行循环中，节点上的动作按 {@link com.navasmart.vda5050.model.enums.BlockingType}
     * 规则依次或并行启动时调用。如果已注册对应 actionType 的 {@link com.navasmart.vda5050.proxy.action.ActionHandler}，
     * 则优先使用 ActionHandler，本方法作为兜底回调。</p>
     *
     * @param vehicleId 车辆标识符
     * @param action    待执行的动作，包含 actionType、actionId、参数等信息
     * @return 动作执行结果的异步 Future；成功时返回 {@link ActionResult#success()}，
     *         失败时返回 {@link ActionResult#failure(String)}
     */
    CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action);

    /**
     * 当需要取消正在执行的动作时被调用。
     *
     * <p>调用时机：收到 cancelOrder 即时动作或订单被中止时，框架会尝试取消所有正在运行的动作。</p>
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要取消的动作 ID
     */
    void onActionCancel(String vehicleId, String actionId);

    /**
     * 当需要暂停正在执行的动作时被调用（可选实现）。
     *
     * <p>调用时机：收到 startPause 即时动作时，框架在暂停订单执行的同时
     * 会通知暂停正在运行的动作。默认空实现。</p>
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要暂停的动作 ID
     */
    default void onActionPause(String vehicleId, String actionId) {}

    /**
     * 当需要恢复已暂停的动作时被调用（可选实现）。
     *
     * <p>调用时机：收到 stopPause 即时动作时被调用。默认空实现。</p>
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要恢复的动作 ID
     */
    default void onActionResume(String vehicleId, String actionId) {}

    /**
     * 当收到 startPause 即时动作时被调用，通知车辆暂停运行。
     *
     * <p>调用时机：状态机从 RUNNING 转换到 PAUSED 状态时调用。
     * 实现时应暂停车辆的所有运动和当前操作。</p>
     *
     * @param vehicleId 车辆标识符
     */
    void onPause(String vehicleId);

    /**
     * 当收到 stopPause 即时动作时被调用，通知车辆恢复运行。
     *
     * <p>调用时机：状态机从 PAUSED 转换回 RUNNING 状态时调用。
     * 实现时应恢复车辆的运动和之前暂停的操作。</p>
     *
     * @param vehicleId 车辆标识符
     */
    void onResume(String vehicleId);

    /**
     * 当收到 cancelOrder 即时动作时被调用，通知车辆取消当前订单。
     *
     * <p>调用时机：状态机处理 cancelOrder 即时动作时调用，
     * 状态随后转换为 IDLE。实现时应立即停止所有与当前订单相关的操作。</p>
     *
     * @param vehicleId 车辆标识符
     */
    void onOrderCancel(String vehicleId);

    /**
     * 当需要启动远程遥控模式时被调用（可选实现）。
     *
     * <p>默认空实现，如需支持遥操作功能请覆盖此方法。</p>
     *
     * @param vehicleId 车辆标识符
     */
    default void onTeleopStart(String vehicleId) {}

    /**
     * 当需要停止远程遥控模式时被调用（可选实现）。
     *
     * <p>默认空实现，如需支持遥操作功能请覆盖此方法。</p>
     *
     * @param vehicleId 车辆标识符
     */
    default void onTeleopStop(String vehicleId) {}
}
