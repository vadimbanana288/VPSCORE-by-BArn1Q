package io.vpscore.net;

import io.vpscore.config.VPSConfig.NetworkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class DNSServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DNSServer.class);

    private final NetworkConfig config;
    private final Map<String, String> records = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running;
    private DatagramSocket socket;

    public DNSServer(NetworkConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        running = true;
        socket = new DatagramSocket(config.getDnsPort());
        executor.submit(this::listen);
        log.info("DNS server started on port {}", config.getDnsPort());
    }

    private void listen() {
        var buf = new byte[512];
        var packet = new DatagramPacket(buf, buf.length);
        while (running) {
            try {
                socket.receive(packet);
                handleQuery(packet);
            } catch (Exception e) {
                if (running) log.debug("DNS receive error", e);
            }
        }
    }

    private void handleQuery(DatagramPacket packet) {
        var response = buildResponse(packet.getData(), packet.getLength());
        if (response != null) {
            try {
                socket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
            } catch (Exception e) {
                log.debug("DNS send error", e);
            }
        }
    }

    private byte[] buildResponse(byte[] query, int length) {
        // Simple DNS response builder stub
        var response = Arrays.copyOf(query, length);
        if (length > 12) {
            response[2] = (byte) 0x81;
            response[3] = (byte) 0x80;
        }
        return response;
    }

    public void addRecord(String domain, String ip) {
        records.put(domain.toLowerCase(), ip);
    }

    public void removeRecord(String domain) {
        records.remove(domain.toLowerCase());
    }

    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        executor.shutdownNow();
    }
}
