package com.navasmart.vda5050.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navasmart.vda5050.model.enums.ActionStatus;
import com.navasmart.vda5050.model.enums.BlockingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for VDA5050 model serialization and deserialization using Jackson.
 *
 * <p>Uses the same ObjectMapper configuration as {@code Vda5050AutoConfiguration}
 * (NON_NULL inclusion, ignore unknown properties).</p>
 */
class ModelSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    @Test
    void orderRoundTrip() throws Exception {
        Order order = new Order();
        order.setHeaderId(1);
        order.setTimestamp("2024-01-01T12:00:00.000Z");
        order.setVersion("2.0.0");
        order.setManufacturer("TestMfg");
        order.setSerialNumber("AGV001");
        order.setOrderId("order-001");
        order.setOrderUpdateId(0);
        order.setZoneSetId("zone-A");

        Node node = new Node();
        node.setNodeId("node-1");
        node.setSequenceId(0);
        node.setReleased(true);

        Action action = new Action();
        action.setActionType("pick");
        action.setActionId("action-1");
        action.setBlockingType("HARD");
        node.setActions(List.of(action));
        order.setNodes(List.of(node));

        Edge edge = new Edge();
        edge.setEdgeId("edge-1");
        edge.setSequenceId(1);
        edge.setStartNodeId("node-1");
        edge.setEndNodeId("node-2");
        edge.setReleased(true);
        order.setEdges(List.of(edge));

        String json = mapper.writeValueAsString(order);
        Order deserialized = mapper.readValue(json, Order.class);

        assertThat(deserialized.getHeaderId()).isEqualTo(1);
        assertThat(deserialized.getTimestamp()).isEqualTo("2024-01-01T12:00:00.000Z");
        assertThat(deserialized.getVersion()).isEqualTo("2.0.0");
        assertThat(deserialized.getManufacturer()).isEqualTo("TestMfg");
        assertThat(deserialized.getSerialNumber()).isEqualTo("AGV001");
        assertThat(deserialized.getOrderId()).isEqualTo("order-001");
        assertThat(deserialized.getOrderUpdateId()).isEqualTo(0);
        assertThat(deserialized.getZoneSetId()).isEqualTo("zone-A");
        assertThat(deserialized.getNodes()).hasSize(1);
        assertThat(deserialized.getNodes().get(0).getNodeId()).isEqualTo("node-1");
        assertThat(deserialized.getNodes().get(0).getActions()).hasSize(1);
        assertThat(deserialized.getNodes().get(0).getActions().get(0).getActionType()).isEqualTo("pick");
        assertThat(deserialized.getEdges()).hasSize(1);
        assertThat(deserialized.getEdges().get(0).getEdgeId()).isEqualTo("edge-1");
    }

    @Test
    void agvStateRoundTrip() throws Exception {
        AgvState state = new AgvState();
        state.setHeaderId(42);
        state.setTimestamp("2024-06-15T08:30:00.000Z");
        state.setVersion("2.0.0");
        state.setManufacturer("TestMfg");
        state.setSerialNumber("AGV001");
        state.setOrderId("order-001");
        state.setLastNodeId("node-5");
        state.setLastNodeSequenceId(4);
        state.setDriving(true);
        state.setOperatingMode("AUTOMATIC");

        AgvPosition position = new AgvPosition();
        position.setX(10.5);
        position.setY(20.3);
        position.setTheta(1.57);
        position.setMapId("map-01");
        position.setPositionInitialized(true);
        state.setAgvPosition(position);

        BatteryState battery = new BatteryState();
        battery.setBatteryCharge(85.5);
        battery.setBatteryVoltage(48.2);
        battery.setCharging(false);
        state.setBatteryState(battery);

        ActionState actionState = new ActionState();
        actionState.setActionId("action-1");
        actionState.setActionType("pick");
        actionState.setActionStatus("RUNNING");
        state.setActionStates(List.of(actionState));

        String json = mapper.writeValueAsString(state);
        AgvState deserialized = mapper.readValue(json, AgvState.class);

        assertThat(deserialized.getHeaderId()).isEqualTo(42);
        assertThat(deserialized.getTimestamp()).isEqualTo("2024-06-15T08:30:00.000Z");
        assertThat(deserialized.getManufacturer()).isEqualTo("TestMfg");
        assertThat(deserialized.getOrderId()).isEqualTo("order-001");
        assertThat(deserialized.isDriving()).isTrue();
        assertThat(deserialized.getAgvPosition().getX()).isEqualTo(10.5);
        assertThat(deserialized.getAgvPosition().getY()).isEqualTo(20.3);
        assertThat(deserialized.getAgvPosition().getMapId()).isEqualTo("map-01");
        assertThat(deserialized.getBatteryState().getBatteryCharge()).isEqualTo(85.5);
        assertThat(deserialized.getBatteryState().isCharging()).isFalse();
        assertThat(deserialized.getActionStates()).hasSize(1);
        assertThat(deserialized.getActionStates().get(0).getActionStatus()).isEqualTo("RUNNING");
    }

    @Test
    void nullFieldsExcludedFromJson() throws Exception {
        Order order = new Order();
        order.setOrderId("order-002");
        order.setOrderUpdateId(1);
        // zoneSetId is not set, so it should be null and excluded

        String json = mapper.writeValueAsString(order);

        assertThat(json).contains("\"orderId\"");
        assertThat(json).doesNotContain("zoneSetId");
    }

    @Test
    void blockingTypeEnumSerialization() throws Exception {
        String json = mapper.writeValueAsString(BlockingType.HARD);
        assertThat(json).isEqualTo("\"HARD\"");

        BlockingType deserialized = mapper.readValue("\"HARD\"", BlockingType.class);
        assertThat(deserialized).isEqualTo(BlockingType.HARD);
    }

    @Test
    void blockingTypeEnumAllValues() throws Exception {
        for (BlockingType type : BlockingType.values()) {
            String json = mapper.writeValueAsString(type);
            BlockingType roundTripped = mapper.readValue(json, BlockingType.class);
            assertThat(roundTripped).isEqualTo(type);
        }
    }

    @Test
    void actionStatusEnumSerialization() throws Exception {
        String json = mapper.writeValueAsString(ActionStatus.RUNNING);
        assertThat(json).isEqualTo("\"RUNNING\"");

        ActionStatus deserialized = mapper.readValue("\"FINISHED\"", ActionStatus.class);
        assertThat(deserialized).isEqualTo(ActionStatus.FINISHED);
        assertThat(deserialized.isTerminal()).isTrue();
    }

    @Test
    void unknownFieldToleranceDuringDeserialization() {
        String jsonWithExtraField = """
                {
                    "orderId": "order-003",
                    "orderUpdateId": 0,
                    "unknownField": "should be ignored",
                    "anotherUnknown": 42
                }
                """;

        assertThatCode(() -> mapper.readValue(jsonWithExtraField, Order.class))
                .doesNotThrowAnyException();
    }
}
