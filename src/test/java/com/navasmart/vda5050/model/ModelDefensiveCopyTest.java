package com.navasmart.vda5050.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelDefensiveCopyTest {

    @Test
    void agvState_setNodeStates_defensiveCopy() {
        List<NodeState> original = new ArrayList<>();
        original.add(new NodeState());
        AgvState state = new AgvState();
        state.setNodeStates(original);
        original.add(new NodeState());
        assertThat(state.getNodeStates()).hasSize(1);
    }

    @Test
    void agvState_setErrors_defensiveCopy() {
        List<Error> original = new ArrayList<>();
        original.add(new Error());
        AgvState state = new AgvState();
        state.setErrors(original);
        original.add(new Error());
        assertThat(state.getErrors()).hasSize(1);
    }

    @Test
    void order_setNodes_defensiveCopy() {
        List<Node> original = new ArrayList<>();
        original.add(new Node());
        Order order = new Order();
        order.setNodes(original);
        original.add(new Node());
        assertThat(order.getNodes()).hasSize(1);
    }

    @Test
    void order_setEdges_defensiveCopy() {
        List<Edge> original = new ArrayList<>();
        original.add(new Edge());
        Order order = new Order();
        order.setEdges(original);
        original.add(new Edge());
        assertThat(order.getEdges()).hasSize(1);
    }

    @Test
    void node_setActions_defensiveCopy() {
        List<Action> original = new ArrayList<>();
        original.add(new Action());
        Node node = new Node();
        node.setActions(original);
        original.add(new Action());
        assertThat(node.getActions()).hasSize(1);
    }

    @Test
    void edge_setActions_defensiveCopy() {
        List<Action> original = new ArrayList<>();
        original.add(new Action());
        Edge edge = new Edge();
        edge.setActions(original);
        original.add(new Action());
        assertThat(edge.getActions()).hasSize(1);
    }

    @Test
    void action_setActionParameters_defensiveCopy() {
        List<ActionParameter> original = new ArrayList<>();
        original.add(new ActionParameter("k1", "v1"));
        Action action = new Action();
        action.setActionParameters(original);
        original.add(new ActionParameter("k2", "v2"));
        assertThat(action.getActionParameters()).hasSize(1);
    }

    @Test
    void error_setErrorReferences_defensiveCopy() {
        List<ErrorReference> original = new ArrayList<>();
        original.add(new ErrorReference("rk", "rv"));
        Error error = new Error();
        error.setErrorReferences(original);
        original.add(new ErrorReference("rk2", "rv2"));
        assertThat(error.getErrorReferences()).hasSize(1);
    }

    @Test
    void instantActions_setInstantActions_defensiveCopy() {
        List<Action> original = new ArrayList<>();
        original.add(new Action());
        InstantActions ia = new InstantActions();
        ia.setInstantActions(original);
        original.add(new Action());
        assertThat(ia.getInstantActions()).hasSize(1);
    }

    @Test
    void agvState_setNullList_returnsEmptyList() {
        AgvState state = new AgvState();
        state.setNodeStates(null);
        assertThat(state.getNodeStates()).isNotNull().isEmpty();
    }
}
