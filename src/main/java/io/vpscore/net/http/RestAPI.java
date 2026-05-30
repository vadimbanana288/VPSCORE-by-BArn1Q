package io.vpscore.net.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpMethod;
import io.vpscore.VPSCore;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class RestAPI {

    private static final Gson gson = new Gson();

    public static void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        var uri = req.uri();
        var method = req.method();

        try {
            if (method == HttpMethod.GET && uri.equals("/api/health")) {
                health(ctx);
            } else if (method == HttpMethod.GET && uri.equals("/api/info")) {
                info(ctx);
            } else if (method == HttpMethod.GET && uri.equals("/api/processes")) {
                processes(ctx);
            } else if (method == HttpMethod.POST && uri.equals("/api/exec")) {
                exec(ctx, req);
            } else if (method == HttpMethod.GET && uri.startsWith("/api/fs/ls")) {
                fsList(ctx, uri);
            } else {
                HTTPServer.sendJson(ctx, NOT_FOUND, "{\"error\":\"not_found\"}");
            }
        } catch (Exception e) {
            HTTPServer.sendJson(ctx, INTERNAL_SERVER_ERROR,
                "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static void health(ChannelHandlerContext ctx) {
        HTTPServer.sendJson(ctx, OK, "{\"status\":\"ok\",\"running\":true}");
    }

    private static void info(ChannelHandlerContext ctx) {
        var json = new JsonObject();
        json.addProperty("version", "1.0.0");
        json.addProperty("os", System.getProperty("os.name"));
        json.addProperty("arch", System.getProperty("os.arch"));
        json.addProperty("java", System.getProperty("java.version"));
        json.addProperty("cpus", Runtime.getRuntime().availableProcessors());
        json.addProperty("memory_total_mb", Runtime.getRuntime().totalMemory() / (1024 * 1024));
        json.addProperty("memory_max_mb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        json.addProperty("mode", VPSCore.getInstance().getBootstrap().getConfig().getMode().getKey());
        HTTPServer.sendJson(ctx, OK, gson.toJson(json));
    }

    private static void processes(ChannelHandlerContext ctx) {
        var pm = VPSCore.getInstance().getLauncher().getProcessManager();
        var list = pm.listProcesses();
        var arr = new com.google.gson.JsonArray();
        for (var p : list) {
            var obj = new JsonObject();
            obj.addProperty("pid", p.getPid());
            obj.addProperty("command", p.getCommand());
            obj.addProperty("running", p.isRunning());
            obj.addProperty("uptime_ms", p.getUptime());
            obj.addProperty("exit_code", p.getExitCode());
            arr.add(obj);
        }
        var json = new JsonObject();
        json.add("processes", arr);
        json.addProperty("count", list.size());
        HTTPServer.sendJson(ctx, OK, gson.toJson(json));
    }

    private static void exec(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var content = req.content().toString(StandardCharsets.UTF_8);
            var json = gson.fromJson(content, JsonObject.class);
            var cmd = json.get("cmd").getAsString();

            var pm = VPSCore.getInstance().getLauncher().getProcessManager();
            var pid = pm.execute(cmd, false);
            var proc = pm.getProcess(pid);

            if (proc != null && !proc.isRunning()) {
                var result = new JsonObject();
                result.addProperty("exit_code", proc.getExitCode());
                result.addProperty("output", String.join("\n", proc.getStdout()));
                result.addProperty("error", String.join("\n", proc.getStderr()));
                HTTPServer.sendJson(ctx, OK, gson.toJson(result));
            } else {
                var result = new JsonObject();
                result.addProperty("pid", pid);
                result.addProperty("status", "started");
                HTTPServer.sendJson(ctx, OK, gson.toJson(result));
            }
        } catch (Exception e) {
            HTTPServer.sendJson(ctx, BAD_REQUEST, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static void fsList(ChannelHandlerContext ctx, String uri) {
        var path = uri.length() > "/api/fs/ls".length()
            ? uri.substring("/api/fs/ls".length())
            : ".";
        try {
            var fs = VPSCore.getInstance().getLauncher().getFileSystemManager();
            if (fs == null) {
                HTTPServer.sendJson(ctx, SERVICE_UNAVAILABLE, "{\"error\":\"fs_not_available\"}");
                return;
            }
            var entries = fs.list(path);
            var arr = new com.google.gson.JsonArray();
            for (var e : entries) {
                var obj = new JsonObject();
                obj.addProperty("name", e.name());
                obj.addProperty("path", e.path());
                obj.addProperty("directory", e.isDirectory());
                obj.addProperty("size", e.size());
                obj.addProperty("last_modified", e.lastModified());
                arr.add(obj);
            }
            var json = new JsonObject();
            json.add("entries", arr);
            json.addProperty("path", path);
            HTTPServer.sendJson(ctx, OK, gson.toJson(json));
        } catch (Exception e) {
            HTTPServer.sendJson(ctx, INTERNAL_SERVER_ERROR,
                "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
