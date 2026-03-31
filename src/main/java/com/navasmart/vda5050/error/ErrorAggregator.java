package com.navasmart.vda5050.error;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import com.navasmart.vda5050.vehicle.VehicleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 错误聚合器，负责管理 {@link VehicleContext} 中 AGV 状态的错误列表。
 *
 * <p>提供添加、清除、查询错误的便捷方法，所有操作直接作用于
 * {@code ctx.getAgvState().getErrors()} 列表。</p>
 *
 * <p><b>注意：</b>调用方应在持有 {@link VehicleContext#lock()} 的前提下操作，
 * 以避免并发修改错误列表。</p>
 */
@Component
public class ErrorAggregator {

    private final Vda5050ErrorFactory errorFactory;

    public ErrorAggregator(Vda5050ErrorFactory errorFactory) {
        this.errorFactory = errorFactory;
    }

    /**
     * 向车辆状态中添加一个已构建好的错误。
     *
     * @param ctx   车辆上下文
     * @param error 要添加的错误对象
     */
    public void addError(VehicleContext ctx, Error error) {
        ctx.getAgvState().getErrors().add(error);
    }

    /**
     * 向车辆状态中添加一个 WARNING 级别的错误。
     *
     * @param ctx         车辆上下文
     * @param description 错误描述
     * @param errorType   错误类型标识符
     * @param references  错误引用键值对（可为 null）
     */
    public void addWarning(VehicleContext ctx, String description, String errorType,
                           Map<String, String> references) {
        Error error = errorFactory.createError(ErrorLevel.WARNING, description, errorType, references);
        ctx.getAgvState().getErrors().add(error);
    }

    /**
     * 向车辆状态中添加一个 FATAL 级别的错误。
     *
     * @param ctx         车辆上下文
     * @param description 错误描述
     * @param errorType   错误类型标识符
     */
    public void addFatalError(VehicleContext ctx, String description, String errorType) {
        Error error = errorFactory.createFatal(description, errorType, null);
        ctx.getAgvState().getErrors().add(error);
    }

    /**
     * 清除车辆状态中的所有错误。
     *
     * @param ctx 车辆上下文
     */
    public void clearAllErrors(VehicleContext ctx) {
        ctx.getAgvState().getErrors().clear();
    }

    /**
     * 检查车辆是否存在 FATAL 级别的错误。
     *
     * @param ctx 车辆上下文
     * @return 如果存在至少一个 FATAL 错误则返回 true
     */
    public boolean hasFatalError(VehicleContext ctx) {
        return ctx.getAgvState().getErrors().stream()
                .anyMatch(e -> ErrorLevel.FATAL.getValue().equals(e.getErrorLevel()));
    }

    /**
     * 将外部系统（如 FMS）产生的错误合并到车辆状态中。
     *
     * @param ctx       车辆上下文
     * @param fmsErrors 外部错误列表（可为 null，null 时不做任何操作）
     */
    public void mergeExternalErrors(VehicleContext ctx, List<Error> fmsErrors) {
        if (fmsErrors != null) {
            ctx.getAgvState().getErrors().addAll(fmsErrors);
        }
    }
}
