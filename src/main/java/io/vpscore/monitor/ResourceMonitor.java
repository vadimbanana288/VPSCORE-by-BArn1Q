package io.vpscore.monitor;

import io.vpscore.config.VPSConfig.MonitorConfig;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.*;
import java.util.concurrent.*;

public class ResourceMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);

    private final MonitorConfig config;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final MetricsCollector collector = new MetricsCollector();
    private final ResourceLimiter limiter;
    private volatile boolean running;
    private HTTPServer prometheusServer;

    public ResourceMonitor(MonitorConfig config) {
        this.config = config;
        this.limiter = new ResourceLimiter(config);
    }

    public void start() throws Exception {
        running = true;
        DefaultExports.initialize();

        if (config.isPrometheusEnable() && config.getMetricsPort() > 0) {
            try {
                prometheusServer = new HTTPServer(config.getMetricsPort());
                log.info("Prometheus metrics exposed on port {}", config.getMetricsPort());
            } catch (Exception e) {
                log.warn("Could not start Prometheus HTTP server on port {} (may be in use by web server). Metrics available via REST API /api/info", config.getMetricsPort());
            }
        }

        scheduler.scheduleAtFixedRate(collector::collect, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(limiter::check, 0, 10, TimeUnit.SECONDS);

        log.info("Resource monitor started");
    }

    public MetricsSnapshot getSnapshot() {
        return collector.getSnapshot();
    }

    @Override
    public void close() {
        running = false;
        if (prometheusServer != null) prometheusServer.close();
        scheduler.shutdownNow();
        log.info("Resource monitor stopped");
    }

    static class MetricsCollector {
        private static final Gauge cpuGauge = Gauge.build().name("vps_cpu_usage").help("CPU usage %").register();
        private static final Gauge memGauge = Gauge.build().name("vps_memory_usage_bytes").help("Memory usage").register();
        private static final Gauge memMaxGauge = Gauge.build().name("vps_memory_max_bytes").help("Max memory").register();
        private static final Gauge diskFreeGauge = Gauge.build().name("vps_disk_free_bytes").help("Free disk space").register();
        private static final Gauge diskTotalGauge = Gauge.build().name("vps_disk_total_bytes").help("Total disk space").register();
        private static final Gauge threadsGauge = Gauge.build().name("vps_threads").help("Active threads").register();
        private static final Counter opsCounter = Counter.build().name("vps_operations_total").help("Total operations").register();

        private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        private MetricsSnapshot lastSnapshot = new MetricsSnapshot(0, 0, 0, 0, 0, 0);

        void collect() {
            try {
                var cpu = ((com.sun.management.OperatingSystemMXBean) osBean).getCpuLoad() * 100;
                var mem = memBean.getHeapMemoryUsage().getUsed();
                var memMax = memBean.getHeapMemoryUsage().getMax();
                var disk = new File(System.getProperty("user.dir"));
                var diskFree = disk.getFreeSpace();
                var diskTotal = disk.getTotalSpace();
                var threads = threadBean.getThreadCount();

                cpuGauge.set(cpu);
                memGauge.set(mem);
                memMaxGauge.set(memMax);
                diskFreeGauge.set(diskFree);
                diskTotalGauge.set(diskTotal);
                threadsGauge.set(threads);
                opsCounter.inc();

                lastSnapshot = new MetricsSnapshot(cpu, mem, memMax, diskFree, diskTotal, threads);
            } catch (Exception e) {
                log.debug("Metrics collection error", e);
            }
        }

        MetricsSnapshot getSnapshot() { return lastSnapshot; }
    }

    public record MetricsSnapshot(double cpuUsage, long memoryUsed, long memoryMax,
                                   long diskFree, long diskTotal, int threadCount) {}
}
