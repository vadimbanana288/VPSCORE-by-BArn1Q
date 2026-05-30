package io.vpscore.net.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.vpscore.VPSCore;
import io.vpscore.config.VPSConfig.NetworkConfig;
import io.vpscore.security.AuthManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HTTPServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HTTPServer.class);

    private static final String HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>VPS Core</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{background:#0c0c10;color:#c0c0d0;font-family:'Segoe UI','Cascadia Code',monospace;min-height:100vh}
header{background:#16161e;padding:12px 24px;border-bottom:1px solid #2a2a3a;display:flex;align-items:center;gap:16px}
header h1{color:#00ff88;font-size:18px;font-weight:600}
header .info{color:#888;font-size:13px}
nav{background:#12121a;display:flex;gap:0;border-bottom:1px solid #2a2a3a;padding:0 24px}
nav button{background:none;border:none;color:#888;padding:10px 20px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;transition:.2s}
nav button:hover{color:#fff}
nav button.active{color:#00ff88;border-bottom-color:#00ff88}
main{padding:24px;max-width:1200px;margin:0 auto}
.tab{display:none}
.tab.active{display:block}
#terminal{background:#0a0a0e;border:1px solid #2a2a3a;border-radius:8px;overflow:hidden}
#term-output{height:400px;overflow-y:auto;padding:16px;font-family:'Cascadia Code','Fira Code',monospace;font-size:14px;line-height:1.6;white-space:pre-wrap;word-wrap:break-word;color:#c0c0d0}
#term-output .prompt{color:#00ff88}
#term-output .error{color:#ff4455}
#term-output .info{color:#888}
#term-input-line{display:flex;background:#0e0e14;border-top:1px solid #2a2a3a;padding:8px 16px}
#term-prompt{color:#00ff88;margin-right:10px;font-family:monospace;font-size:14px;white-space:nowrap}
#term-input{background:transparent;border:none;color:#c0c0d0;font-family:'Cascadia Code',monospace;font-size:14px;flex:1;outline:none}
#term-input::placeholder{color:#444}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin-bottom:24px}
.card{background:#16161e;border:1px solid #2a2a3a;border-radius:8px;padding:16px}
.card h3{color:#888;font-size:12px;text-transform:uppercase;letter-spacing:1px;margin-bottom:8px}
.card .value{color:#fff;font-size:24px;font-weight:600}
.card .value.green{color:#00ff88}
.card .value.yellow{color:#ffcc00}
.card .value.blue{color:#4488ff}
pre{background:#0a0a0e;color:#c0c0d0;padding:12px;border-radius:6px;font-size:13px;overflow-x:auto;border:1px solid #2a2a3a}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #2a2a3a}
th{color:#888;font-weight:400;text-transform:uppercase;letter-spacing:1px;font-size:11px}
td{color:#c0c0d0}
tr:hover td{background:#1a1a24}
::-webkit-scrollbar{width:6px}
::-webkit-scrollbar-track{background:#0a0a0e}
::-webkit-scrollbar-thumb{background:#333;border-radius:3px}
</style>
</head>
<body>
<header>
  <h1>VPS Core</h1>
  <span class="info" id="header-info">loading...</span>
</header>
<nav>
  <button class="active" onclick="switchTab('console')">Console</button>
  <button onclick="switchTab('processes')">Processes</button>
  <button onclick="switchTab('files')">Files</button>
  <button onclick="switchTab('system')">System</button>
</nav>
<main>
<div id="tab-console" class="tab active">
  <div id="terminal">
    <div id="term-output"></div>
    <div id="term-input-line">
      <span id="term-prompt">root@vps:~$</span>
      <input id="term-input" type="text" placeholder="Type command..." autofocus spellcheck="false">
    </div>
  </div>
</div>
<div id="tab-processes" class="tab">
  <table><thead><tr><th>PID</th><th>Command</th><th>Status</th><th>Uptime</th></tr></thead><tbody id="proc-list"></tbody></table>
</div>
<div id="tab-files" class="tab">
  <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
    <span style="color:#888;font-size:13px">Path:</span>
    <input id="fs-path" type="text" value="/home/container" style="background:#0e0e14;border:1px solid #2a2a3a;border-radius:4px;color:#c0c0d0;padding:6px 10px;font-family:monospace;flex:1;outline:none">
    <button onclick="loadFiles()" style="background:#00ff88;color:#000;border:none;border-radius:4px;padding:6px 16px;cursor:pointer;font-weight:600">Go</button>
  </div>
  <table><thead><tr><th>Name</th><th>Size</th><th>Type</th></tr></thead><tbody id="fs-list"></tbody></table>
</div>
<div id="tab-system" class="tab">
  <div class="cards" id="sys-cards"></div>
  <pre id="sys-detail"></pre>
</div>
</main>
<script>
const termOut=document.getElementById('term-output');
const termIn=document.getElementById('term-input');

function appendTerm(text,cls=''){const d=document.createElement('div');d.className=cls;d.textContent=text;termOut.appendChild(d);termOut.scrollTop=termOut.scrollHeight}

termIn.addEventListener('keydown',async function(e){
  if(e.key!=='Enter')return;
  e.preventDefault();
  const cmd=this.value.trim();
  if(!cmd)return;
  appendTerm('root@vps:~$ '+cmd,'prompt');
  this.value='';
  try{
    const r=await fetch('/api/exec',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({cmd})});
    const d=await r.json();
    if(d.output) appendTerm(d.output);
    if(d.error) appendTerm(d.error,'error');
  }catch(e){appendTerm('Error: '+e.message,'error')}
});

function switchTab(name){
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  document.querySelectorAll('nav button').forEach(b=>b.classList.remove('active'));
  document.getElementById('tab-'+name).classList.add('active');
  document.querySelector('nav button[onclick*="'+name+'"]').classList.add('active');
  if(name==='system')loadSystem();
  if(name==='processes')loadProcesses();
  if(name==='files')loadFiles();
}

async function loadSystem(){
  const r=await fetch('/api/info');const d=await r.json();
  const s=await fetch('/api/sysinfo');const sd=await s.json();
  const cards=[
    {h:'CPU',v:d.cpu?.toFixed(1)+'%',c:'green'},
    {h:'Memory',v:d.mem_used+'M / '+d.mem_total+'M',c:'blue'},
    {h:'Disk Free',v:d.disk_free+'M / '+d.disk_total+'M',c:'yellow'},
    {h:'Threads',v:d.threads,c:'green'},
    {h:'OS',v:sd.os,c:''},
    {h:'Java',v:sd.java,c:''},
    {h:'CPU Cores',v:sd.cpus,c:''},
    {h:'Max Memory',v:sd.mem_max,c:'blue'}
  ];
  document.getElementById('sys-cards').innerHTML=cards.map(c=>
    '<div class="card"><h3>'+c.h+'</h3><div class="value '+(c.c||'')+'">'+c.v+'</div></div>'
  ).join('');
  document.getElementById('sys-detail').textContent=JSON.stringify(sd,null,2);
  document.getElementById('header-info').textContent=sd.os+' | '+sd.cpus+' cores | '+sd.mem_max;
}

async function loadProcesses(){
  const r=await fetch('/api/processes');const d=await r.json();
  document.getElementById('proc-list').innerHTML=(d.processes||[]).map(p=>
    '<tr><td>'+p.pid+'</td><td>'+p.command+'</td><td>'+(p.running?'<span style="color:#00ff88">running</span>':'<span style="color:#888">exited ('+p.exit_code+')</span>')+'</td><td>'+(p.uptime_ms?Math.round(p.uptime_ms/1000)+'s':'')+'</td></tr>'
  ).join('')||'<tr><td colspan="4" style="color:#888;text-align:center">No processes</td></tr>';
}

async function loadFiles(){
  const p=document.getElementById('fs-path').value||'/home/container';
  try{
    const r=await fetch('/api/fs/ls?path='+encodeURIComponent(p));
    const d=await r.json();
    if(d.error){document.getElementById('fs-list').innerHTML='<tr><td colspan="3" style="color:#ff4455;text-align:center">'+d.error+'</td></tr>';return}
    document.getElementById('fs-list').innerHTML=(d.entries||[]).map(e=>
      '<tr><td><span style="color:'+(e.directory?'#4488ff':'#c0c0d0')+'">'+(e.directory?'[DIR] ':'[FILE] ')+e.name+'</span></td><td>'+(e.directory?'--':e.size+' bytes')+'</td><td>'+(e.directory?'directory':'file')+'</td></tr>'
    ).join('')||'<tr><td colspan="3" style="color:#888;text-align:center">Empty</td></tr>';
  }catch(e){document.getElementById('fs-list').innerHTML='<tr><td colspan="3" style="color:#ff4455;text-align:center">Error loading</td></tr>'}
}

appendTerm('VPS Core v1.0.0','info');
appendTerm('Type "help" for commands','info');
setInterval(loadSystem,5000);
</script>
</body>
</html>""";

    private final NetworkConfig config;
    private final AuthManager authManager;
    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Map<String, HttpHandler> handlers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;
    private Channel channel;
    private final int port;

    public HTTPServer(NetworkConfig config, AuthManager authManager, int port) {
        this.config = config;
        this.authManager = authManager;
        this.port = port;
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        handlers.put("/api/health", (ctx, req) -> sendJson(ctx, OK, "{\"status\":\"ok\",\"version\":\"1.0.0\"}"));
        handlers.put("/api/info", (ctx, req) -> {
            var core = VPSCore.getInstance();
            var mon = core.getLauncher().getResourceMonitor();
            var snap = mon != null ? mon.getSnapshot() : null;
            var json = new Gson().toJson(Map.of(
                "os", System.getProperty("os.name"),
                "cpus", Runtime.getRuntime().availableProcessors(),
                "mem_total", Runtime.getRuntime().totalMemory() / (1024 * 1024),
                "mem_max", Runtime.getRuntime().maxMemory() / (1024 * 1024),
                "cpu", snap != null ? snap.cpuUsage() : 0,
                "mem_used", snap != null ? snap.memoryUsed() / (1024 * 1024) : 0,
                "disk_free", snap != null ? snap.diskFree() / (1024 * 1024) : 0,
                "disk_total", snap != null ? snap.diskTotal() / (1024 * 1024) : 0,
                "threads", snap != null ? snap.threadCount() : 0
            ));
            sendJson(ctx, OK, json);
        });
        handlers.put("/api/sysinfo", (ctx, req) -> {
            var json = new Gson().toJson(Map.of(
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "arch", System.getProperty("os.arch"),
                "java", System.getProperty("java.version"),
                "jvm", System.getProperty("java.vm.name"),
                "cpus", Runtime.getRuntime().availableProcessors(),
                "mem_total", Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB",
                "mem_max", Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB",
                "user", System.getProperty("user.name"),
                "dir", System.getProperty("user.dir")
            ));
            sendJson(ctx, OK, json);
        });
        handlers.put("/api/exec", this::handleExec);
        handlers.put("/api/fs", this::handleFs);
        handlers.put("/api/fs/ls", this::handleFsList);
        handlers.put("/api/fs/cat", this::handleFsCat);
        handlers.put("/api/processes", this::handleProcesses);
        handlers.put("/", (ctx, req) -> sendHtml(ctx, OK, HTML));
        handlers.put("/terminal", (ctx, req) -> sendHtml(ctx, OK, HTML));
        handlers.put("/desktop", (ctx, req) -> sendHtml(ctx, OK, HTML));
        handlers.put("/api/desktop/status", this::handleDesktopStatus);
        handlers.put("/api/desktop/install", this::handleDesktopInstall);
        handlers.put("/api/desktop/start", this::handleDesktopStart);
        handlers.put("/api/desktop/stop", this::handleDesktopStop);
    }

    public void start() throws Exception {
        running = true;
        var b = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(
                        new HttpServerCodec(),
                        new HttpObjectAggregator(1048576),
                        new WebSocketServerProtocolHandler("/api/desktop/vnc", null, true),
                        new HttpRequestHandler(handlers, authManager),
                        new VncProxyHandler("127.0.0.1", 5900)
                    );
                }
            })
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        channel = b.bind(port).sync().channel();
        System.out.println("[VPS Core] HTTP server started on port " + port);
        log.info("HTTP server started on port {}", port);
    }

    private void handleExec(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var json = req.content().toString(StandardCharsets.UTF_8);
            var obj = JsonParser.parseString(json).getAsJsonObject();
            var cmd = obj.get("cmd").getAsString();

            var core = VPSCore.getInstance();
            var pm = core.getLauncher().getProcessManager();
            var pid = pm.execute(cmd, false);
            var proc = pm.getProcess(pid);

            if (proc != null) {
                executor.execute(() -> {
                    try {
                        proc.getProcess().waitFor(10, TimeUnit.SECONDS);
                        var output = proc.getStdout().stream().collect(Collectors.joining("\n"));
                        var error = proc.getStderr().stream().collect(Collectors.joining("\n"));
                        var result = new JsonObject();
                        result.addProperty("exit_code", proc.getExitCode());
                        result.addProperty("output", output);
                        if (!error.isBlank()) result.addProperty("error", error);
                        ctx.executor().execute(() -> sendJson(ctx, OK, new Gson().toJson(result)));
                    } catch (Exception ex) {
                        ctx.executor().execute(() -> sendJson(ctx, BAD_REQUEST, "{\"error\":\"" + ex.getMessage() + "\"}"));
                    }
                });
            } else {
                sendJson(ctx, OK, "{\"output\":\"Process finished\"}");
            }
        } catch (Exception e) {
            sendJson(ctx, BAD_REQUEST, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleFs(ChannelHandlerContext ctx, FullHttpRequest req) {
        sendJson(ctx, OK, "{\"status\":\"fs_not_implemented\"}");
    }

    private void handleFsList(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var core = VPSCore.getInstance();
            var fs = core.getLauncher().getFileSystemManager();
            if (fs == null) { sendJson(ctx, OK, "{\"entries\":[]}"); return; }

            var uri = req.uri();
            var pathStart = uri.indexOf("path=");
            var path = pathStart >= 0 ? uri.substring(pathStart + 5) : ".";
            if (path.contains("&")) path = path.substring(0, path.indexOf("&"));
            path = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);

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
            sendJson(ctx, OK, new Gson().toJson(json));
        } catch (Exception e) {
            sendJson(ctx, OK, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleFsCat(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var core = VPSCore.getInstance();
            var fs = core.getLauncher().getFileSystemManager();
            if (fs == null) { sendJson(ctx, OK, "{\"content\":\"\"}"); return; }

            var uri = req.uri();
            var pathStart = uri.indexOf("path=");
            var path = pathStart >= 0 ? uri.substring(pathStart + 5) : "";
            if (path.contains("&")) path = path.substring(0, path.indexOf("&"));
            path = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);

            var content = fs.readFile(path);
            sendJson(ctx, OK, "{\"content\":" + new Gson().toJson(content) + "}");
        } catch (Exception e) {
            sendJson(ctx, OK, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleProcesses(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var core = VPSCore.getInstance();
            var pm = core.getLauncher().getProcessManager();
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
            sendJson(ctx, OK, new Gson().toJson(json));
        } catch (Exception e) {
            sendJson(ctx, OK, "{\"processes\":[],\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleDesktopStatus(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var desktop = VPSCore.getInstance().getLauncher().getDesktop();
            sendJson(ctx, OK, new Gson().toJson(desktop.getStatus()));
        } catch (Exception e) {
            var obj = new JsonObject();
            obj.addProperty("installed", false);
            obj.addProperty("running", false);
            obj.addProperty("error", e.getMessage());
            sendJson(ctx, OK, new Gson().toJson(obj));
        }
    }

    private void handleDesktopInstall(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var desktop = VPSCore.getInstance().getLauncher().getDesktop();
            if (desktop.isInstalled()) {
                var obj = new JsonObject();
                obj.addProperty("result", "already_installed");
                sendJson(ctx, OK, new Gson().toJson(obj));
                return;
            }
            desktop.install().thenAcceptAsync(result -> {
                System.out.println("[Desktop] Install result: " + result);
                if (ctx.channel().isActive()) {
                    var obj = new JsonObject();
                    obj.addProperty("result", result);
                    sendJson(ctx, OK, new Gson().toJson(obj));
                }
            }, ctx.executor()).exceptionally(e -> {
                var msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                System.out.println("[Desktop] Install error: " + msg);
                if (ctx.channel().isActive()) {
                    var obj = new JsonObject();
                    obj.addProperty("error", msg);
                    sendJson(ctx, BAD_REQUEST, new Gson().toJson(obj));
                }
                return null;
            });
        } catch (Exception e) {
            System.out.println("[Desktop] Install error: " + e.getMessage());
            var obj = new JsonObject();
            obj.addProperty("error", e.getMessage());
            sendJson(ctx, BAD_REQUEST, new Gson().toJson(obj));
        }
    }

    private void handleDesktopStart(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var desktop = VPSCore.getInstance().getLauncher().getDesktop();
            desktop.startAsync(null).thenAcceptAsync(result -> {
                System.out.println("[Desktop] Start result: " + result);
                if (ctx.channel().isActive()) {
                    var obj = new JsonObject();
                    obj.addProperty("status", result);
                    sendJson(ctx, OK, new Gson().toJson(obj));
                }
            }, ctx.executor()).exceptionally(e -> {
                var msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                System.out.println("[Desktop] Start error: " + msg);
                if (ctx.channel().isActive()) {
                    var obj = new JsonObject();
                    obj.addProperty("error", msg);
                    sendJson(ctx, BAD_REQUEST, new Gson().toJson(obj));
                }
                return null;
            });
        } catch (Exception e) {
            System.out.println("[Desktop] Start error: " + e.getMessage());
            var obj = new JsonObject();
            obj.addProperty("error", e.getMessage());
            sendJson(ctx, BAD_REQUEST, new Gson().toJson(obj));
        }
    }

    private void handleDesktopStop(ChannelHandlerContext ctx, FullHttpRequest req) {
        try {
            var desktop = VPSCore.getInstance().getLauncher().getDesktop();
            desktop.stop();
            System.out.println("[Desktop] Stopped");
            var obj = new JsonObject();
            obj.addProperty("status", "stopped");
            sendJson(ctx, OK, new Gson().toJson(obj));
        } catch (Exception e) {
            System.out.println("[Desktop] Stop error: " + e.getMessage());
            var obj = new JsonObject();
            obj.addProperty("error", e.getMessage());
            sendJson(ctx, BAD_REQUEST, new Gson().toJson(obj));
        }
    }

    static void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String json) {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        HttpUtil.setContentLength(response, bytes.length);
        ctx.writeAndFlush(response);
    }

    static void sendHtml(ChannelHandlerContext ctx, HttpResponseStatus status, String html) {
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpUtil.setContentLength(response, bytes.length);
        ctx.writeAndFlush(response);
    }

    public void registerHandler(String path, HttpHandler handler) {
        handlers.put(path, handler);
    }

    @Override
    public void close() {
        running = false;
        if (channel != null) channel.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("HTTP server stopped");
    }

    @FunctionalInterface
    public interface HttpHandler {
        void handle(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception;
    }

    static class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final Map<String, HttpHandler> handlers;
        private final AuthManager authManager;

        HttpRequestHandler(Map<String, HttpHandler> handlers, AuthManager authManager) {
            this.handlers = handlers;
            this.authManager = authManager;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            var uri = req.uri().contains("?") ? req.uri().substring(0, req.uri().indexOf('?')) : req.uri();
            var handler = handlers.get(uri);
            if (handler != null) {
                try {
                    handler.handle(ctx, req);
                } catch (Exception e) {
                    sendJson(ctx, INTERNAL_SERVER_ERROR,
                        "{\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                sendJson(ctx, NOT_FOUND, "{\"error\":\"not_found\"}");
            }
        }
    }
}
