package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * VDA5050 安全状态信息。
 * <p>
 * 描述 AGV 当前的安全相关状态，包括急停类型和安全区域违反情况。
 * 该对象作为 AGV 状态消息的一部分上报给主控系统。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SafetyState {

    /**
     * 急停类型。
     * <p>
     * 可选值包括：
     * <ul>
     *   <li>{@code "AUTOACK"} - 自动确认的急停</li>
     *   <li>{@code "MANUAL"} - 需要手动确认的急停</li>
     *   <li>{@code "REMOTE"} - 远程触发的急停</li>
     *   <li>{@code "NONE"} - 无急停</li>
     * </ul>
     * </p>
     */
    private String eStop;

    /**
     * 是否违反了保护区域/安全区域。
     * <p>
     * {@code true} 表示 AGV 的保护区域被侵入或 AGV 偏离了安全区域；
     * {@code false} 表示安全区域状态正常。
     * </p>
     */
    private boolean fieldViolation;

    public SafetyState() {}

    public String getEStop() { return eStop; }
    public void setEStop(String eStop) { this.eStop = eStop; }

    public boolean isFieldViolation() { return fieldViolation; }
    public void setFieldViolation(boolean fieldViolation) { this.fieldViolation = fieldViolation; }
}
