package com.navasmart.vda5050.proxy.action;

import com.navasmart.vda5050.model.Action;
import com.navasmart.vda5050.proxy.callback.ActionResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义动作处理器接口，用于处理特定类型的 VDA5050 动作。
 *
 * <p><b>注册机制：</b>实现此接口并注册为 Spring Bean 后，框架会通过
 * {@link ActionHandlerRegistry} 自动发现并注册。当订单执行器需要执行动作时，
 * 优先匹配已注册的 ActionHandler（按 {@link #getSupportedActionTypes()} 匹配 actionType），
 * 未匹配到时才回退到 {@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter#onActionExecute}。</p>
 *
 * <p><b>优先级：</b>同一个 actionType 只能注册一个 ActionHandler。如果多个 Handler 声明支持同一
 * actionType，后注册的会覆盖先注册的（取决于 Spring Bean 的加载顺序）。</p>
 *
 * <p>线程安全：实现类应确保 {@link #execute} 方法是线程安全的，因为它可能被不同车辆的执行循环并发调用。</p>
 *
 * @see ActionHandlerRegistry
 * @see ActionResult
 */
public interface ActionHandler {

    /**
     * 返回此处理器支持的动作类型集合。
     *
     * <p>框架在启动时调用此方法获取支持的 actionType 列表，
     * 并将它们注册到 {@link ActionHandlerRegistry} 中。</p>
     *
     * @return 支持的 actionType 字符串集合，不应返回 {@code null} 或空集合
     */
    Set<String> getSupportedActionTypes();

    /**
     * 执行指定的动作。
     *
     * <p>调用时机：订单执行循环中，当节点上某个动作的 actionType 匹配到此处理器时被调用。
     * 返回的 {@link CompletableFuture} 完成后，框架会自动更新对应的 ActionState。</p>
     *
     * @param vehicleId 车辆标识符
     * @param action    待执行的动作，包含 actionType、actionId、参数等信息
     * @return 动作执行结果的异步 Future
     */
    CompletableFuture<ActionResult> execute(String vehicleId, Action action);

    /**
     * 取消正在执行的动作（可选实现）。
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要取消的动作 ID
     */
    default void cancel(String vehicleId, String actionId) {}

    /**
     * 暂停正在执行的动作（可选实现）。
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要暂停的动作 ID
     */
    default void pause(String vehicleId, String actionId) {}

    /**
     * 恢复已暂停的动作（可选实现）。
     *
     * @param vehicleId 车辆标识符
     * @param actionId  需要恢复的动作 ID
     */
    default void resume(String vehicleId, String actionId) {}

    /**
     * 声明此处理器执行的动作是否支持暂停。
     *
     * <p>默认返回 {@code false}。如果返回 {@code true}，
     * 框架在收到 startPause 即时动作时会调用 {@link #pause} 方法。</p>
     *
     * @return 如果动作可以被暂停返回 {@code true}
     */
    default boolean canBePaused() { return false; }
}
