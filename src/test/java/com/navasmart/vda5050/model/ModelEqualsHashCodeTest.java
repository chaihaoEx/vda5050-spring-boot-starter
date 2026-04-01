package com.navasmart.vda5050.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelEqualsHashCodeTest {

    // --- Order ---

    @Test
    void order_reflexive() {
        Order a = new Order();
        a.setOrderId("O1");
        a.setOrderUpdateId(1L);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void order_symmetric() {
        Order a = new Order();
        a.setOrderId("O1");
        a.setOrderUpdateId(1L);
        Order b = new Order();
        b.setOrderId("O1");
        b.setOrderUpdateId(1L);
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void order_different() {
        Order a = new Order();
        a.setOrderId("O1");
        a.setOrderUpdateId(1L);
        Order c = new Order();
        c.setOrderId("O2");
        c.setOrderUpdateId(2L);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void order_hashCodeConsistency() {
        Order a = new Order();
        a.setOrderId("O1");
        a.setOrderUpdateId(1L);
        Order b = new Order();
        b.setOrderId("O1");
        b.setOrderUpdateId(1L);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void order_nullSafety() {
        Order a = new Order();
        a.setOrderId("O1");
        a.setOrderUpdateId(1L);
        assertThat(a).isNotEqualTo(null);
    }

    // --- Node ---

    @Test
    void node_reflexive() {
        Node a = new Node();
        a.setNodeId("N1");
        a.setSequenceId(1);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void node_symmetric() {
        Node a = new Node();
        a.setNodeId("N1");
        a.setSequenceId(1);
        Node b = new Node();
        b.setNodeId("N1");
        b.setSequenceId(1);
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void node_different() {
        Node a = new Node();
        a.setNodeId("N1");
        a.setSequenceId(1);
        Node c = new Node();
        c.setNodeId("N2");
        c.setSequenceId(2);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void node_hashCodeConsistency() {
        Node a = new Node();
        a.setNodeId("N1");
        a.setSequenceId(1);
        Node b = new Node();
        b.setNodeId("N1");
        b.setSequenceId(1);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void node_nullSafety() {
        Node a = new Node();
        a.setNodeId("N1");
        a.setSequenceId(1);
        assertThat(a).isNotEqualTo(null);
    }

    // --- Edge ---

    @Test
    void edge_reflexive() {
        Edge a = new Edge();
        a.setEdgeId("E1");
        a.setSequenceId(1);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void edge_symmetric() {
        Edge a = new Edge();
        a.setEdgeId("E1");
        a.setSequenceId(1);
        Edge b = new Edge();
        b.setEdgeId("E1");
        b.setSequenceId(1);
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void edge_different() {
        Edge a = new Edge();
        a.setEdgeId("E1");
        a.setSequenceId(1);
        Edge c = new Edge();
        c.setEdgeId("E2");
        c.setSequenceId(2);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void edge_hashCodeConsistency() {
        Edge a = new Edge();
        a.setEdgeId("E1");
        a.setSequenceId(1);
        Edge b = new Edge();
        b.setEdgeId("E1");
        b.setSequenceId(1);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void edge_nullSafety() {
        Edge a = new Edge();
        a.setEdgeId("E1");
        a.setSequenceId(1);
        assertThat(a).isNotEqualTo(null);
    }

    // --- Action ---

    @Test
    void action_reflexive() {
        Action a = new Action();
        a.setActionId("A1");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void action_symmetric() {
        Action a = new Action();
        a.setActionId("A1");
        Action b = new Action();
        b.setActionId("A1");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void action_different() {
        Action a = new Action();
        a.setActionId("A1");
        Action c = new Action();
        c.setActionId("A2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void action_hashCodeConsistency() {
        Action a = new Action();
        a.setActionId("A1");
        Action b = new Action();
        b.setActionId("A1");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void action_nullSafety() {
        Action a = new Action();
        a.setActionId("A1");
        assertThat(a).isNotEqualTo(null);
    }

    // --- ActionState ---

    @Test
    void actionState_reflexive() {
        ActionState a = new ActionState();
        a.setActionId("AS1");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void actionState_symmetric() {
        ActionState a = new ActionState();
        a.setActionId("AS1");
        ActionState b = new ActionState();
        b.setActionId("AS1");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void actionState_different() {
        ActionState a = new ActionState();
        a.setActionId("AS1");
        ActionState c = new ActionState();
        c.setActionId("AS2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void actionState_hashCodeConsistency() {
        ActionState a = new ActionState();
        a.setActionId("AS1");
        ActionState b = new ActionState();
        b.setActionId("AS1");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void actionState_nullSafety() {
        ActionState a = new ActionState();
        a.setActionId("AS1");
        assertThat(a).isNotEqualTo(null);
    }

    // --- NodeState ---

    @Test
    void nodeState_reflexive() {
        NodeState a = new NodeState();
        a.setNodeId("NS1");
        a.setSequenceId(1);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void nodeState_symmetric() {
        NodeState a = new NodeState();
        a.setNodeId("NS1");
        a.setSequenceId(1);
        NodeState b = new NodeState();
        b.setNodeId("NS1");
        b.setSequenceId(1);
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void nodeState_different() {
        NodeState a = new NodeState();
        a.setNodeId("NS1");
        a.setSequenceId(1);
        NodeState c = new NodeState();
        c.setNodeId("NS2");
        c.setSequenceId(2);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void nodeState_hashCodeConsistency() {
        NodeState a = new NodeState();
        a.setNodeId("NS1");
        a.setSequenceId(1);
        NodeState b = new NodeState();
        b.setNodeId("NS1");
        b.setSequenceId(1);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void nodeState_nullSafety() {
        NodeState a = new NodeState();
        a.setNodeId("NS1");
        a.setSequenceId(1);
        assertThat(a).isNotEqualTo(null);
    }

    // --- EdgeState ---

    @Test
    void edgeState_reflexive() {
        EdgeState a = new EdgeState();
        a.setEdgeId("ES1");
        a.setSequenceId(1);
        assertThat(a).isEqualTo(a);
    }

    @Test
    void edgeState_symmetric() {
        EdgeState a = new EdgeState();
        a.setEdgeId("ES1");
        a.setSequenceId(1);
        EdgeState b = new EdgeState();
        b.setEdgeId("ES1");
        b.setSequenceId(1);
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void edgeState_different() {
        EdgeState a = new EdgeState();
        a.setEdgeId("ES1");
        a.setSequenceId(1);
        EdgeState c = new EdgeState();
        c.setEdgeId("ES2");
        c.setSequenceId(2);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void edgeState_hashCodeConsistency() {
        EdgeState a = new EdgeState();
        a.setEdgeId("ES1");
        a.setSequenceId(1);
        EdgeState b = new EdgeState();
        b.setEdgeId("ES1");
        b.setSequenceId(1);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void edgeState_nullSafety() {
        EdgeState a = new EdgeState();
        a.setEdgeId("ES1");
        a.setSequenceId(1);
        assertThat(a).isNotEqualTo(null);
    }

    // --- Error (com.navasmart.vda5050.model.Error) ---

    @Test
    void error_reflexive() {
        com.navasmart.vda5050.model.Error a = new com.navasmart.vda5050.model.Error();
        a.setErrorType("ET");
        a.setErrorDescription("ED");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void error_symmetric() {
        com.navasmart.vda5050.model.Error a = new com.navasmart.vda5050.model.Error();
        a.setErrorType("ET");
        a.setErrorDescription("ED");
        com.navasmart.vda5050.model.Error b = new com.navasmart.vda5050.model.Error();
        b.setErrorType("ET");
        b.setErrorDescription("ED");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void error_different() {
        com.navasmart.vda5050.model.Error a = new com.navasmart.vda5050.model.Error();
        a.setErrorType("ET");
        a.setErrorDescription("ED");
        com.navasmart.vda5050.model.Error c = new com.navasmart.vda5050.model.Error();
        c.setErrorType("ET2");
        c.setErrorDescription("ED2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void error_hashCodeConsistency() {
        com.navasmart.vda5050.model.Error a = new com.navasmart.vda5050.model.Error();
        a.setErrorType("ET");
        a.setErrorDescription("ED");
        com.navasmart.vda5050.model.Error b = new com.navasmart.vda5050.model.Error();
        b.setErrorType("ET");
        b.setErrorDescription("ED");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void error_nullSafety() {
        com.navasmart.vda5050.model.Error a = new com.navasmart.vda5050.model.Error();
        a.setErrorType("ET");
        a.setErrorDescription("ED");
        assertThat(a).isNotEqualTo(null);
    }

    // --- ActionParameter ---

    @Test
    void actionParameter_reflexive() {
        ActionParameter a = new ActionParameter();
        a.setKey("K1");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void actionParameter_symmetric() {
        ActionParameter a = new ActionParameter();
        a.setKey("K1");
        ActionParameter b = new ActionParameter();
        b.setKey("K1");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void actionParameter_different() {
        ActionParameter a = new ActionParameter();
        a.setKey("K1");
        ActionParameter c = new ActionParameter();
        c.setKey("K2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void actionParameter_hashCodeConsistency() {
        ActionParameter a = new ActionParameter();
        a.setKey("K1");
        ActionParameter b = new ActionParameter();
        b.setKey("K1");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void actionParameter_nullSafety() {
        ActionParameter a = new ActionParameter();
        a.setKey("K1");
        assertThat(a).isNotEqualTo(null);
    }

    // --- ErrorReference ---

    @Test
    void errorReference_reflexive() {
        ErrorReference a = new ErrorReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void errorReference_symmetric() {
        ErrorReference a = new ErrorReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        ErrorReference b = new ErrorReference();
        b.setReferenceKey("RK");
        b.setReferenceValue("RV");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void errorReference_different() {
        ErrorReference a = new ErrorReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        ErrorReference c = new ErrorReference();
        c.setReferenceKey("RK2");
        c.setReferenceValue("RV2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void errorReference_hashCodeConsistency() {
        ErrorReference a = new ErrorReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        ErrorReference b = new ErrorReference();
        b.setReferenceKey("RK");
        b.setReferenceValue("RV");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void errorReference_nullSafety() {
        ErrorReference a = new ErrorReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        assertThat(a).isNotEqualTo(null);
    }

    // --- InfoReference ---

    @Test
    void infoReference_reflexive() {
        InfoReference a = new InfoReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void infoReference_symmetric() {
        InfoReference a = new InfoReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        InfoReference b = new InfoReference();
        b.setReferenceKey("RK");
        b.setReferenceValue("RV");
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
    }

    @Test
    void infoReference_different() {
        InfoReference a = new InfoReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        InfoReference c = new InfoReference();
        c.setReferenceKey("RK2");
        c.setReferenceValue("RV2");
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void infoReference_hashCodeConsistency() {
        InfoReference a = new InfoReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        InfoReference b = new InfoReference();
        b.setReferenceKey("RK");
        b.setReferenceValue("RV");
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void infoReference_nullSafety() {
        InfoReference a = new InfoReference();
        a.setReferenceKey("RK");
        a.setReferenceValue("RV");
        assertThat(a).isNotEqualTo(null);
    }
}
