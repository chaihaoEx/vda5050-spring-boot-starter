package com.navasmart.vda5050.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "vda5050")
public class Vda5050Properties {

    private MqttConfig mqtt = new MqttConfig();
    private ProxyConfig proxy = new ProxyConfig();
    private ServerConfig server = new ServerConfig();

    public MqttConfig getMqtt() { return mqtt; }
    public void setMqtt(MqttConfig mqtt) { this.mqtt = mqtt; }

    public ProxyConfig getProxy() { return proxy; }
    public void setProxy(ProxyConfig proxy) { this.proxy = proxy; }

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }

    public static class MqttConfig {
        private String host = "localhost";
        private int port = 1883;
        private String transport = "tcp";
        private int keepAlive = 60;
        private String username = "";
        private String password = "";
        private boolean cleanSession = true;
        private String clientIdPrefix = "vda5050";
        private String interfaceName = "uagv";
        private String majorVersion = "v2";
        private String protocolVersion = "2.0.0";

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
    }

    public static class ProxyConfig {
        private boolean enabled = false;
        private long heartbeatIntervalMs = 1000;
        private long orderLoopIntervalMs = 200;
        private long navigationTimeoutMs = 300000;
        private long actionTimeoutMs = 120000;
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

    public static class ServerConfig {
        private boolean enabled = false;
        private long stateTimeoutMs = 60000;
        private long connectionCheckMs = 30000;
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

    public static class VehicleConfig {
        private String manufacturer;
        private String serialNumber;
        private String robotType = "FORKLIFT";

        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

        public String getRobotType() { return robotType; }
        public void setRobotType(String robotType) { this.robotType = robotType; }
    }
}
