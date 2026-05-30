package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import io.vpscore.net.http.HTTPServer;
import io.vpscore.security.AuthManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NetworkManager.class);

    private final NetworkConfig config;
    private final AuthManager authManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final int webPort;
    private volatile boolean running;

    private TunnelManager tunnelManager;
    private Firewall firewall;
    private DNSServer dnsServer;
    private ReverseProxy reverseProxy;
    private HTTPServer httpServer;

    public NetworkManager(NetworkConfig config, AuthManager authManager, int webPort) {
        this.config = config;
        this.authManager = authManager;
        this.webPort = webPort;
    }

    public void start() throws Exception {
        running = true;

        tunnelManager = new TunnelManager(config);
        tunnelManager.start();

        if (config.isFirewallEnable()) {
            firewall = new Firewall(config);
            firewall.start();
        }

        if (config.isDnsEnable()) {
            dnsServer = new DNSServer(config);
            dnsServer.start();
        }

        if (config.isReverseProxyEnable()) {
            reverseProxy = new ReverseProxy(config, authManager);
            reverseProxy.start();
        }

        httpServer = new HTTPServer(config, authManager, webPort);
        httpServer.start();

        log.info("Network manager started");
    }

    @Override
    public void close() {
        running = false;
        if (httpServer != null) httpServer.close();
        if (reverseProxy != null) reverseProxy.close();
        if (dnsServer != null) dnsServer.close();
        if (firewall != null) firewall.close();
        if (tunnelManager != null) tunnelManager.close();
        executor.shutdownNow();
        log.info("Network manager stopped");
    }
}
