# VDA5050 数据模型参考

本文档定义了 VDA5050 v2 协议中所有消息类型的 Java POJO 模型。**Proxy 和 Server 两种模式共享同一套数据模型**。

包路径：`com.navasmart.vda5050.model`

所有字段使用 **camelCase** 命名，Jackson 直接映射。

> 参考来源：`vda5050_msgs/msg/*.msg`（共 27 个消息定义）

---

## 1. 顶层消息

### 1.1 Order — 订单

- Proxy 模式：**接收**（从 MQTT 反序列化）
- Server 模式：**构建并发送**（序列化到 MQTT）

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| headerId | int | 是 | 消息头 ID，每个 topic 独立自增 |
| timestamp | String | 是 | ISO8601，如 `"2017-04-15T11:40:03.12Z"` |
| version | String | 是 | 协议版本，如 `"2.0.0"` |
| manufacturer | String | 是 | AGV 制造商 |
| serialNumber | String | 是 | AGV 序列号 |
| orderId | String | 是 | 订单唯一标识 |
| orderUpdateId | long | 是 | 订单更新 ID |
| zoneSetId | String | 否 | 区域集标识 |
| nodes | List\<Node\> | 是 | 节点数组（至少 1 个） |
| edges | List\<Edge\> | 是 | 边数组（可为空） |

### 1.2 AgvState — AGV 状态

- Proxy 模式：**构建并发布**
- Server 模式：**接收并解析**

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| headerId | int | 是 | 消息头 ID |
| timestamp | String | 是 | ISO8601 时间戳 |
| version | String | 是 | 协议版本 |
| manufacturer | String | 是 | 制造商 |
| serialNumber | String | 是 | 序列号 |
| orderId | String | 是 | 当前/上一个订单 ID（无则空字符串） |
| orderUpdateId | long | 是 | 订单更新 ID |
| zoneSetId | String | 否 | 区域集标识 |
| lastNodeId | String | 否 | 最后到达的节点 ID |
| lastNodeSequenceId | int | 是 | 最后节点的 sequenceId |
| nodeStates | List\<NodeState\> | 是 | 待遍历节点状态 |
| edgeStates | List\<EdgeState\> | 是 | 待遍历边状态 |
| agvPosition | AgvPosition | 否 | 当前位置 |
| velocity | Velocity | 是 | 当前速度 |
| loads | List\<Load\> | 否 | 当前载荷 |
| driving | boolean | 是 | 是否正在行驶/旋转 |
| paused | boolean | 是 | 是否暂停 |
| newBaseRequested | boolean | 是 | 是否需要新 base |
| distanceSinceLastNode | double | 是 | 自上个节点行驶距离（米） |
| actionStates | List\<ActionState\> | 是 | 动作状态列表 |
| batteryState | BatteryState | 是 | 电池信息 |
| operatingMode | String | 是 | 操作模式枚举 |
| errors | List\<Error\> | 是 | 活跃错误列表 |
| informations | List\<Info\> | 是 | 信息列表 |
| safetyState | SafetyState | 是 | 安全状态 |

### 1.3 InstantActions — 即时动作

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| headerId | int | 是 | 消息头 ID |
| timestamp | String | 是 | ISO8601 |
| version | String | 是 | 协议版本 |
| manufacturer | String | 是 | 制造商 |
| serialNumber | String | 是 | 序列号 |
| instantActions | List\<Action\> | 是 | 即时动作列表 |

### 1.4 Connection — 连接状态

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| headerId | int | 是 | 消息头 ID |
| timestamp | String | 是 | ISO8601 |
| version | String | 是 | 协议版本 |
| manufacturer | String | 是 | 制造商 |
| serialNumber | String | 是 | 序列号 |
| connectionState | String | 是 | `ONLINE` / `OFFLINE` / `CONNECTIONBROKEN` |

### 1.5 Factsheet — 车辆能力

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| headerId | int | 是 | 消息头 ID |
| timestamp | String | 是 | ISO8601 |
| version | String | 是 | 协议版本 |
| manufacturer | String | 是 | 制造商 |
| serialNumber | String | 是 | 序列号 |
| typeSpecification | TypeSpecification | 是 | 类型规格 |
| physicalParameters | PhysicalParameters | 是 | 物理参数 |

