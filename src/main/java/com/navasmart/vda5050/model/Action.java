package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VDA5050 动作定义（Action）。
 * <p>
 * 描述 AGV 需要执行的一个动作，可以出现在节点、边或即时动作中。
 * 动作类型（actionType）由 VDA5050 标准或 AGV 厂商定义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Action {

    /**
     * 动作类型标识，定义要执行的动作。
     * VDA5050 标准预定义了一些类型（如 "pick"、"drop"、"startCharging" 等），
     * AGV 厂商也可自定义类型。
     */
    private String actionType;

    /** 动作唯一标识符，在整个订单范围内唯一。 */
    private String actionId;

    /** 动作的人类可读描述。可选字段。 */
    private String actionDescription;

    /**
     * 阻塞类型，定义动作执行时对 AGV 移动的影响。取值包括：
     * <ul>
     *   <li>{@code NONE} - 动作不阻塞 AGV 移动</li>
     *   <li>{@code SOFT} - 动作执行期间 AGV 可以移动，但在该节点处可能需要等待</li>
     *   <li>{@code HARD} - 动作执行期间 AGV 必须停止移动</li>
     * </ul>
     */
    private String blockingType;

    /** 动作参数列表，以键值对形式传递动作所需的参数。可选字段。 */
    private List<ActionParameter> actionParameters = new ArrayList<>();

    public Action() {}

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getActionDescription() { return actionDescription; }
    public void setActionDescription(String actionDescription) { this.actionDescription = actionDescription; }

    public String getBlockingType() { return blockingType; }
    public void setBlockingType(String blockingType) { this.blockingType = blockingType; }

    public List<ActionParameter> getActionParameters() { return actionParameters; }
    public void setActionParameters(List<ActionParameter> actionParameters) {
        this.actionParameters = actionParameters != null ? new ArrayList<>(actionParameters) : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Action that = (Action) o;
        return Objects.equals(actionId, that.actionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionId);
    }
}
