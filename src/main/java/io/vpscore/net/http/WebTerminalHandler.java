package io.vpscore.net.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public class WebTerminalHandler {

    public void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        var html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <title>VPS Core - Web Terminal</title>
            <style>
                *{margin:0;padding:0;box-sizing:border-box}
                body{background:#0a0a0a;color:#e0e0e0;font-family:'Cascadia Code','JetBrains Mono','Fira Code',monospace;height:100vh;display:flex;flex-direction:column}
                #header{background:#1a1a2e;padding:8px 16px;display:flex;align-items:center;gap:12px;border-bottom:1px solid #333}
                #header h1{font-size:14px;color:#00ff88;font-weight:400}
                #header .status{color:#666;font-size:12px}
                #terminal{flex:1;background:#0d0d0d;padding:12px;overflow-y:auto;font-size:14px;line-height:1.5;white-space:pre-wrap;word-wrap:break-word}
                #input-line{display:flex;background:#111;border-top:1px solid #333;padding:8px 12px;align-items:center}
                #prompt{color:#00ff88;margin-right:8px;white-space:nowrap;font-size:14px}
                #input{background:transparent;border:none;color:#e0e0e0;font-family:inherit;font-size:14px;flex:1;outline:none;caret-color:#00ff88}
                #input::placeholder{color:#444}
                .output{color:#e0e0e0;margin:2px 0}
                .error{color:#ff4444}
                .info{color:#888}
                ::-webkit-scrollbar{width:6px}
                ::-webkit-scrollbar-track{background:#111}
                ::-webkit-scrollbar-thumb{background:#333;border-radius:3px}
            </style>
            </head>
            <body>
            <div id="header">
                <h1>VPS Core Terminal</h1>
                <span class="status">$(hostname)</span>
            </div>
            <div id="terminal">
                <span class="info">VPS Core v1.0.0 — Web Terminal</span>
                <span class="info">Type 'help' for available commands</span>
            </div>
            <div id="input-line">
                <span id="prompt">root@vpscore:~$</span>
                <input id="input" type="text" placeholder="Enter command..." autofocus spellcheck="false">
            </div>
            <script>
                const term=document.getElementById('terminal');
                const input=document.getElementById('input');
                const prompt=document.getElementById('prompt');

                function appendOutput(text,cls='output'){
                    const div=document.createElement('div');
                    div.className=cls;
                    div.textContent=text;
                    term.appendChild(div);
                    term.scrollTop=term.scrollHeight;
                }

                input.addEventListener('keydown',async function(e){
                    if(e.key==='Enter'){
                        e.preventDefault();
                        const cmd=this.value.trim();
                        if(!cmd) return;
                        appendOutput('root@vpscore:~$ '+cmd,'output');
                        this.value='';
                        try{
                            const res=await fetch('/api/exec',{
                                method:'POST',
                                headers:{'Content-Type':'application/json'},
                                body:JSON.stringify({cmd:cmd})
                            });
                            const data=await res.json();
                            if(data.output) appendOutput(data.output);
                            if(data.error) appendOutput(data.error,'error');
                        }catch(err){
                            appendOutput('Error: '+err.message,'error');
                        }
                    }
                });
            </script>
            </body>
            </html>
            """;
        HTTPServer.sendHtml(ctx, io.netty.handler.codec.http.HttpResponseStatus.OK, html);
    }
}