---

## 2. 导航元素

### 2.1 Node — 节点

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| nodeId | String | 是 | 节点唯一标识 |
| sequenceId | int | 是 | 序列号 |
| nodeDescription | String | 是 | 附加说明 |
| released | boolean | 是 | true=base, false=horizon |
| nodePosition | NodePosition | 否 | 节点位置 |
| actions | List\<Action\> | 是 | 动作列表 |

### 2.2 NodePosition

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| x | double | 是 | X 坐标 |
| y | double | 是 | Y 坐标 |
| theta | double | 否 | 朝向，[-PI, PI] |
| allowedDeviationXY | float | 是 | 位置偏差（米） |
| allowedDeviationTheta | float | 是 | 角度偏差 |
| mapId | String | 是 | 地图标识 |
| mapDescription | String | 是 | 地图描述 |

### 2.3 Edge — 边

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| edgeId | String | 是 | 边唯一标识 |
| sequenceId | int | 是 | 序列号 |
| edgeDescription | String | 是 | 描述 |
| released | boolean | 是 | base/horizon |
| startNodeId | String | 是 | 起始节点 |
| endNodeId | String | 是 | 终止节点 |
| maxSpeed | double | 是 | 最大速度 (m/s) |
| maxHeight | double | 是 | 最大高度 |
| minHeight | double | 是 | 最小高度 |
| orientation | double | 是 | AGV 朝向 |
| orientationType | String | 否 | GLOBAL / TANGENTIAL |
| direction | String | 否 | 路口方向 |
| rotationAllowed | boolean | 否 | 是否允许旋转 |
| maxRotationSpeed | double | 否 | 最大旋转速度 |
| trajectory | Trajectory | 否 | NURBS 轨迹 |
| length | double | 否 | 路径长度 |
| actions | List\<Action\> | 是 | 动作列表 |

### 2.4 NodeState / EdgeState

NodeState: `nodeId`, `sequenceId`, `nodeDescription`, `position(NodePosition)`, `released`

EdgeState: `edgeId`, `sequenceId`, `edgeDescription`, `released`, `trajectory`

### 2.5 Trajectory / ControlPoint

Trajectory: `degree(int)`, `knotVector(List<Double>)`, `controlPoints(List<ControlPoint>)`

ControlPoint: `x`, `y`, `weight(可选,默认1.0)`

---

## 3. 动作相关

### 3.1 Action

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| actionType | String | 是 | 动作类型名 |
| actionId | String | 是 | 唯一标识（建议 UUID） |
| actionDescription | String | 是 | 描述 |
| blockingType | String | 是 | NONE / SOFT / HARD |
| actionParameters | List\<ActionParameter\> | 是 | 参数列表 |

### 3.2 ActionParameter

`key(String)`, `value(String)`

### 3.3 ActionState

| 字段 | Java 类型 | 必填 | 说明 |
|------|----------|------|------|
| actionId | String | 是 | 动作 ID |
| actionType | String | 否 | 动作类型 |
| actionDescription | String | 是 | 描述 |
| actionStatus | String | 是 | 状态枚举 |
| resultDescription | String | 是 | 结果描述 |

---

## 4. 车辆状态组件

### 4.1 AgvPosition
`positionInitialized(bool)`, `localizationScore(double,可选)`, `deviationRange(double,可选)`, `x`, `y`, `theta`, `mapId`, `mapDescription`

### 4.2 Velocity
`vx`, `vy`, `omega`

### 4.3 BatteryState
`batteryCharge`, `batteryVoltage`, `batteryHealth(int,0-100)`, `charging(bool)`, `reach(long)`

### 4.4 SafetyState
`eStop(String: AUTOACK/MANUAL/REMOTE/NONE)`, `fieldViolation(bool)`

### 4.5 Load / LoadDimensions / BoundingBoxReference
Load: `loadId`, `loadType`, `loadPosition`, `boundingBoxReference`, `loadDimensions`, `weight`
LoadDimensions: `length`, `width`, `height(可选)`
BoundingBoxReference: `x`, `y`, `z`, `theta`

