package com.navasmart.vda5050.autoconfigure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * VDA5050 Starter 配置属性，前缀为 {@code vda5050}。
 *
 * <p>配置示例：
 * <pre>
 * vda5050:
 *   mqtt:
 *     host: mqtt.example.com
 *     port: 1883
 *     ssl:
 *       enabled: true
 *       truststorePath: /path/to/truststore.jks
 *       truststorePassword: changeit
 *   proxy:
 *     enabled: true
 *     vehicles:
 *       - manufacturer: MyCompany
 *         serialNumber: forklift01
 *   server:
 *     enabled: true
 *     vehicles:
 *       - manufacturer: ThirdParty
 *         serialNumber: agv01
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "vda5050")
public class Vda5050Properties {

    /** MQTT 连接配置 */
    @Valid
    private MqttConfig mqtt = new MqttConfig();
    /** Proxy 模式配置 */
    @Valid
    private ProxyConfig proxy = new ProxyConfig();
    /** Server 模式配置 */
    @Valid
    private ServerConfig server = new ServerConfig();

    public MqttConfig getMqtt() { return mqtt; }
    public void setMqtt(MqttConfig mqtt) { this.mqtt = mqtt; }

    public ProxyConfig getProxy() { return proxy; }
    public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }

    /**
     * MQTT 连接配置（{@code vda5050.mqtt.*}）。
     */
    public static class MqttConfig {
        /** MQTT Broker 地址。默认：{@code localhost} */
        @NotBlank
        private String host = "localhost";
        /** MQTT Broker 端口。默认：{@code 1883} */
        @Min(1) @Max(65535)
        private int port = 1883;
        /** 传输协议：{@code tcp} 或 {@code websocket}。默认：{@code tcp} */
        private String transport = "tcp";
        /** 心跳间隔（秒）。默认：{@code 60} */
        @Positive
        private int keepAlive = 60;
        /** MQTT 用户名。默认：空字符串（匿名连接） */
        private String username = "";
        /** MQTT 密码。默认：空字符串 */
        private String password = "";
        /** 是否使用 Clean Session。默认：{@code true} */
        private boolean cleanSession = true;
        /** MQTT Client ID 前缀。默认：{@code vda5050} */
        private String clientIdPrefix = "vda5050";
        /** VDA5050 Topic 中的接口名称。默认：{@code uagv} */
        @NotBlank
        private String interfaceName = "uagv";
        /** VDA5050 Topic 中的主版本号。默认：{@code v2} */
        @NotBlank
        private String majorVersion = "v2";
        /** VDA5050 协议版本号，写入消息的 version 字段。默认：{@code 2.0.0} */
        @NotBlank
        private String protocolVersion = "2.0.0";
        /** 最大重连尝试次数。0 = 无限制。默认：{@code 0} */
        @Min(0)
        private int maxReconnectAttempts = 0;
        /** 每种 Topic 类型的最小发布间隔（毫秒）。0 = 不限制。默认：{@code 0} */
        @Min(0)
        private long minPublishIntervalMs = 0;
        /** SSL/TLS 配置 */
        @Valid
        private SslConfig ssl = new SslConfig();

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }

        public int getKeepAlive() { return keepAlive; }
        public void setKeepAlive(int keepAlive) { this.keepAlive = keepAlive; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean isCleanSession() { return cleanSession; }
        public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }

        public String getClientIdPrefix() { return clientIdPrefix; }
        public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }

        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }

        public String getMajorVersion() { return majorVersion; }
        public void setMajorVersion(String majorVersion) { this.majorVersion = majorVersion; }

        public String getProtocolVersion() { return protocolVersion; }
        public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

        public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
        public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

        public long getMinPublishIntervalMs() { return minPublishIntervalMs; }
        public void setMinPublishIntervalMs(long minPublishIntervalMs) { this.minPublishIntervalMs = minPublishIntervalMs; }

        public SslConfig getSsl() { return ssl; }
        public void setSsl(SslConfig ssl) { this.ssl = ssl; }

        /**
         * 根据传输协议和 SSL 配置解析 MQTT URI scheme。
         *
         * @return {@code tcp}、{@code ssl}、{@code ws} 或 {@code wss}
         */
        public String resolveScheme() {
            if ("websocket".equalsIgnoreCase(transport)) {
                return ssl.isEnabled() ? "wss" : "ws";
            }
            return ssl.isEnabled() ? "ssl" : "tcp";
        }
    }

    /**
     * SSL/TLS 配置（{@code vda5050.mqtt.ssl.*}）。
     */
    public static class SslConfig {
        /** 是否启用 SSL/TLS。默认：{@code false} */
        private boolean enabled = false;
        /** Keystore 文件路径（JKS 或 PKCS12 格式） */
        private String keystorePath;
        /** Keystore 密码 */
        private String keystorePassword = "";
        /** Truststore 文件路径 */
        private String truststorePath;
        /** Truststore 密码 */
        private String truststorePassword = "";
        /** SSL 协议版本。默认：{@code TLSv1.3} */
        private String protocol = "TLSv1.3";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getKeystorePath() { return keystorePath; }
        public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }

        public String getKeystorePassword() { return keystorePassword; }
        public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

        public String getTruststorePath() { return truststorePath; }
        public void setTruststorePath(String truststorePath) { this.truststorePath = truststorePath; }

        public String getTruststorePassword() { return truststorePassword; }
        public void setTruststorePassword(String truststorePassword) { this.truststorePassword = truststorePassword; }

        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
    }

    /**
     * Proxy 模式配置（{@code vda5050.proxy.*}）。
     */
    public static class ProxyConfig {
        /** 是否启用 Proxy 模式。默认：{@code false} */
        private boolean enabled = false;
        /** AgvState 心跳发布间隔（毫秒）。默认：{@code 1000} */
        @Positive
        private long heartbeatIntervalMs = 1000;
        /** 订单执行循环间隔（毫秒）。默认：{@code 200} */
        @Positive
        private long orderLoopIntervalMs = 200;
        /** 导航超时时间（毫秒）。默认：{@code 300000}（5 分钟） */
        @Positive
        private long navigationTimeoutMs = 300000;
        /** 单个动作超时时间（毫秒）。默认：{@code 120000}（2 分钟） */
        @Positive
        private long actionTimeoutMs = 120000;
        /** 代理车辆列表 */
        @Valid
        private List<VehicleConfig> vehicles = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
        public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }

        public long getOrderLoopIntervalMs() { return orderLoopIntervalMs; }
        public void setOrderLoopIntervalMs(long orderLoopIntervalMs) { this.orderLoopIntervalMs = orderLoopIntervalMs; }

        public long getNavigationTimeoutMs() { return navigationTimeoutMs; }
        public void setNavigationTimeoutMs(long navigationTimeoutMs) { this.navigationTimeoutMs = navigationTimeoutMs; }

        public long getActionTimeoutMs() { return actionTimeoutMs; }
        public void setActionTimeoutMs(long actionTimeoutMs) { this.actionTimeoutMs = actionTimeoutMs; }

        public List<VehicleConfig> getVehicles() { return vehicles; }
        public void setVehicles(List<VehicleConfig> vehicles) { this.vehicles = vehicles; }
    }

    /**
     * Server 模式配置（{@code vda5050.server.*}）。
     */
    public static class ServerConfig {
        /** 是否启用 Server 模式。默认：{@code false} */
        private boolean enabled = false;
        /** AGV 状态上报超时时间（毫秒），超时触发 onVehicleTimeout 回调。默认：{@code 60000}（1 分钟） */
        @Positive
        private long stateTimeoutMs = 60000;
        /** 连接检查间隔（毫秒）。默认：{@code 30000}（30 秒） */
        @Positive
        private long connectionCheckMs = 30000;
        /** 受控 AGV 车辆列表 */
        @Valid
        private List<VehicleConfig> vehicles = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getStateTimeoutMs() { return stateTimeoutMs; }
        public void setStateTimeoutMs(long stateTimeoutMs) { this.stateTimeoutMs = stateTimeoutMs; }

        public long getConnectionCheckMs() { return connectionCheckMs; }
        public void setConnectionCheckMs(long connectionCheckMs) { this.connectionCheckMs = connectionCheckMs; }

        public List<VehicleConfig> getVehicles() { return vehicles; }
        public void setVehicles(List<VehicleConfig> vehicles) { this.vehicles = vehicles; }
    }

    /**
     * 单辆车辆的配置信息。
     */
    public static class VehicleConfig {
        /** AGV 制造商名称，对应 VDA5050 Topic 中的 manufacturer 部分。必填。 */
        @NotBlank
        private String manufacturer;
        /** AGV 唯一序列号，对应 VDA5050 Topic 中的 serialNumber 部分。必填。 */
        @NotBlank
        private String serialNumber;
        /** 机器人类型，如 FORKLIFT、CARRIER、TUGGER。默认：{@code FORKLIFT} */
        private String robotType = "FORKLIFT";

        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

        public String getRobotType() { return robotType; }
        public void setRobotType(String robotType) { this.robotType = robotType; }
    }
}
