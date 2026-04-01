package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * VDA5050 信息引用。
 * <p>
 * 以键值对形式描述与信息条目相关的引用数据，用于提供信息的详细上下文。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfoReference {

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

    public InfoReference() {}

    public InfoReference(String referenceKey, String referenceValue) {
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
        InfoReference that = (InfoReference) o;
        return Objects.equals(referenceKey, that.referenceKey) && Objects.equals(referenceValue, that.referenceValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceKey, referenceValue);
    }
}
