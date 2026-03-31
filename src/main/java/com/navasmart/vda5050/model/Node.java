package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 订单中的路径节点（Node）。
 * <p>
 * 定义 AGV 行驶路径中必须经过的一个节点，包含位置信息和到达该节点后需要执行的动作列表。
 * 通过 {@code released} 标志区分已确认节点（base）和预览节点（horizon）：
 * <ul>
 *   <li>{@code released = true} - 已确认节点（base），AGV 必须驶向该节点</li>
 *   <li>{@code released = false} - 预览节点（horizon），仅供 AGV 提前规划路径使用</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node {

    /** 节点唯一标识符，对应地图中的节点 ID。 */
    private String nodeId;

    /** 节点在订单中的序列号。节点和边交替排列，节点序号为偶数（0, 2, 4, ...）。 */
    private int sequenceId;

    /** 节点的人类可读描述。可选字段。 */
    private String nodeDescription;

    /**
     * 节点释放标志。
     * {@code true} 表示已确认节点（base），AGV 必须执行；
     * {@code false} 表示预览节点（horizon），AGV 仅用于路径规划。
     */
    private boolean released;

    /** 节点位置坐标信息。对于已确认节点必须提供；预览节点可选。 */
    private NodePosition nodePosition;

    /** 到达该节点后需要执行的动作列表。可以为空列表。 */
    private List<Action> actions = new ArrayList<>();

    public Node() {}

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int getSequenceId() { return sequenceId; }
    public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }

    public String getNodeDescription() { return nodeDescription; }
    public void setNodeDescription(String nodeDescription) { this.nodeDescription = nodeDescription; }

    public boolean isReleased() { return released; }
    public void setReleased(boolean released) { this.released = released; }

    public NodePosition getNodePosition() { return nodePosition; }
    public void setNodePosition(NodePosition nodePosition) { this.nodePosition = nodePosition; }

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) { this.actions = actions; }
}
