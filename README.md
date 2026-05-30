<pre>
  _    ______  _____ __________  ____  ______      
 | |  / / __ \/ ___// ____/ __ \/ __ \/ ____/      
 | | / / /_/ /\__ \/ /   / / / / /_/ / __/         
 | |/ / ____/___/ / /___/ /_/ / _, _/ /___         
 |___/_/    /____/\____/\____/_/ |_/_____/ _______ 
    / /_  __  __   / __ )/   |  _________ <  / __ \
   / __ \/ / / /  / __  / /| | / ___/ __ \/ / / / /
  / /_/ / /_/ /  / /_/ / ___ |/ /  / / / / / /_/ / 
 /_.___/\__, /  /_____/_/  |_/_/  /_/ /_/_/\___\_\ 
       /____/                                       
</pre>

<h1 align="center">VPS Core</h1>

<p align="center">
  <strong>Turn any Minecraft hosting server into a full-featured VPS — no root required.</strong>
  <br>
  Java 21 • Pterodactyl • Web Terminal • SSH • SFTP • Monitoring
</p>

<p align="center">
  <b>Desktop environment</b> 🚧 <i>under development — coming soon</i>
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| **🌐 Web Terminal** | In-browser shell, type commands like a real terminal |
| **🔐 SSH Server** | Full SSH access with password / TOTP auth |
| **📁 File Manager** | Browse, upload, download files from the web UI |
| **📂 SFTP** | File transfer over SSH (same port) |
| **📊 Monitoring** | CPU, memory, disk — real-time + Prometheus metrics |
| **🔌 Port Proxy** | TCP tunnel forwarding through allocated ports |
| **⚙️ Process Manager** | Run and supervise background tasks |
| **💾 Backups** | Scheduled automatic file backups |
| **🤖 Bots** | Manage your VPS via Telegram or Discord |
| **🖥 Desktop** | Remote desktop (VNC in browser) — 🚧 in development |

---

## Quick Start

### Prerequisites

- A Pterodactyl game server (Minecraft, etc.) or any Linux VM
- Java 21 (Temurin recommended)
- At least **2 allocated ports** (web + SSH)

### Install & Run

```bash
# 1. Download server.jar from Releases
# 2. Upload to your server directory
# 3. Start:

java -Xms6G -Xmx6G -jar server.jar
```

VPS Core auto-detects Pterodactyl allocated ports and generates `vpscore.yml` automatically.

### Open the Web UI

```
http://<your-server-ip>:50906
```

---

## Screenshots

*Replace these placeholders with your actual screenshots.*

### Web Terminal

![Web Terminal](https://placehold.co/800x420/0c0c10/00ff88?text=Web+Terminal)

### System Dashboard

![System Dashboard](https://placehold.co/800x420/0c0c10/4488ff?text=System+Dashboard)

### File Manager

![File Manager](https://imgur.com/a/7ChWp2g)


---

## Configuration

VPS Core reads `vpscore.yml` from the working directory.  
If the file doesn't exist, it's generated on first launch.

### Port Mapping

With **only 2 allocated ports** (e.g. `50906` and `50398`):

| Index | Port  | Service                        |
|-------|-------|--------------------------------|
| [0]   | 50906 | Web UI + REST API + Prometheus |
| [1]   | 50398 | SSH + SFTP                     |

If more ports are available, Telnet, WebDAV, and proxy tunnels are enabled automatically.

### Minimal Config Example

```yaml
# vpscore.yml
mode: standalone

shell:
  web_terminal_port: 50906
  web_terminal_enabled: true
  ssh_port: 50398
  ssh_enabled: true

network:
  enable: true
  firewall_enable: true

filesystem:
  sftp_enabled: true
  sftp_port: 50398
  backups_enable: true
```

### CLI Arguments

| Argument | Description |
|----------|-------------|
| `--standalone` | Full VPS mode (default) |
| `--minimal` | Shell + files only, no network |
| `--config <path>` | Custom config path |
| `--pterodactyl-ports <ports>` | Manually specify allocated ports (comma-separated) |

---

## Usage

### Pterodactyl Console Commands

```
exec curl -s ifconfig.me          # Run any command
ps                                # List managed processes
sysinfo                           # System information
fs cat /home/container/file.txt   # Read file contents
stop                              # Shutdown
```

### Web Terminal

Open `http://<server-ip>:50906` — full shell in your browser.

### SSH

```bash
ssh root@<server-ip> -p 50398
```

Default password: `vpscore` (change in config).

### SFTP

```bash
sftp -P 50398 root@<server-ip>
```

---

## Services Reference

| Service | Port  | Enabled | Notes |
|---------|-------|---------|-------|
| Web UI + API  | 50906  | ✅ | Main interface |
| SSH | 50398   | ✅    | Shell access            |
| SFTP | 50398  | ✅ | Over SSH               |
| Prometheus    | 50906 | ✅ | Shares web port  |
| Desktop (VNC) | - | 🚧 | Work in progress  |

---

## Build from Source

```bash
git clone https://github.com/user/vpscore.git
cd vpscore
./gradlew fatJar
```

Output: `build/libs/vpscore-1.0.0-all.jar`

---



---

## License

MIT — see [LICENSE](LICENSE).

---

<p align="center">
  <sub>Built for Pterodactyl • Runs everywhere Java 21 is available</sub>
</p>
