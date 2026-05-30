# VPS Core - Custom Pterodactyl Docker Image
# Based on Pterodactyl Java 21 yolk with additional tools

FROM ghcr.io/pterodactyl/yolks:java_21

LABEL maintainer="VPS Core Team <vpscore@io>"
LABEL description="VPS Core - Turn Pterodactyl into a VPS"

USER root

# Install additional system tools required by VPS Core
RUN apt-get update -y \
    && apt-get install -y --no-install-recommends \
        curl \
        wget \
        netcat-openbsd \
        net-tools \
        iproute2 \
        iptables \
        dnsutils \
        telnet \
        ssh \
        openssh-client \
        openssh-server \
        wireguard-tools \
        zip \
        unzip \
        gzip \
        tar \
        tmux \
        screen \
        htop \
        nano \
        vim \
        bash-completion \
        ca-certificates \
        jq \
        sqlite3 \
        cron \
    && rm -rf /var/lib/apt/lists/*

# Create necessary directories
RUN mkdir -p /home/container/logs /home/container/backups /home/container/bin

# Copy entrypoint script
COPY --chown=container:container pterodactyl/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

USER container
ENV USER=container HOME=/home/container

WORKDIR /home/container

ENTRYPOINT ["/entrypoint.sh"]
