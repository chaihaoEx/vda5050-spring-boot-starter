package com.navasmart.vda5050.proxy.validation;

import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 入站 Order 消息校验器。
 *
 * <p>校验规则：
 * <ul>
 *   <li>orderId 非空</li>
 *   <li>nodes 非空且不为空列表</li>
 *   <li>Node sequenceId 偶数、严格递增</li>
 *   <li>Edge sequenceId 奇数、严格递增</li>
 *   <li>Edge 数量 = Node 数量 - 1（若有 edge）</li>
 * </ul>
 */
public class OrderValidator {

    /**
     * 校验入站 Order 消息。
     *
     * @param order 待校验的 Order
     * @return 校验错误列表，空列表表示通过校验
     */
    public List<String> validate(Order order) {
        List<String> errors = new ArrayList<>();

        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            errors.add("orderId is required");
        }

        List<Node> nodes = order.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            errors.add("Order must contain at least one node");
            return errors;
        }

        // Node sequenceId 校验：偶数、严格递增
        int prevNodeSeq = -1;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.getNodeId() == null || node.getNodeId().isEmpty()) {
                errors.add("Node at index " + i + " has null/empty nodeId");
            }
            int seq = node.getSequenceId();
            if (seq % 2 != 0) {
                errors.add("Node sequenceId must be even, got " + seq + " at index " + i);
            }
            if (seq <= prevNodeSeq) {
                errors.add("Node sequenceId must be strictly increasing, got " + seq
                        + " after " + prevNodeSeq + " at index " + i);
            }
            prevNodeSeq = seq;
        }

        // Edge 校验
        List<Edge> edges = order.getEdges();
        if (edges != null && !edges.isEmpty()) {
            if (edges.size() != nodes.size() - 1) {
                errors.add("Edge count (" + edges.size() + ") must equal node count - 1 ("
                        + (nodes.size() - 1) + ")");
            }

            int prevEdgeSeq = -1;
            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);
                int seq = edge.getSequenceId();
                if (seq % 2 == 0) {
                    errors.add("Edge sequenceId must be odd, got " + seq + " at index " + i);
                }
                if (seq <= prevEdgeSeq) {
                    errors.add("Edge sequenceId must be strictly increasing, got " + seq
                            + " after " + prevEdgeSeq + " at index " + i);
                }
                prevEdgeSeq = seq;
            }
        }

        return errors;
    }

    /**
     * 校验订单更新的 orderUpdateId 单调递增性。
     *
     * @param newOrder     新收到的订单
     * @param currentOrder 当前正在执行的订单
     * @return 校验错误列表
     */
    public List<String> validateUpdate(Order newOrder, Order currentOrder) {
        List<String> errors = new ArrayList<>();

        if (currentOrder != null
                && currentOrder.getOrderId().equals(newOrder.getOrderId())
                && newOrder.getOrderUpdateId() <= currentOrder.getOrderUpdateId()) {
            errors.add("orderUpdateId must be strictly increasing: received "
                    + newOrder.getOrderUpdateId() + ", current " + currentOrder.getOrderUpdateId());
        }

        return errors;
    }
}
