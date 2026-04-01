# VDA5050 Spring Boot Starter — 安全配置指南

## 1. SSL/TLS 加密连接

支持通过 SSL/TLS 加密 MQTT 通信：

```yaml
vda5050:
  mqtt:
    host: mqtt.example.com
    port: 8883
    ssl:
      enabled: true
      protocol: TLSv1.3
      truststorePath: /path/to/truststore.jks
      truststorePassword: changeit
      keystorePath: /path/to/keystore.jks      # 可选：双向认证时使用
      keystorePassword: changeit
```

启用 SSL 后，MQTT URI scheme 自动切换：
- `tcp` → `ssl`
- `websocket` (`ws`) → `wss`

支持的 Keystore 格式：JKS、PKCS12。

## 2. 密码安全

### 环境变量引用（推荐）

Spring Boot 原生支持在配置文件中引用环境变量，避免明文存储密码：

```yaml
vda5050:
  mqtt:
    username: ${MQTT_USERNAME}
    password: ${MQTT_PASSWORD}
    ssl:
      keystorePassword: ${KEYSTORE_PASSWORD}
      truststorePassword: ${TRUSTSTORE_PASSWORD}
```

运行时通过环境变量注入：

```bash
export MQTT_USERNAME=myuser
export MQTT_PASSWORD=secret123
java -jar my-app.jar
```

### 命令行参数

也可通过 JVM 参数传递敏感配置：

```bash
java -jar my-app.jar \
  --vda5050.mqtt.password=secret123 \
  --vda5050.mqtt.ssl.keystorePassword=changeit
```

### Spring Cloud Config / Vault

对于生产环境，建议使用：

- **Spring Cloud Config Server**：集中管理配置，支持加密存储
- **HashiCorp Vault**：通过 `spring-cloud-vault` 集成，从 Vault 中动态获取密码
- **Jasypt**：通过 `jasypt-spring-boot-starter` 支持在配置文件中使用加密值

## 3. 最佳实践

1. **生产环境必须启用 SSL/TLS**，使用端口 8883（MQTT over TLS）或 443（WSS）
2. **不要在配置文件中硬编码密码**，使用环境变量或外部配置中心
3. **使用 TLSv1.3**（默认），避免使用 TLSv1.0/1.1
4. **定期轮换证书和密码**
5. **限制 MQTT Broker 的 ACL**，确保每辆车只能访问自己的 Topic
