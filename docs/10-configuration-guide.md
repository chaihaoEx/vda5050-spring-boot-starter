# 配置指南

---

## 1. 完整 application.yml

```yaml
vda5050:
  # ===== 共享 MQTT 配置 =====
  mqtt:
    host: mqtt.example.com
    port: 1883
    transport: tcp              # tcp / websocket
    keepAlive: 60
    username: ""
    password: ""
    cleanSession: true
    clientIdPrefix: vda5050
    interfaceName: uagv         # Topic 第一段
    majorVersion: v2            # Topic 第二段
    protocolVersion: "2.0.0"    # 消息中的 version 字段

  # ===== Proxy 模式配置 =====
  proxy:
    enabled: true               # 是否启用 Proxy 模式
    heartbeatIntervalMs: 1000   # AgvState 发布间隔
    orderLoopIntervalMs: 200    # 订单执行循环间隔
    navigationTimeoutMs: 300000 # 导航超时（5 分钟）
    actionTimeoutMs: 120000     # 动作超时（2 分钟）
    vehicles:
      - manufacturer: MyCompany
        serialNumber: forklift01
        robotType: FORKLIFT
      - manufacturer: MyCompany
        serialNumber: forklift02
        robotType: FORKLIFT

  # ===== Server 模式配置 =====
  server:
    enabled: true               # 是否启用 Server 模式
    stateTimeoutMs: 60000       # AGV 状态超时判定（60 秒）
    connectionCheckMs: 30000    # 连接检查间隔（30 秒）
    vehicles:
      - manufacturer: ThirdParty
        serialNumber: agv01
      - manufacturer: ThirdParty
        serialNumber: agv02
```

---

## 2. 配置项说明

### 2.1 MQTT 配置 (`vda5050.mqtt.*`)

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| host | String | localhost | Broker 地址 |
| port | int | 1883 | 端口 |
| transport | String | tcp | tcp / websocket |
| keepAlive | int | 60 | 心跳间隔（秒） |
| username | String | "" | 认证用户名 |
| password | String | "" | 认证密码 |
| cleanSession | boolean | true | 是否清除会话 |
| clientIdPrefix | String | vda5050 | 客户端 ID 前缀 |
| interfaceName | String | uagv | Topic 前缀 |
| majorVersion | String | v2 | 协议版本 |
| protocolVersion | String | "2.0.0" | 消息版本字符串 |

### 2.2 Proxy 配置 (`vda5050.proxy.*`)

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | false | 是否启用 Proxy 模式 |
| heartbeatIntervalMs | long | 1000 | AgvState 发布间隔 |
| orderLoopIntervalMs | long | 200 | 订单执行循环间隔 |
| navigationTimeoutMs | long | 300000 | 导航超时 |
| actionTimeoutMs | long | 120000 | 动作超时 |
| vehicles | List | [] | Proxy 管理的车辆列表 |

### 2.3 Server 配置 (`vda5050.server.*`)

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| enabled | boolean | false | 是否启用 Server 模式 |
| stateTimeoutMs | long | 60000 | AGV 状态超时判定 |
| connectionCheckMs | long | 30000 | 连接检查间隔 |
| vehicles | List | [] | Server 管理的车辆列表 |

### 2.4 车辆配置项 (`vehicles[]`)

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| manufacturer | String | 是 | 制造商（对应 MQTT Topic） |
| serialNumber | String | 是 | 序列号（对应 MQTT Topic） |
| robotType | String | 否 | 车辆类型，默认 FORKLIFT |

---

## 3. Spring Boot 自动配置

### 3.1 Vda5050Properties

```java
@Data
@ConfigurationProperties(prefix = "vda5050")
public class Vda5050Properties {
    private MqttConfig mqtt = new MqttConfig();
    private ProxyConfig proxy = new ProxyConfig();
    private ServerConfig server = new ServerConfig();

    @Data
    public static class ProxyConfig {
        private boolean enabled = false;
        private long heartbeatIntervalMs = 1000;
        private long orderLoopIntervalMs = 200;
        private long navigationTimeoutMs = 300000;
        private long actionTimeoutMs = 120000;
        private List<VehicleConfig> vehicles = new ArrayList<>();
    }

    @Data
    public static class ServerConfig {
        private boolean enabled = false;
        private long stateTimeoutMs = 60000;
        private long connectionCheckMs = 30000;
        private List<VehicleConfig> vehicles = new ArrayList<>();
    }
}
```

### 3.2 条件装配

```java
// Proxy 模式自动配置
@Configuration
@ConditionalOnProperty(prefix = "vda5050.proxy", name = "enabled", havingValue = "true")
public class Vda5050ProxyAutoConfiguration { ... }

// Server 模式自动配置
@Configuration
@ConditionalOnProperty(prefix = "vda5050.server", name = "enabled", havingValue = "true")
public class Vda5050ServerAutoConfiguration { ... }
```

### 3.3 自动配置注册

Spring Boot 4.x / 3.x 使用 `AutoConfiguration.imports`：

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.navasmart.vda5050.autoconfigure.Vda5050ProxyAutoConfiguration
com.navasmart.vda5050.autoconfigure.Vda5050ServerAutoConfiguration
```

---

## 4. 环境变量

```bash
# MQTT
export VDA5050_MQTT_HOST=mqtt.prod.com
export VDA5050_MQTT_PORT=8883
export VDA5050_MQTT_USERNAME=user
export VDA5050_MQTT_PASSWORD=pass

# Proxy
export VDA5050_PROXY_ENABLED=true
export VDA5050_PROXY_HEARTBEATINTERVALMS=1000

# Server
export VDA5050_SERVER_ENABLED=true
export VDA5050_SERVER_STATETIMEOUTMS=60000
```

---

## 5. 多环境 Profile

### 开发环境 (`application-dev.yml`)

```yaml
vda5050:
  mqtt:
    host: localhost
  proxy:
    enabled: true
    heartbeatIntervalMs: 5000
    vehicles:
      - manufacturer: Test
        serialNumber: test01
  server:
    enabled: false
```

### 生产环境 (`application-prod.yml`)

```yaml
vda5050:
  mqtt:
    host: mqtt.production.internal
    port: 8883
    username: ${MQTT_USER}
    password: ${MQTT_PASS}
  proxy:
    enabled: true
    vehicles: [...]
  server:
    enabled: true
    vehicles: [...]
```

---

## 6. 日志配置

```xml
<logger name="com.navasmart.vda5050" level="INFO"/>
<logger name="com.navasmart.vda5050.mqtt" level="DEBUG"/>
<logger name="com.navasmart.vda5050.proxy" level="INFO"/>
<logger name="com.navasmart.vda5050.server" level="INFO"/>
<logger name="org.eclipse.paho" level="WARN"/>
```
