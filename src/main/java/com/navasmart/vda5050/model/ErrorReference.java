package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * VDA5050 错误引用信息。
 * <p>
 * 以键值对形式描述与错误相关的引用信息，用于提供错误的详细上下文。
 * 例如可以引用导致错误的订单 ID、节点 ID 或动作 ID 等。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorReference {

    /**
     * 引用键名。
     * <p>
     * 例如 {@code "orderId"}、{@code "nodeId"}、{@code "actionId"} 等。
     * </p>
     */
    private String referenceKey;

    /**
     * 引用键对应的值。
     */
    private String referenceValue;

    public ErrorReference() {}

    public ErrorReference(String referenceKey, String referenceValue) {
        this.referenceKey = referenceKey;
        this.referenceValue = referenceValue;
    }

    public String getReferenceKey() { return referenceKey; }
    public void setReferenceKey(String referenceKey) { this.referenceKey = referenceKey; }

    public String getReferenceValue() { return referenceValue; }
    public void setReferenceValue(String referenceValue) { this.referenceValue = referenceValue; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ErrorReference that = (ErrorReference) o;
        return Objects.equals(referenceKey, that.referenceKey) && Objects.equals(referenceValue, that.referenceValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceKey, referenceValue);
    }
}
