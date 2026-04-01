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
    void agvState_setEdgeStates_defensiveCopy() {
        List<EdgeState> original = new ArrayList<>();
        original.add(new EdgeState());
        AgvState state = new AgvState();
        state.setEdgeStates(original);
        original.add(new EdgeState());
        assertThat(state.getEdgeStates()).hasSize(1);
    }

    @Test
    void agvState_setLoads_defensiveCopy() {
        List<Load> original = new ArrayList<>();
        original.add(new Load());
        AgvState state = new AgvState();
        state.setLoads(original);
        original.add(new Load());
        assertThat(state.getLoads()).hasSize(1);
    }

    @Test
    void agvState_setActionStates_defensiveCopy() {
        List<ActionState> original = new ArrayList<>();
        original.add(new ActionState());
        AgvState state = new AgvState();
        state.setActionStates(original);
        original.add(new ActionState());
        assertThat(state.getActionStates()).hasSize(1);
    }

    @Test
    void agvState_setInformations_defensiveCopy() {
        List<Info> original = new ArrayList<>();
        original.add(new Info());
        AgvState state = new AgvState();
        state.setInformations(original);
        original.add(new Info());
        assertThat(state.getInformations()).hasSize(1);
    }

    @Test
    void info_setInfoReferences_defensiveCopy() {
        List<InfoReference> original = new ArrayList<>();
        original.add(new InfoReference("rk", "rv"));
        Info info = new Info();
        info.setInfoReferences(original);
        original.add(new InfoReference("rk2", "rv2"));
        assertThat(info.getInfoReferences()).hasSize(1);
    }

    @Test
    void trajectory_setKnotVector_defensiveCopy() {
        List<Double> original = new ArrayList<>();
        original.add(1.0);
        Trajectory trajectory = new Trajectory();
        trajectory.setKnotVector(original);
        original.add(2.0);
        assertThat(trajectory.getKnotVector()).hasSize(1);
    }

    @Test
    void trajectory_setControlPoints_defensiveCopy() {
        List<ControlPoint> original = new ArrayList<>();
        original.add(new ControlPoint());
        Trajectory trajectory = new Trajectory();
        trajectory.setControlPoints(original);
        original.add(new ControlPoint());
        assertThat(trajectory.getControlPoints()).hasSize(1);
    }

    @Test
    void typeSpecification_setLocalizationTypes_defensiveCopy() {
        List<String> original = new ArrayList<>();
        original.add("NATURAL");
        TypeSpecification ts = new TypeSpecification();
        ts.setLocalizationTypes(original);
        original.add("REFLECTOR");
        assertThat(ts.getLocalizationTypes()).hasSize(1);
    }

    @Test
    void typeSpecification_setNavigationTypes_defensiveCopy() {
        List<String> original = new ArrayList<>();
        original.add("AUTONOMOUS");
        TypeSpecification ts = new TypeSpecification();
        ts.setNavigationTypes(original);
        original.add("VIRTUAL_LINE_GUIDED");
        assertThat(ts.getNavigationTypes()).hasSize(1);
    }

    @Test
    void agvState_setNullList_returnsEmptyList() {
        AgvState state = new AgvState();
        state.setNodeStates(null);
        assertThat(state.getNodeStates()).isNotNull().isEmpty();
    }
}
