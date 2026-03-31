package com.navasmart.vda5050.test;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lightweight embedded MQTT broker backed by Moquette, intended for
 * integration tests.  Each instance binds to a random available port
 * on the loopback interface so that parallel test runs never collide.
 */
public class EmbeddedMqttBroker {

    private final Server server = new Server();
    private int port;

    /**
     * Starts the embedded broker on a random free port.
     *
     * @throws IOException if the broker fails to start
     */
    public void start() throws IOException {
        this.port = findFreePort();

        Properties props = new Properties();
        props.setProperty("host", "127.0.0.1");
        props.setProperty("port", String.valueOf(port));
        props.setProperty("allow_anonymous", "true");
        // 将 H2 持久化文件写入临时目录，避免污染项目根目录
        Path tempDir = Files.createTempDirectory("moquette-test");
        props.setProperty("data_path", tempDir.toString());

        IConfig config = new MemoryConfig(props);
        server.startServer(config);
    }

    /**
     * Stops the embedded broker and releases all resources.
     */
    public void stop() {
        server.stopServer();
    }

    /**
     * Returns the TCP port the broker is listening on.
     *
     * @return the broker port
     */
    public int getPort() {
        return port;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
