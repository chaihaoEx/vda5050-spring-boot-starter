# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VDA5050 Spring Boot Starter — a Java/Spring Boot library implementing the VDA5050 AGV communication protocol. Provides two independent modes:
- **Proxy mode**: AGV-side agent that receives orders from an external VDA5050 dispatcher and translates them via callback interfaces to the user's fleet management system
- **Server mode**: Master control that sends orders to VDA5050-compliant AGVs and tracks their state

Both modes share a common MQTT communication layer and data model. They can be enabled independently or together via configuration properties.

## Project Status

This project is currently in the **design/documentation phase**. The `docs/` directory contains 12 detailed specification documents covering architecture, data models, MQTT communication, callback interfaces, state machines, and configuration. Source code implementation has not yet begun.

## Target Tech Stack

- Java 17+, Spring Boot 4.x (compatible with 3.x)
- Eclipse Paho MQTT v3 (1.2.5), Jackson
- Maven build, JUnit 5 for testing
- Package root: `com.navasmart.vda5050`

## Architecture

### Dual-Mode Design
- Proxy and Server modes are activated via `@ConditionalOnProperty` (`vda5050.proxy.enabled` / `vda5050.server.enabled`)
- Auto-configuration classes: `Vda5050ProxyAutoConfiguration`, `Vda5050ServerAutoConfiguration`
- Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Shared Layer
- **Data models** (`model/`): 27 VDA5050 message POJOs (Order, AgvState, InstantActions, Connection, Factsheet, etc.)
- **MQTT** (`mqtt/`): `MqttGateway` (publish), `MqttInboundRouter` (subscribe/route), `MqttTopicResolver` (topic construction: `{interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}`)
- **Vehicle management** (`vehicle/`): `VehicleContext` (per-vehicle state with ReentrantLock), `VehicleRegistry` (concurrent map)

### Proxy Mode (`proxy/`)
- User implements `Vda5050ProxyVehicleAdapter` (navigation, action execution, pause/resume callbacks returning CompletableFuture)
- `ProxyOrderStateMachine`: states IDLE → RUNNING ↔ PAUSED
- `ProxyOrderExecutor`: 200ms execution loop processing nodes/actions
- Action scheduling respects BlockingType (HARD=exclusive, SOFT=no driving, NONE=parallel)
- `ProxyHeartbeatScheduler`: publishes AgvState at configurable interval (default 1s)

### Server Mode (`server/`)
- User implements `Vda5050ServerAdapter` (state updates, node reached, order completed/failed callbacks)
- `OrderDispatcher` / `InstantActionSender`: APIs for sending commands
- `AgvStateTracker`: detects state changes from AGV status messages
- `ServerConnectionMonitor`: tracks AGV timeouts

### Thread Safety
- All mutable state changes require per-vehicle ReentrantLock
- Lock ordering: MQTT inbound → vehicle lock; never do I/O while holding lock
- Key concurrent actors: MQTT threads, order executor loop, heartbeat scheduler, connection monitor

## Key Design Decisions

- MQTT QoS 0 for all topics except `/connection` (QoS 1, retained) which uses LWT for disconnect detection
- Orders use VDA5050's horizon concept: nodes/edges have `released` flag distinguishing committed vs preview segments
- Error aggregation in Proxy mode: WARNING (non-fatal) vs FATAL (triggers order abort after running actions complete)
- Order completion in Server mode detected when: all nodes passed, driving=false, all actions terminal
