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
import java.util.Arrays;

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
            char[] ksPassword = config.getKeystorePassword().toCharArray();
            try {
                KeyStore ks = KeyStore.getInstance(config.getKeystoreType());
                try (FileInputStream fis = new FileInputStream(config.getKeystorePath())) {
                    ks.load(fis, ksPassword);
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, ksPassword);
                keyManagers = kmf.getKeyManagers();
            } finally {
                Arrays.fill(ksPassword, '\0');
            }
        }

        if (config.getTruststorePath() != null && !config.getTruststorePath().isEmpty()) {
            char[] tsPassword = config.getTruststorePassword().toCharArray();
            try {
                KeyStore ts = KeyStore.getInstance(config.getKeystoreType());
                try (FileInputStream fis = new FileInputStream(config.getTruststorePath())) {
                    ts.load(fis, tsPassword);
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
                trustManagers = tmf.getTrustManagers();
            } finally {
                Arrays.fill(tsPassword, '\0');
            }
        }

        SSLContext sslContext = SSLContext.getInstance(config.getProtocol());
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext.getSocketFactory();
    }
}
