# VPS Core — Turn Minecraft hosting into a VPS

**VPS Core** is a self-contained Java server that transforms any Pterodactyl Minecraft container into a fully functional virtual private server with SSH access and a web management interface.

## Features

- **SSH Server** — Full SSH access via Apache MINA SSHD
- **Web Interface** — Terminal, file manager, process monitor, system info
- **Command Execution** — Execute commands via REST API
- **File Management** — Browse, read files on the server
- **Process Manager** — Monitor and manage running processes
- **Resource Monitoring** — CPU, memory, disk usage tracking
- **Remote Desktop** — Xvfb + x11vnc + Fluxbox via noVNC in browser
- **Security** — IP-based authentication, rate limiting
- **Pterodactyl Integration** — Auto-detects Pterodactyl environment, works with egg system

## Quick Start

### Prerequisites
- Java 21 (Temurin recommended)
- Pterodactyl server (or any Linux server with Java 21)
- At least 2 ports available (web + SSH)

### Installation

1. Upload `server.jar` and `vpscore.yml` to your server directory
2. Start with: `java -Xms512M -Xmx1G -jar server.jar`
3. Access the web interface at `http://your-server:PORT/terminal`
4. Connect via SSH: `ssh root@your-server -p SSH_PORT`

### Configuration

Create `vpscore.yml` in the same directory as `server.jar`:

```yaml
web_terminal_port: 8080
ssh_port: 2222
auth_required: false
password: "admin"
```

## Building from Source

```bash
git clone https://github.com/vpscore/vpscore.git
cd vpscore
./gradlew fatJar
```

The compiled jar will be at `build/libs/vpscore-*-all.jar`.

## API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | Health check |
| `/api/info` | GET | System information |
| `/api/sysinfo` | GET | OS/Java details |
| `/api/exec` | POST | Execute command |
| `/api/processes` | GET | List processes |
| `/api/fs/ls?path=` | GET | List directory |
| `/api/fs/cat?path=` | GET | Read file |
| `/api/desktop/status` | GET | Desktop status |
| `/api/desktop/install` | POST | Install desktop |
| `/api/desktop/start` | POST | Start desktop |
| `/api/desktop/stop` | POST | Stop desktop |
| `/api/desktop/vnc` | WS | VNC WebSocket proxy |

## License

MIT License — see [LICENSE](LICENSE)
