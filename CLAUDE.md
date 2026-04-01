# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./mvnw clean compile                    # Build
./mvnw verify                           # Build + test + SpotBugs + Checkstyle + JaCoCo
./mvnw test -pl . -Dtest=TimestampUtilTest  # Run single test class
mvn verify --batch-mode --no-transfer-progress  # CI-style (if mvnw download fails in China)
```

97 tests (unit + integration) across 23 files in `src/test/java/`. JUnit 5 via `spring-boot-starter-test`. Integration tests use Moquette embedded MQTT broker (`io.moquette:moquette-broker:0.17`, test scope).

### Test Categories
- **Unit tests**: `TimestampUtilTest`, `Vda5050PropertiesTest`, `MqttTopicResolverTest`, `ModelSerializationTest`, `ErrorHandlingTest`
- **Auto-config tests**: `AutoConfigurationTest`, `ProxyAutoConfigurationTest`, `ServerAutoConfigurationTest`, `ConfigValidationTest` (use `ApplicationContextRunner`)
- **Integration tests**: `ProxyOrderFlowTest`, `ProxyActionHandlerTest`, `ServerOrderDispatchTest`, `ServerStateTrackingTest`, `GracefulShutdownTest` (use `@SpringBootTest` + embedded broker)
- **Feature tests**: `OrderValidatorTest`, `SslConfigTest`, `MqttHealthIndicatorTest`, `MalformedMessageTest`, `DynamicVehicleRegistrationTest`, `AdapterCallbackExceptionTest`
- **Test infra**: `EmbeddedMqttBroker`, `MockProxyAdapter`, `MockServerAdapter` in `test/` package

## CI

GitHub Actions workflow at `.github/workflows/ci.yml` — triggers on push/PR to main.
Runs: compile → test → JaCoCo coverage → SpotBugs analysis → Checkstyle → upload JAR artifact.
SpotBugs and Checkstyle are **blocking** (`failOnError=true` / `failOnViolation=true`).

## Gotchas

- `com.navasmart.vda5050.model.Error` conflicts with `java.lang.Error` — use FQN when both in scope
- Jackson `setSerializationInclusion()` deprecated in Spring Boot 4.x — use `setDefaultPropertyInclusion()`
- Aliyun Maven mirror may lag behind Maven Central — check available versions before pinning
- Maven Wrapper may fail to download in China network — fall back to local `mvn`

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

~100 Java source files across `model/`, `mqtt/`, `proxy/`, `server/`, `vehicle/`, `error/`, `util/`, `autoconfigure/`, `event/`, `actuator/`.

### Auto-Configuration (4 classes in `autoconfigure/`)
- `Vda5050AutoConfiguration`: shared beans — ObjectMapper (NON_NULL, ignore unknown), MqttClient, MqttGateway, MqttInboundRouter, VehicleRegistry, ErrorAggregator. Enables `@EnableScheduling`.
- `Vda5050ProxyAutoConfiguration`: `@ConditionalOnProperty(vda5050.proxy.enabled=true)`. Requires user-provided `Vda5050ProxyVehicleAdapter` and `Vda5050ProxyStateProvider` beans. Wires MQTT order/instantActions handlers to ProxyOrderStateMachine.
- `Vda5050ServerAutoConfiguration`: `@ConditionalOnProperty(vda5050.server.enabled=true)`. Requires user-provided `Vda5050ServerAdapter` bean. Wires MQTT state/connection/factsheet handlers to AgvStateTracker and ServerConnectionMonitor.
- `Vda5050ActuatorAutoConfiguration`: `@ConditionalOnClass(HealthIndicator.class)`. Registers `MqttHealthIndicator` for Spring Boot Actuator.

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Data Models (`model/`, 35 files)
28 POJOs + 7 enums covering all VDA5050 message types. All use `@JsonInclude(NON_NULL)`. Enum fields in POJOs use String type for JSON compatibility; enum classes provide `@JsonValue`/`@JsonCreator`. Note: `com.navasmart.vda5050.model.Error` conflicts with `java.lang.Error` — use fully qualified name when both are in scope.

### MQTT Layer (`mqtt/`, 5 files)
- `MqttTopicResolver`: builds topic paths `{interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}`
- `MqttGateway`: publish with QoS/retain. Proxy publishes state/connection/factsheet via per-vehicle client; Server publishes order/instantActions via shared client. `resolveProxyClient()` picks the right client. Per-vehicle rate limiting via `ConcurrentHashMap.compute()`.
- `MqttInboundRouter`: implements `MqttCallbackExtended`, routes by topic suffix to registered `BiConsumer` handlers. Supports reconnect listeners for auto re-subscribe.
- `MqttConnectionManager`: multi-client architecture — each Proxy vehicle gets a dedicated `MqttClient` (stored in `VehicleContext.proxyMqttClient`) with its own LWT; Server mode uses a shared `MqttClient`. Reconnect via `MqttCallbackExtended.connectComplete()` auto re-subscribes; circuit breaker via `maxReconnectAttempts`.
- `SslUtil`: creates `SSLSocketFactory` from configurable keystore/truststore for MQTT SSL/TLS connections

### Proxy Mode (`proxy/`, 12 files)
- **User implements**: `Vda5050ProxyVehicleAdapter` (onNavigate, onActionExecute returning CompletableFuture, onPause/onResume/onOrderCancel) and `Vda5050ProxyStateProvider` (getVehicleStatus, getFactsheet). Optionally implement `ActionHandler` for custom action types.
- `ProxyOrderStateMachine`: receives Order/InstantActions, manages IDLE→RUNNING↔PAUSED transitions, handles built-in actions (cancelOrder, startPause, stopPause, factsheetRequest)
- `ProxyOrderExecutor`: `@Scheduled(fixedDelay=200ms)` loop iterating proxy vehicles, processing nodes with BlockingType dispatch (HARD=exclusive, SOFT=no driving, NONE=parallel). Enforces navigation timeout (`navigationTimeoutMs`) and action timeout (`actionTimeoutMs`). Starts edge actions when traversing edges. Graceful shutdown via `shutdown()` + `isIdle()`.
- `ProxyHeartbeatScheduler`: `@Scheduled` publishes AgvState per vehicle, merging FMS status from StateProvider
- `ProxyConnectionPublisher`: publishes ONLINE at `@PostConstruct`, OFFLINE at `@PreDestroy` with graceful drain
- `OrderValidator`: validates incoming orders (orderId, node/edge sequence consistency, orderUpdateId monotonicity)

### Server Mode (`server/`, 9 files)
- **User implements**: `Vda5050ServerAdapter` (onStateUpdate required; onNodeReached, onOrderCompleted, onOrderFailed, etc. optional defaults)
- **User calls**: `OrderDispatcher.sendOrder()`, `InstantActionSender.send()` / `.cancelOrder()` / `.pauseVehicle()` etc.
- `AgvStateTracker`: detects node reached (lastNodeId change), action state changes, order completion (nodeStates empty + !driving + all actions terminal), error changes
- `OrderProgressTracker`: query current progress (completed nodes/actions, completion percent)
- `ServerConnectionMonitor`: `@Scheduled` timeout detection, processes Connection messages

### Event System (`event/`, 9 files)
8 Spring `ApplicationEvent` types: `OrderReceivedEvent`, `OrderCompletedEvent`, `OrderFailedEvent`, `NodeReachedEvent`, `ConnectionStateChangedEvent`, `ErrorOccurredEvent`, `VehicleTimeoutEvent`. Base class `Vda5050Event` with vehicleId.

### Thread Safety (`vehicle/`, 2 files)
- `VehicleContext`: per-vehicle state container with `ReentrantLock`. Holds proxy state (agvState, currentOrder, clientState, nodeIndex, proxyMqttClient, navigationStartTime, actionStartTimes) and server state (lastReceivedState, lastSentOrder, connectionState). AtomicInteger header ID generators.
- `VehicleRegistry`: `ConcurrentHashMap<String, VehicleContext>`, key format `"{manufacturer}:{serialNumber}"`. Initialized from config at `@PostConstruct`. Supports runtime `registerVehicle()`/`unregisterVehicle()` (callers must also coordinate MQTT via `MqttConnectionManager`).
- Lock rule: acquire per-vehicle lock for state mutations; release before I/O (MQTT publish), Spring event publishing (`publishEvent()`), and adapter callbacks. Pattern: collect events/callbacks inside lock, execute after unlock.

## Key Design Decisions

- MQTT QoS 0 for all topics except `/connection` (QoS 1, retained) which uses LWT for disconnect detection. Each Proxy vehicle has a dedicated MqttClient with its own LWT, solving the Paho single-LWT limitation.
- Orders use VDA5050's horizon concept: nodes/edges have `released` flag distinguishing committed vs preview segments
- Error aggregation in Proxy mode: WARNING (non-fatal) vs FATAL (triggers order abort)
- Order completion in Proxy mode: all nodes traversed + all actions (including edge actions) in terminal state
- Order completion in Server mode: all nodes passed + driving=false + all actions terminal
- Action dispatch priority: built-in instant actions → registered ActionHandler → VehicleAdapter.onActionExecute() → mark FAILED
- Library dependency discipline: optional features (actuator, micrometer, validation) use `<optional>true</optional>` in pom.xml
- SSL/TLS: `vda5050.mqtt.ssl.*` config with keystore/truststore, auto scheme resolution (tcp→ssl, ws→wss)
- Config validation: `@Validated` + Jakarta Bean Validation on `Vda5050Properties` (active when validator on classpath)

## Design Documentation

15 documents in `docs/` covering architecture, data models, MQTT communication, callback interfaces, state machine, action handler, server interfaces, order dispatch, multi-vehicle management, configuration, error handling, integration guide, proxy quick start, improvement roadmap, and security guide.
