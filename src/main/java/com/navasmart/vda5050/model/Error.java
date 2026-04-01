package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VDA5050 错误信息。
 * <p>
 * 描述 AGV 上报的错误详情，包括错误类型、错误描述、错误级别和关联引用。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 * <p>
 * 注意：此类名称与 {@link java.lang.Error} 存在命名冲突，使用时请注意导入路径。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Error {

    /**
     * 错误类型标识符。
     * <p>
     * 用于标识错误的具体类型，便于主控系统进行分类处理。
     * </p>
     */
    private String errorType;

    /**
     * 错误引用列表。
     * <p>
     * 包含与该错误相关的键值对引用信息，用于提供更多的错误上下文。
     * </p>
     */
    private List<ErrorReference> errorReferences = new ArrayList<>();

    /**
     * 错误的可读描述信息。可选字段。
     */
    private String errorDescription;

    /**
     * 错误级别。
     * <p>
     * 可选值：
     * <ul>
     *   <li>{@code "WARNING"} - 警告级别，AGV 仍可继续运行</li>
     *   <li>{@code "FATAL"} - 致命级别，AGV 无法继续运行</li>
     * </ul>
     * </p>
     */
    private String errorLevel;

    public Error() {}

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public List<ErrorReference> getErrorReferences() { return errorReferences; }
    public void setErrorReferences(List<ErrorReference> errorReferences) {
        this.errorReferences = errorReferences != null ? new ArrayList<>(errorReferences) : new ArrayList<>();
    }

    public String getErrorDescription() { return errorDescription; }
    public void setErrorDescription(String errorDescription) { this.errorDescription = errorDescription; }

    public String getErrorLevel() { return errorLevel; }
    public void setErrorLevel(String errorLevel) { this.errorLevel = errorLevel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Error that = (Error) o;
        return Objects.equals(errorType, that.errorType) && Objects.equals(errorDescription, that.errorDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorType, errorDescription);
    }
}