---

## 5. 错误与信息

### 5.1 Error / ErrorReference
Error: `errorType`, `errorReferences(List)`, `errorDescription`, `errorLevel(WARNING/FATAL)`
ErrorReference: `referenceKey`, `referenceValue`

### 5.2 Info / InfoReference
Info: `infoType`, `infoReferences(List)`, `infoDescription`, `infoLevel(DEBUG/INFO)`
InfoReference: `referenceKey`, `referenceValue`

---

## 6. 能力描述

### 6.1 TypeSpecification
`seriesName`, `seriesDescription`, `agvKinematics`, `agvClass(FORKLIFT/CARRIER/TUGGER/...)`, `maxLoadMass`, `localizationTypes(List)`, `navigationTypes(List)`

### 6.2 PhysicalParameters
`speedMin`, `speedMax`, `accelerationMax`, `decelerationMax`, `heightMin`, `heightMax`, `width`, `length`

---

## 7. 枚举类型汇总

| 枚举类 | 值 | 用途 |
|--------|-----|------|
| BlockingType | NONE, SOFT, HARD | 动作阻塞类型 |
| ActionStatus | WAITING, INITIALIZING, RUNNING, PAUSED, FINISHED, FAILED | 动作状态 |
| OperatingMode | AUTOMATIC, SEMIAUTOMATIC, MANUAL, SERVICE, TEACHIN | AGV 操作模式 |
| EStopType | AUTOACK, MANUAL, REMOTE, NONE | 急停类型 |
| ErrorLevel | WARNING, FATAL | 错误级别 |
| ConnectionState | ONLINE, OFFLINE, CONNECTIONBROKEN | 连接状态 |

---

## 8. JSON 示例

### Order 示例（Server 模式构建，Proxy 模式接收）

```json
{
  "headerId": 1,
  "timestamp": "2025-06-11T20:46:59.097Z",
  "version": "2.0.0",
  "manufacturer": "MyCompany",
  "serialNumber": "forklift01",
  "orderId": "order_001",
  "orderUpdateId": 0,
  "nodes": [
    {
      "nodeId": "node0",
      "sequenceId": 0,
      "released": true,
      "nodePosition": {
        "x": 0.0, "y": 0.0, "theta": 0.0,
        "mapId": "warehouse",
        "mapDescription": "",
        "allowedDeviationXY": 1.0,
        "allowedDeviationTheta": 3.14
      },
      "actions": [
        {
          "actionId": "act_001",
          "actionType": "pick",
          "blockingType": "HARD",
          "actionParameters": [{"key": "loadId", "value": "pallet_42"}],
          "actionDescription": ""
        }
      ],
      "nodeDescription": ""
    }
  ],
  "edges": []
}
```

### AgvState 示例（Proxy 模式构建，Server 模式接收）

```json
{
  "headerId": 42,
  "timestamp": "2025-06-11T20:47:05.000Z",
  "version": "2.0.0",
  "manufacturer": "MyCompany",
  "serialNumber": "forklift01",
  "orderId": "order_001",
  "orderUpdateId": 0,
  "lastNodeId": "node0",
  "lastNodeSequenceId": 0,
  "driving": false,
  "paused": false,
  "newBaseRequested": false,
  "distanceSinceLastNode": 0.0,
  "operatingMode": "AUTOMATIC",
  "agvPosition": { "positionInitialized": true, "x": 0.0, "y": 0.0, "theta": 0.0, "mapId": "warehouse", "mapDescription": "" },
  "velocity": { "vx": 0.0, "vy": 0.0, "omega": 0.0 },
  "batteryState": { "batteryCharge": 85.0, "batteryVoltage": 48.2, "batteryHealth": 95, "charging": false, "reach": 12000 },
  "nodeStates": [],
  "edgeStates": [],
  "actionStates": [
    { "actionId": "act_001", "actionType": "pick", "actionStatus": "FINISHED", "actionDescription": "", "resultDescription": "" }
  ],
  "errors": [],
  "informations": [],
  "safetyState": { "eStop": "NONE", "fieldViolation": false }
}
```
