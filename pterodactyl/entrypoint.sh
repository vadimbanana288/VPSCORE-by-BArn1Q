#!/bin/bash
# VPS Core Entrypoint for Pterodactyl
# Запускается при старте контейнера

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}  VPS Core for Pterodactyl${NC}"
echo -e "${CYAN}  Starting container...${NC}"
echo -e "${CYAN}============================================${NC}"

# Проверка наличия vpscore.jar
if [ ! -f /home/container/vpscore.jar ]; then
    echo -e "${RED}[ERROR] vpscore.jar not found in /home/container${NC}"
    echo -e "${YELLOW}Please reinstall the egg or place vpscore.jar manually${NC}"
    sleep 10
    exit 1
fi

# Отображаем MOTD
if [ -f /home/container/bin/motd.sh ]; then
    bash /home/container/bin/motd.sh
fi

# Определяем режим запуска
MODE=${VPS_MODE:-standalone}
echo -e "${GREEN}[VPS Core] Starting in mode: $MODE${NC}"
echo -e "${GREEN}[VPS Core] Version: $(java -jar /home/container/vpscore.jar --version 2>/dev/null || echo 'unknown')${NC}"

# Вычисляем память
if [ -n "$SERVER_MEMORY" ]; then
    MEMORY="$SERVER_MEMORY"
    echo -e "${GREEN}[VPS Core] Allocated memory: ${MEMORY}MB${NC}"
else
    MEMORY=1024
    echo -e "${YELLOW}[VPS Core] SERVER_MEMORY not set, using default: ${MEMORY}MB${NC}"
fi

# Определяем доп. аргументы JVM
JVM_ARGS="${VPS_ARGS:-}"

# Запуск VPS Core
echo -e "${CYAN}[VPS Core] Executing: java -Xms${MEMORY}M -Xmx${MEMORY}M ${JVM_ARGS} -jar vpscore.jar --${MODE}${NC}"
echo ""

cd /home/container

# Исполняем команду из STARTUP (переданную Pterodactyl)
# Если STARTUP не задана, используем стандартную
if [ -n "$STARTUP" ]; then
    echo -e "${GREEN}[VPS Core] Using STARTUP command from Pterodactyl${NC}"
    eval "${STARTUP}"
else
    exec java -Xms${MEMORY}M -Xmx${MEMORY}M ${JVM_ARGS} -jar vpscore.jar --${MODE}
fi
