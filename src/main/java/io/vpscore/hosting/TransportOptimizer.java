package io.vpscore.hosting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;

public class TransportOptimizer {

    private static final Logger log = LoggerFactory.getLogger(TransportOptimizer.class);

    public enum Transport {
        TCP, UDP, WEBSOCKET, SSH
    }

    private final List<Transport> availableTransports = new ArrayList<>();
    private Transport bestTransport = Transport.TCP;

    public TransportOptimizer() {
        detectAvailableTransports();
    }

    private void detectAvailableTransports() {
        availableTransports.add(Transport.TCP);
        availableTransports.add(Transport.UDP);

        try {
            var socket = new Socket();
            socket.connect(new InetSocketAddress("example.com", 443), 2000);
            socket.close();
            availableTransports.add(Transport.WEBSOCKET);
        } catch (Exception e) {
            log.debug("WebSocket transport not available");
        }

        bestTransport = selectBest();
        log.info("Available transports: {} | Best: {}", availableTransports, bestTransport);
    }

    private Transport selectBest() {
        if (availableTransports.contains(Transport.TCP)) return Transport.TCP;
        if (availableTransports.contains(Transport.WEBSOCKET)) return Transport.WEBSOCKET;
        if (availableTransports.contains(Transport.SSH)) return Transport.SSH;
        return Transport.UDP;
    }

    public Transport getBestTransport() {
        return bestTransport;
    }

    public boolean isAvailable(Transport transport) {
        return availableTransports.contains(transport);
    }

    public List<Transport> getAvailableTransports() {
        return List.copyOf(availableTransports);
    }
}
