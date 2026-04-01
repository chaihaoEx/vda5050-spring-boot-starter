package com.navasmart.vda5050.mqtt;

import com.navasmart.vda5050.autoconfigure.Vda5050Properties.SslConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * SSL/TLS 工具类，根据配置构建 {@link SSLSocketFactory}。
 */
public final class SslUtil {

    private SslUtil() {}

    /**
     * 根据 SSL 配置创建 {@link SSLSocketFactory}。
     *
     * @param config SSL 配置
     * @return 配置好的 SSLSocketFactory
     * @throws Exception 证书加载或 SSL 上下文初始化失败时抛出
     */
    public static SSLSocketFactory createSocketFactory(SslConfig config) throws Exception {
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        if (config.getKeystorePath() != null && !config.getKeystorePath().isEmpty()) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(config.getKeystorePath())) {
                ks.load(fis, config.getKeystorePassword().toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, config.getKeystorePassword().toCharArray());
            keyManagers = kmf.getKeyManagers();
        }

        if (config.getTruststorePath() != null && !config.getTruststorePath().isEmpty()) {
            KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
            try (FileInputStream fis = new FileInputStream(config.getTruststorePath())) {
                ts.load(fis, config.getTruststorePassword().toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            trustManagers = tmf.getTrustManagers();
        }

        SSLContext sslContext = SSLContext.getInstance(config.getProtocol());
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext.getSocketFactory();
    }
}
