# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn clean compile          # Build (no tests yet)
mvn clean compile -q       # Quiet build
mvn clean compile -Dmaven.compiler.showDeprecation=true  # Show deprecation warnings
```

No tests exist yet. The project uses `spring-boot-starter-test` (JUnit 5) for future tests.

## Project Overview

VDA5050 Spring Boot Starter — a Java/Spring Boot library (not an application) implementing the VDA5050 AGV communication protocol. Provides two independent modes activated via config:
- **Proxy mode** (`vda5050.proxy.enabled=true`): AGV-side agent receiving orders from an external VDA5050 dispatcher, translating via callbacks to the user's FMS
- **Server mode** (`vda5050.server.enabled=true`): Master control sending orders to VDA5050-compliant AGVs and tracking their state

## Tech Stack

- Java 17, Spring Boot 4.0.5 (parent POM)
- Eclipse Paho MQTT v3 (1.2.5), Jackson 3.x
- Maven build, packaged as a library (`spring-boot-maven-plugin` skip=true)
- Package root: `com.navasmart.vda5050`

## Architecture

69 Java source files across `model/`, `mqtt/`, `proxy/`, `server/`, `vehicle/`, `error/`, `util/`, `autoconfigure/`.

### Auto-Configuration (3 classes in `autoconfigure/`)
- `Vda5050AutoConfiguration`: shared beans — ObjectMapper (NON_NULL, ignore unknown), MqttClient, MqttGateway, MqttInboundRouter, VehicleRegistry, ErrorAggregator. Enables `@EnableScheduling`.
- `Vda5050ProxyAutoConfiguration`: `@ConditionalOnProperty(vda5050.proxy.enabled=true)`. Requires user-provided `Vda5050ProxyVehicleAdapter` and `Vda5050ProxyStateProvider` beans. Wires MQTT order/instantActions handlers to ProxyOrderStateMachine.
- `Vda5050ServerAutoConfiguration`: `@ConditionalOnProperty(vda5050.server.enabled=true)`. Requires user-provided `Vda5050ServerAdapter` bean. Wires MQTT state/connection/factsheet handlers to AgvStateTracker and ServerConnectionMonitor.

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Data Models (`model/`, 35 files)
28 POJOs + 7 enums covering all VDA5050 message types. All use `@JsonInclude(NON_NULL)`. Enum fields in POJOs use String type for JSON compatibility; enum classes provide `@JsonValue`/`@JsonCreator`. Note: `com.navasmart.vda5050.model.Error` conflicts with `java.lang.Error` — use fully qualified name when both are in scope.

### MQTT Layer (`mqtt/`, 4 files)
- `MqttTopicResolver`: builds topic paths `{interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}`
- `MqttGateway`: publish with QoS/retain. Proxy publishes state/connection/factsheet; Server publishes order/instantActions.
- `MqttInboundRouter`: implements `MqttCallback`, routes by topic suffix to registered `BiConsumer` handlers
- `MqttConnectionManager`: lifecycle management, auto-reconnect, LWT setup, topic subscriptions at `@PostConstruct`

### Proxy Mode (`proxy/`, 12 files)
- **User implements**: `Vda5050ProxyVehicleAdapter` (onNavigate, onActionExecute returning CompletableFuture, onPause/onResume/onOrderCancel) and `Vda5050ProxyStateProvider` (getVehicleStatus, getFactsheet). Optionally implement `ActionHandler` for custom action types.
- `ProxyOrderStateMachine`: receives Order/InstantActions, manages IDLE→RUNNING↔PAUSED transitions, handles built-in actions (cancelOrder, startPause, stopPause, factsheetRequest)
- `ProxyOrderExecutor`: `@Scheduled(fixedDelay=200ms)` loop iterating proxy vehicles, processing nodes with BlockingType dispatch (HARD=exclusive, SOFT=no driving, NONE=parallel)
- `ProxyHeartbeatScheduler`: `@Scheduled` publishes AgvState per vehicle, merging FMS status from StateProvider
- `ProxyConnectionPublisher`: publishes ONLINE at `@PostConstruct`, OFFLINE at `@PreDestroy`

### Server Mode (`server/`, 9 files)
- **User implements**: `Vda5050ServerAdapter` (onStateUpdate required; onNodeReached, onOrderCompleted, onOrderFailed, etc. optional defaults)
- **User calls**: `OrderDispatcher.sendOrder()`, `InstantActionSender.send()` / `.cancelOrder()` / `.pauseVehicle()` etc.
- `AgvStateTracker`: detects node reached (lastNodeId change), action state changes, order completion (nodeStates empty + !driving + all actions terminal), error changes
- `OrderProgressTracker`: query current progress (completed nodes/actions, completion percent)
- `ServerConnectionMonitor`: `@Scheduled` timeout detection, processes Connection messages

### Thread Safety (`vehicle/`, 2 files)
- `VehicleContext`: per-vehicle state container with `ReentrantLock`. Holds proxy state (agvState, currentOrder, clientState, nodeIndex) and server state (lastReceivedState, lastSentOrder, connectionState). AtomicInteger header ID generators.
- `VehicleRegistry`: `ConcurrentHashMap<String, VehicleContext>`, key format `"{manufacturer}:{serialNumber}"`. Initialized from config at `@PostConstruct`.
- Lock rule: acquire per-vehicle lock for state mutations; release before I/O (MQTT publish).

## Key Design Decisions

- MQTT QoS 0 for all topics except `/connection` (QoS 1, retained) which uses LWT for disconnect detection
- Orders use VDA5050's horizon concept: nodes/edges have `released` flag distinguishing committed vs preview segments
- Error aggregation in Proxy mode: WARNING (non-fatal) vs FATAL (triggers order abort)
- Order completion in Server mode: all nodes passed + driving=false + all actions terminal
- Action dispatch priority: built-in instant actions → registered ActionHandler → VehicleAdapter.onActionExecute() → mark FAILED

## Design Documentation

12 specification documents in `docs/` covering architecture, data models, MQTT communication, callback interfaces, state machine, action handler, server interfaces, order dispatch, multi-vehicle management, configuration, error handling, and integration guide.
