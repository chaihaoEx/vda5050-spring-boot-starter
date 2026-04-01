package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * VDA5050 动作执行状态（ActionState）。
 * <p>
 * 出现在 {@link AgvState#getActionStates()} 中，描述每个动作的当前执行状态。
 * AGV 通过此对象向调度系统报告动作的执行进度和结果。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionState {

    /** 动作唯一标识符，与 {@link Action#getActionId()} 对应。 */
    private String actionId;

    /** 动作类型标识，与 {@link Action#getActionType()} 对应。 */
    private String actionType;

    /** 动作的人类可读描述。可选字段。 */
    private String actionDescription;

    /**
     * 动作执行状态。取值包括：
     * <ul>
     *   <li>{@code WAITING} - 等待执行，动作已被接受但尚未开始</li>
     *   <li>{@code INITIALIZING} - 正在初始化</li>
     *   <li>{@code RUNNING} - 正在执行</li>
     *   <li>{@code PAUSED} - 已暂停</li>
     *   <li>{@code FINISHED} - 执行完成</li>
     *   <li>{@code FAILED} - 执行失败</li>
     * </ul>
     */
    private String actionStatus;

    /** 动作执行结果描述。可选字段。通常在动作完成或失败时提供详细信息。 */
    private String resultDescription;

    public ActionState() {}

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }

    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String actionStatus) { this.actionStatus = actionStatus; }

    public String getResultDescription() { return resultDescription; }
    public void setResultDescription(String resultDescription) { this.resultDescription = resultDescription; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ActionState that = (ActionState) o;
        return Objects.equals(actionId, that.actionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionId);
    }
}
