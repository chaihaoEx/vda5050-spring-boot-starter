# MQTT 通信层

MQTT 通信层是 Proxy 和 Server 两种模式的**共享基础设施**，负责消息的收发、Topic 路由和连接管理。

包路径：`com.navasmart.vda5050.mqtt`

---

## 1. Topic 结构

```
{interfaceName}/{majorVersion}/{manufacturer}/{serialNumber}/{topicName}
```

示例：`uagv/v2/MyCompany/forklift01/order`

### 1.1 Topic 与模式方向对照

| Topic 后缀 | 消息类型 | Proxy 模式 | Server 模式 |
|-----------|---------|-----------|------------|
| `/order` | Order | 订阅 ← | 发布 → |
| `/instantActions` | InstantActions | 订阅 ← | 发布 → |
| `/state` | AgvState | 发布 → | 订阅 ← |
| `/connection` | Connection | 发布 → (QoS=1, retain) | 订阅 ← |
| `/factsheet` | Factsheet | 发布 → | 订阅 ← |

---

## 2. 共享组件

### 2.1 MqttTopicResolver

为两种模式构建 Topic 路径：

```java
@Component
public class MqttTopicResolver {

    @Autowired
    private Vda5050Properties properties;

    public String buildPrefix(String manufacturer, String serialNumber) {
        return String.format("%s/%s/%s/%s",
            properties.getMqtt().getInterfaceName(),
            properties.getMqtt().getMajorVersion(),
            manufacturer, serialNumber);
    }

    // 各 Topic 的便捷方法
    public String orderTopic(String mfr, String sn) { return buildPrefix(mfr, sn) + "/order"; }
    public String instantActionsTopic(String mfr, String sn) { return buildPrefix(mfr, sn) + "/instantActions"; }
    public String stateTopic(String mfr, String sn) { return buildPrefix(mfr, sn) + "/state"; }
    public String connectionTopic(String mfr, String sn) { return buildPrefix(mfr, sn) + "/connection"; }
    public String factsheetTopic(String mfr, String sn) { return buildPrefix(mfr, sn) + "/factsheet"; }

    /** 从 Topic 路径提取 manufacturer 和 serialNumber */
    public VehicleIdentifier extractVehicleId(String topic) {
        String[] parts = topic.split("/");
        return new VehicleIdentifier(parts[2], parts[3]);
    }

    public String extractTopicSuffix(String topic) {
        String[] parts = topic.split("/");
        return parts[parts.length - 1];
    }
}
```

### 2.2 MqttGateway — 出站发布

基于 Eclipse Paho MQTT v3 客户端直接实现（与 FMS 项目技术栈一致）：

```java
@Component
public class MqttGateway {

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final MqttTopicResolver topicResolver;

    public void publish(String topic, Object payload, int qos, boolean retained) {
        String json = objectMapper.writeValueAsString(payload);
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(retained);
        mqttClient.publish(topic, message);
    }

    // ===== Proxy 模式便捷方法 =====
    public void publishState(String mfr, String sn, AgvState state) {
        publish(topicResolver.stateTopic(mfr, sn), state, 0, false);
    }
    public void publishConnection(String mfr, String sn, Connection conn) {
        publish(topicResolver.connectionTopic(mfr, sn), conn, 1, true);
    }
    public void publishFactsheet(String mfr, String sn, Factsheet fs) {
        publish(topicResolver.factsheetTopic(mfr, sn), fs, 0, false);
    }

    // ===== Server 模式便捷方法 =====
    public void publishOrder(String mfr, String sn, Order order) {
        publish(topicResolver.orderTopic(mfr, sn), order, 0, false);
    }
    public void publishInstantActions(String mfr, String sn, InstantActions actions) {
        publish(topicResolver.instantActionsTopic(mfr, sn), actions, 0, false);
    }
}
```

### 2.3 MqttInboundRouter — 入站路由

基于 Paho 的 `MqttCallback` 实现，根据 Topic 后缀分发消息：

```java
@Component
public class MqttInboundRouter implements MqttCallback {

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        String suffix = topicResolver.extractTopicSuffix(topic);
        VehicleIdentifier vid = topicResolver.extractVehicleId(topic);

        switch (suffix) {
            // Proxy 模式订阅的 Topic
            case "order":
                if (proxyEnabled) proxyHandler.handleOrder(vid, payload);
                break;
            case "instantActions":
                if (proxyEnabled) proxyHandler.handleInstantActions(vid, payload);
                break;

            // Server 模式订阅的 Topic
            case "state":
                if (serverEnabled) serverHandler.handleState(vid, payload);
                break;
            case "connection":
                if (serverEnabled) serverHandler.handleConnection(vid, payload);
                break;
            case "factsheet":
                if (serverEnabled) serverHandler.handleFactsheet(vid, payload);
                break;
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开: {}", cause.getMessage());
        // Paho 自动重连（需配置 automaticReconnect=true）
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 发布完成回调
    }
}
```

---

## 3. Proxy 模式的 MQTT 行为

### 3.1 订阅

启动时为每辆 Proxy 车辆订阅：
- `{prefix}/order`
- `{prefix}/instantActions`

### 3.2 发布

- `{prefix}/state` — 定时发布（默认 1 秒）
- `{prefix}/connection` — 上线 ONLINE、下线 OFFLINE（QoS=1, retain=true）
- `{prefix}/factsheet` — 收到 factsheetRequest 时发布

### 3.3 LWT（Last Will Testament）

连接时设置遗嘱消息 `connectionState=CONNECTIONBROKEN`，断线时 Broker 自动发布。

---

## 4. Server 模式的 MQTT 行为

### 4.1 订阅

启动时为每辆 Server 管理的车辆订阅：
- `{prefix}/state`
- `{prefix}/connection`
- `{prefix}/factsheet`

### 4.2 发布

- `{prefix}/order` — 业务系统调用 `OrderDispatcher.sendOrder()` 时发布
- `{prefix}/instantActions` — 调用 `InstantActionSender.send()` 时发布

### 4.3 连接监控

Server 监听 `{prefix}/connection` Topic：
- 收到 `ONLINE` → 标记车辆在线
- 收到 `CONNECTIONBROKEN` → 标记车辆断连
- 长时间未收到 `state` → 判定车辆超时

---

## 5. MQTT 配置

```yaml
vda5050:
  mqtt:
    host: localhost
    port: 1883
    transport: tcp
    keepAlive: 60
    username: ""
    password: ""
    cleanSession: true
    interfaceName: uagv
    majorVersion: v2
    protocolVersion: "2.0.0"
```

---

## 6. JSON 序列化

```java
@Bean
public ObjectMapper vda5050ObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
}
```

---

## 7. 重连与容错

| 场景 | 处理方式 |
|------|---------|
| Broker 断连 | Paho 自动重连，重连后重新订阅 |
| 消息反序列化失败 | 记录日志，丢弃，不影响其他消息 |
| 未注册车辆的消息 | 记录警告，忽略 |
| 发布失败 | 异步发布，失败记录日志 |
