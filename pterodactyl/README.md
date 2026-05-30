# VPS Core for Pterodactyl

Превращает ваш Pterodactyl сервер в полноценный VPS.

## Установка через Egg (рекомендуется)

1. Скачайте `egg-vps-core.json`
2. В Pterodactyl панели: **Admin → Nests → Import Egg**
3. Создайте новый сервер с этим egg
4. Настройте переменные:
   - `VPS_MODE` = `standalone`
   - `VPS_PASSWORD` = ваш пароль
   - Остальные порты — по желанию
5. Запустите сервер

## Установка в существующий сервер

```bash
# В консоли Pterodactyl сервера:
curl -sL https://github.com/user/vpscore/releases/latest/download/vpscore-all.jar -o vpscore.jar
java -jar vpscore.jar --standalone
```

## Доступ

| Сервис | Порт (по умолчанию) |
|--------|-------------------|
| SSH    | 8022              |
| Telnet | 8023              |
| Web    | 8080              |
| SFTP   | 8024              |
| WebDAV | 8025              |
| Metrics| 9090              |

## Команды

- `/vps shell <cmd>` — выполнить команду (Telegram/Discord)
- `/vps stats` — метрики системы
- `/vps fs ls /path` — список файлов
- `/vps restart` — перезагрузка

## Кастомный Docker образ

```bash
docker build -f pterodactyl/Dockerfile -t ghcr.io/user/vpscore:latest .
docker push ghcr.io/user/vpscore:latest
```

В egg замените `docker_images` на ваш образ.
