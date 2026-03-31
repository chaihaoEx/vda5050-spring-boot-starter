package com.navasmart.vda5050.error;

import com.navasmart.vda5050.model.Error;
import com.navasmart.vda5050.model.ErrorReference;
import com.navasmart.vda5050.model.enums.ErrorLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VDA5050 错误对象工厂，用于创建符合协议规范的 {@link Error} 实例。
 *
 * <h3>VDA5050 错误级别含义</h3>
 * <ul>
 *   <li><b>WARNING</b>：警告级别，AGV 仍可继续运行，但存在需要关注的异常情况</li>
 *   <li><b>FATAL</b>：致命级别，AGV 无法继续执行当前任务，需要外部干预</li>
 * </ul>
 *
 * <p>本类为无状态 Spring 组件，线程安全。</p>
 */
@Component
public class Vda5050ErrorFactory {

    /**
     * 创建一个 VDA5050 错误对象。
     *
     * @param level       错误级别（WARNING 或 FATAL）
     * @param description 人类可读的错误描述
     * @param errorType   错误类型标识符，用于程序化处理
     * @param references  错误引用键值对（可为 null），用于关联相关对象（如 orderId、actionId 等）
     * @return 构建好的 {@link Error} 实例
     */
    public Error createError(ErrorLevel level, String description, String errorType,
                             Map<String, String> references) {
        Error error = new Error();
        error.setErrorLevel(level.getValue());
        error.setErrorDescription(description);
        error.setErrorType(errorType);
        if (references != null) {
            // 将 Map 键值对转换为 VDA5050 ErrorReference 列表
            List<ErrorReference> refs = new ArrayList<>();
            references.forEach((k, v) -> refs.add(new ErrorReference(k, v)));
            error.setErrorReferences(refs);
        }
        return error;
    }

    /**
     * 创建一个 WARNING 级别的错误。
     *
     * @param description 人类可读的错误描述
     * @param errorType   错误类型标识符
     * @return WARNING 级别的 {@link Error} 实例
     */
    public Error createWarning(String description, String errorType) {
        return createError(ErrorLevel.WARNING, description, errorType, null);
    }

    /**
     * 创建一个 FATAL 级别的错误。
     *
     * @param description 人类可读的错误描述
     * @param errorType   错误类型标识符
     * @param references  错误引用键值对（可为 null）
     * @return FATAL 级别的 {@link Error} 实例
     */
    public Error createFatal(String description, String errorType,
                             Map<String, String> references) {
        return createError(ErrorLevel.FATAL, description, errorType, references);
    }
}
