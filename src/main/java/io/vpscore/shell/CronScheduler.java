package io.vpscore.shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class CronScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        var t = new Thread(r, "vps-cron");
        t.setDaemon(true);
        return t;
    });

    private final List<CronTask> tasks = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public void start() {
        running = true;
        scheduler.scheduleAtFixedRate(this::tick, 0, 30, TimeUnit.SECONDS);
        log.info("Cron scheduler started");
    }

    public void addTask(String name, String cronExpression, Consumer<String> action) {
        var task = new CronTask(name, cronExpression, action);
        tasks.add(task);
        log.debug("Cron task added: {} [{}]", name, cronExpression);
    }

    public void removeTask(String name) {
        tasks.removeIf(t -> t.name.equals(name));
    }

    private void tick() {
        if (!running) return;
        var now = LocalDateTime.now();
        for (var task : tasks) {
            if (task.shouldRun(now)) {
                scheduler.submit(() -> {
                    try {
                        log.debug("Running cron task: {}", task.name);
                        task.action.accept(task.name);
                    } catch (Exception e) {
                        log.error("Cron task '{}' failed", task.name, e);
                    }
                });
            }
        }
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }

    record CronTask(String name, String cronExpression, Consumer<String> action) {
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

        boolean shouldRun(LocalDateTime now) {
            var parts = cronExpression.split("\\s+");
            if (parts.length < 5) return false;

            var minute = parts[0];
            var hour = parts[1];

            var currentMinute = String.valueOf(now.getMinute());
            var currentHour = String.valueOf(now.getHour());

            return (minute.equals("*") || minute.equals(currentMinute))
                && (hour.equals("*") || hour.equals(currentHour));
        }
    }
}
