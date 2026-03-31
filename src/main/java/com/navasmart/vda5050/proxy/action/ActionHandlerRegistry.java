package com.navasmart.vda5050.proxy.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 动作处理器注册中心，负责管理 actionType 到 {@link ActionHandler} 的映射关系。
 *
 * <p>在 Spring 容器启动时，通过 {@link org.springframework.beans.factory.annotation.Autowired}
 * 自动注入所有实现了 {@link ActionHandler} 接口的 Spring Bean，并根据各处理器声明的
 * {@link ActionHandler#getSupportedActionTypes()} 建立 actionType -> Handler 的映射。</p>
 *
 * <p>订单执行器在启动动作时会先查询此注册中心，如果找到匹配的处理器则使用它执行动作，
 * 否则回退到 {@link com.navasmart.vda5050.proxy.callback.Vda5050ProxyVehicleAdapter#onActionExecute}。</p>
 *
 * <p>线程安全：注册过程在容器启动阶段完成，运行时只读访问 handlerMap，因此是线程安全的。</p>
 *
 * @see ActionHandler
 */
@Component
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);

    /** actionType -> ActionHandler 的映射表 */
    private final Map<String, ActionHandler> handlerMap = new HashMap<>();

    /**
     * Spring 容器启动时自动注入所有 {@link ActionHandler} Bean 并注册。
     *
     * <p>如果容器中没有任何 ActionHandler Bean，{@code handlers} 参数为 {@code null}，
     * 方法直接返回（{@code required = false}）。</p>
     *
     * @param handlers 容器中所有的 ActionHandler Bean 列表，可能为 {@code null}
     */
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

    /**
     * 根据 actionType 查找已注册的处理器。
     *
     * @param actionType 动作类型字符串
     * @return 包含对应处理器的 Optional；如果未注册，返回空 Optional
     */
    public Optional<ActionHandler> getHandler(String actionType) {
        return Optional.ofNullable(handlerMap.get(actionType));
    }
}
