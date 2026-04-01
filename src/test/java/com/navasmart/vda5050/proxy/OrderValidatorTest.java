package com.navasmart.vda5050.proxy;

import com.navasmart.vda5050.model.Edge;
import com.navasmart.vda5050.model.Node;
import com.navasmart.vda5050.model.Order;
import com.navasmart.vda5050.proxy.validation.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValidatorTest {

    private OrderValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderValidator();
    }

    // --- Helper methods ---

    private Node createNode(String nodeId, int sequenceId) {
        Node node = new Node();
        node.setNodeId(nodeId);
        node.setSequenceId(sequenceId);
        return node;
    }

    private Edge createEdge(String edgeId, int sequenceId) {
        Edge edge = new Edge();
        edge.setEdgeId(edgeId);
        edge.setSequenceId(sequenceId);
        return edge;
    }

    private Order createValidOrder() {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setOrderUpdateId(0);
        order.setNodes(List.of(createNode("n1", 0), createNode("n2", 2)));
        order.setEdges(List.of(createEdge("e1", 1)));
        return order;
    }

    // --- validate() tests ---

    @Test
    void validate_validOrder_returnsNoErrors() {
        Order order = createValidOrder();

        List<String> errors = validator.validate(order);

        assertThat(errors).isEmpty();
    }

    @Test
    void validate_nullOrderId_returnsError() {
        Order order = createValidOrder();
        order.setOrderId(null);

        List<String> errors = validator.validate(order);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("orderId");
    }

    @Test
    void validate_nullNodes_returnsError() {
        Order order = createValidOrder();
        order.setNodes(null);

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("at least one node"));
    }

    @Test
    void validate_emptyNodes_returnsError() {
        Order order = createValidOrder();
        order.setNodes(new ArrayList<>());

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("at least one node"));
    }

    @Test
    void validate_oddNodeSequenceId_returnsError() {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setNodes(List.of(createNode("n1", 1), createNode("n2", 3)));
        order.setEdges(List.of(createEdge("e1", 2)));

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Node sequenceId must be even"));
    }

    @Test
    void validate_nonIncreasingNodeSequenceId_returnsError() {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setNodes(List.of(createNode("n1", 0), createNode("n2", 0)));
        order.setEdges(List.of(createEdge("e1", 1)));

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e ->
                assertThat(e).contains("Node sequenceId must be strictly increasing"));
    }

    @Test
    void validate_evenEdgeSequenceId_returnsError() {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setNodes(List.of(createNode("n1", 0), createNode("n2", 2)));
        order.setEdges(List.of(createEdge("e1", 2)));

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Edge sequenceId must be odd"));
    }

    @Test
    void validate_edgeCountMismatch_returnsError() {
        Order order = new Order();
        order.setOrderId("order-1");
        order.setNodes(List.of(createNode("n1", 0), createNode("n2", 2)));
        order.setEdges(List.of(createEdge("e1", 1), createEdge("e2", 3)));

        List<String> errors = validator.validate(order);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Edge count"));
    }

    // --- validateUpdate() tests ---

    @Test
    void validateUpdate_validIncreasingUpdateId_returnsNoErrors() {
        Order current = createValidOrder();
        current.setOrderUpdateId(1);

        Order update = createValidOrder();
        update.setOrderUpdateId(2);

        List<String> errors = validator.validateUpdate(update, current);

        assertThat(errors).isEmpty();
    }

    @Test
    void validateUpdate_nonMonotonicUpdateId_returnsError() {
        Order current = createValidOrder();
        current.setOrderUpdateId(5);

        Order update = createValidOrder();
        update.setOrderUpdateId(3);

        List<String> errors = validator.validateUpdate(update, current);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("orderUpdateId must be strictly increasing");
    }

    @Test
    void validateUpdate_nullCurrentOrder_returnsNoErrors() {
        Order update = createValidOrder();
        update.setOrderUpdateId(1);

        List<String> errors = validator.validateUpdate(update, null);

        assertThat(errors).isEmpty();
    }
}
