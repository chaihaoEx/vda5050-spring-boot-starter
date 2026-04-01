package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * VDA5050 动作参数（ActionParameter）。
 * <p>
 * 以键值对形式描述动作所需的参数。例如，"pick" 动作可能需要
 * {@code key="stationType", value="floor"} 来指定拾取站类型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionParameter {

    /** 参数键名，标识参数的名称。 */
    private String key;

    /** 参数值，以字符串形式表示。具体含义由动作类型和参数键名决定。 */
    private String value;

    public ActionParameter() {}

    public ActionParameter(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ActionParameter that = (ActionParameter) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
