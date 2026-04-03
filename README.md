# VDA5050 Spring Boot Starter

[![CI](https://github.com/chaihaoEx/vda5050-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/chaihaoEx/vda5050-spring-boot-starter/actions/workflows/ci.yml)

A Spring Boot Starter for the [VDA5050](https://github.com/VDA5050/VDA5050) AGV communication protocol. Drop in the dependency, implement a few callback interfaces, and your Spring Boot application speaks VDA5050 over MQTT — no protocol plumbing required.

## Features

- **Proxy mode** — Bridge between an external VDA5050 dispatcher and your existing fleet
- **Server mode** — Act as a VDA5050-compliant master control for standard AGVs
- **Both modes** can run independently or simultaneously in the same application
- Full VDA5050 v2 message model (28 POJOs + 7 enums)
- Per-vehicle dedicated MQTT clients with LWT-based disconnect detection
- Order state machine with BlockingType action dispatch (HARD / SOFT / NONE)
- Thread-safe dual-lock architecture (proxy lock + server lock per vehicle)
- Spring Boot Actuator health indicator with per-vehicle connection status
- SSL/TLS support with configurable keystore/truststore
- 242 tests (unit + integration with embedded MQTT broker)

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.navasmart</groupId>
    <artifactId>vda5050-spring-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

### 2. Configure

```yaml
vda5050:
  mqtt:
    host: mqtt.example.com
    port: 1883
  proxy:
    enabled: true
    vehicles:
      - manufacturer: MyCompany
        serialNumber: forklift01
  server:
    enabled: true
    vehicles:
      - manufacturer: ThirdParty
        serialNumber: agv01
```

### 3. Implement Callbacks

**Proxy mode** — receive VDA5050 orders and control your vehicles:

```java
@Component
public class MyVehicleAdapter implements Vda5050ProxyVehicleAdapter {
    @Override
    public CompletableFuture<NavigationResult> onNavigate(
            String vehicleId, Node targetNode, List<Node> waypoints, List<Edge> edges) {
        // Navigate your vehicle to the target node
        return CompletableFuture.completedFuture(NavigationResult.success());
    }

    @Override
    public CompletableFuture<ActionResult> onActionExecute(String vehicleId, Action action) {
        // Execute action (pick, drop, charge, etc.)
        return CompletableFuture.completedFuture(ActionResult.success());
    }

    @Override public void onPause(String vehicleId) { /* pause vehicle */ }
    @Override public void onResume(String vehicleId) { /* resume vehicle */ }
    @Override public void onOrderCancel(String vehicleId) { /* cancel current order */ }
    @Override public void onNavigationCancel(String vehicleId) { /* abort navigation */ }
    @Override public void onActionCancel(String vehicleId, String actionId) { /* cancel action */ }
}

@Component
public class MyStateProvider implements Vda5050ProxyStateProvider {
    @Override
    public VehicleStatus getVehicleStatus(String vehicleId) {
        // Return current vehicle state (position, battery, speed, etc.)
        return new VehicleStatus();
    }

    @Override
    public Factsheet getFactsheet(String vehicleId) {
        // Return vehicle capability description
        return new Factsheet();
    }
}
```

**Server mode** — dispatch orders and track AGV state:

```java
@Component
public class MyServerAdapter implements Vda5050ServerAdapter {
    @Override
    public void onStateUpdate(String vehicleId, AgvState state) {
        // Process AGV state updates
    }

    @Override
    public void onOrderCompleted(String vehicleId, String orderId) {
        // Order execution completed
    }

    @Override
    public void onOrderFailed(String vehicleId, String orderId, List<Error> errors) {
        // Order execution failed
    }
}

@Service
public class MyDispatchService {
    @Autowired private OrderDispatcher orderDispatcher;
    @Autowired private InstantActionSender actionSender;

    public void dispatch() {
        Order order = OrderBuilder.create("order-001")
            .addNode("node-1", 0.0, 0.0, 0.0, "map-1", true)
            .withAction("action-1", "pick", "HARD", Map.of("station", "S1"))
            .addEdge("edge-1", "node-1", "node-2", 1.5, true)
            .addNode("node-2", 10.0, 0.0, 0.0, "map-1", true)
            .build();

        SendResult result = orderDispatcher.sendOrder("ThirdParty:agv01", order);
        // result.isSuccess() / result.getFailureReason()
    }

    public void cancelOrder(String vehicleId) {
        actionSender.cancelOrder(vehicleId);
    }
}
```

## Architecture

```
                    Proxy Mode                                Server Mode
          ┌─────────────────────────┐              ┌─────────────────────────┐
          │  External VDA5050       │              │  Your Dispatch System   │
          │  Dispatcher             │              │                         │
          └──────────┬──────────────┘              └──────────┬──────────────┘
                     │ MQTT                         API calls │
                     ▼                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  VDA5050 Spring Boot Starter                                                │
│                                                                             │
│  ┌─────────────────────────┐        ┌───────────────────────────────────┐  │
│  │ ProxyOrderStateMachine  │        │ OrderDispatcher                   │  │
│  │ ProxyOrderExecutor      │        │ InstantActionSender               │  │
│  │  ├ NodeActionDispatcher │        │ AgvStateTracker                   │  │
│  │  └ NavigationController │        │ OrderProgressTracker              │  │
│  │ ProxyHeartbeatScheduler │        │ ServerConnectionMonitor           │  │
│  └─────────────────────────┘        └───────────────────────────────────┘  │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ MQTT Layer: MqttGateway · MqttConnectionManager · MqttInboundRouter│    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ VehicleRegistry · VehicleContext (dual ReentrantLock per vehicle)   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                     │ MQTT                                   │ MQTT
                     ▼                                        ▼
          ┌─────────────────────────┐              ┌─────────────────────────┐
          │  Your Vehicles          │              │  VDA5050 AGVs           │
          │  (via callback)         │              │  (standard protocol)    │
          └─────────────────────────┘              └─────────────────────────┘
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `vda5050.mqtt.host` | `localhost` | MQTT broker address |
| `vda5050.mqtt.port` | `1883` | MQTT broker port |
| `vda5050.mqtt.transport` | `tcp` | Transport protocol (`tcp` / `websocket`) |
| `vda5050.mqtt.interfaceName` | `uagv` | VDA5050 topic interface name |
| `vda5050.mqtt.majorVersion` | `v2` | VDA5050 topic version |
| `vda5050.mqtt.ssl.enabled` | `false` | Enable SSL/TLS |
| `vda5050.mqtt.maxReconnectAttempts` | `0` | Max reconnect attempts (0 = unlimited) |
| `vda5050.proxy.enabled` | `false` | Enable Proxy mode |
| `vda5050.proxy.heartbeatIntervalMs` | `1000` | State heartbeat interval (ms) |
| `vda5050.proxy.orderLoopIntervalMs` | `200` | Order execution loop interval (ms) |
| `vda5050.proxy.navigationTimeoutMs` | `0` | Navigation timeout (0 = disabled) |
| `vda5050.proxy.actionTimeoutMs` | `0` | Action timeout (0 = disabled) |
| `vda5050.server.enabled` | `false` | Enable Server mode |
| `vda5050.server.stateTimeoutMs` | `60000` | AGV state timeout threshold (ms) |

See [Configuration Guide](docs/10-configuration-guide.md) for full reference.

## Spring Events

Subscribe to VDA5050 lifecycle events via `@EventListener`:

| Event | Description |
|-------|-------------|
| `OrderReceivedEvent` | New order received (proxy mode) |
| `OrderCompletedEvent` | Order execution completed |
| `OrderFailedEvent` | Order execution failed |
| `NodeReachedEvent` | Vehicle reached a node |
| `ConnectionStateChangedEvent` | Vehicle connection state changed |
| `ErrorOccurredEvent` | Vehicle reported an error |
| `VehicleTimeoutEvent` | Vehicle state message timeout |

## Actuator Health

With Spring Boot Actuator on the classpath, the `/actuator/health` endpoint includes MQTT connection status per vehicle, with reconnect attempt counters.

## Build & Test

```bash
./mvnw clean compile                         # Compile
./mvnw verify                                # Compile + test + SpotBugs + Checkstyle + JaCoCo
./mvnw test -Dtest=ProxyOrderFlowTest        # Run a single test class
```

242 tests (unit + integration). Integration tests use an embedded MQTT broker (Moquette) — no external dependencies needed.

## Tech Stack

- Java 17, Spring Boot 4.0.5
- Eclipse Paho MQTT v3 (1.2.5), Jackson 3.x
- Maven build, packaged as a library
- SpotBugs + Checkstyle enforced in CI

## Documentation

| Doc | Description |
|-----|-------------|
| [Architecture Overview](docs/01-architecture-overview.md) | Dual-mode architecture, module structure, data flow |
| [Data Model Reference](docs/02-data-model-reference.md) | 28 VDA5050 message types |
| [MQTT Communication](docs/03-mqtt-communication.md) | Topic structure, publish/subscribe, connection management |
| [Proxy Callback Interface](docs/04-proxy-callback-interface.md) | Interfaces your FMS implements |
| [Proxy Order State Machine](docs/05-proxy-order-state-machine.md) | Order execution engine |
| [Proxy Action Handler](docs/06-proxy-action-handler.md) | BlockingType dispatch and custom action handlers |
| [Server Interface Design](docs/07-server-interface-design.md) | Dispatch system interfaces and API |
| [Server Order Dispatch](docs/08-server-order-dispatch.md) | State tracking, progress query |
| [Multi-Vehicle Management](docs/09-multi-vehicle-management.md) | VehicleContext, thread safety, dual-lock design |
| [Configuration Guide](docs/10-configuration-guide.md) | Full YAML reference |
| [Error Handling](docs/11-error-handling.md) | Error classification and aggregation |
| [Integration Guide](docs/12-integration-guide.md) | Hands-on examples for both modes |
| [Proxy Quick Start](docs/13-proxy-quick-start.md) | 10-minute Proxy mode setup |
| [Security Guide](docs/15-security-guide.md) | SSL/TLS configuration |

## References

- [VDA5050/VDA5050](https://github.com/VDA5050/VDA5050) — Official VDA5050 protocol specification
- [NVIDIA Isaac ROS Cloud Control](https://github.com/NVIDIA-ISAAC-ROS/isaac_ros_cloud_control) — Reference VDA5050 client implementation (C++ / Python / ROS 2). This project's data model, order state machine, action dispatch logic and MQTT communication design were informed by this implementation.

## License

TBD
