# Yggdrasil P2P Chat

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

Децентрализованный peer-to-peer мессенджер поверх сети [Yggdrasil](https://github.com/yggdrasil-network/yggdrasil-go). Прямое соединение между устройствами без центральных серверов.

Каждому устройству Yggdrasil выдаёт статический IPv6-адрес в mesh-сети. Приложение подключается напрямую к этому адресу через QUIC — без серверов, DNS и NAT-пробросов.

## Требования

- **Java 21** или новее
- **[Yggdrasil](https://github.com/yggdrasil-network/yggdrasil-go)** — запущен на обоих устройствах

## Установка

**Windows:** скачайте готовый `.exe` из [Releases](https://github.com/bulka808/yggdrasil-p2p-chat/releases).

**Linux / macOS / сборка из исходников:**

```bash
./gradlew build
```

**JAR:** `build/libs/yggdrasil-p2p-chat-1.0-SNAPSHOT.jar`

Сборка нативного установщика через `jpackage` (опционально):

```bash
./gradlew jpackageApp
```

## Запуск

1. Запустите Yggdrasil на обоих устройствах.
2. Запустите приложение:
   ```bash
   java -jar build/libs/yggdrasil-p2p-chat-1.0-SNAPSHOT.jar
   ```
3. При первом запуске адрес определяется автоматически из Yggdrasil API.
4. Добавьте пира по его Yggdrasil IPv6-адресу.

## Возможности

- Текстовые сообщения и отправка изображений
- Автопереподключение при разрыве соединения
- Звуковые уведомления
- Валидация отправителя (защита от подделки `from`)
- Структурированные настройки (профиль, лимиты, техническая часть)

## Конфигурация

`config.yaml` создаётся автоматически:

```yaml
yggdrasil:
  host: localhost
  port: 9001
server_port: 4433
own_address: "200:..."
display_name: "User_a1b2c3d4"
limits:
  image_size_mb: 10
  file_size_mb: 100
  storage_limit_mb: 4096
database:
  path: chat.db
debug_logging: false
```

## Архитектура

- **Kotlin + Compose Desktop** — UI
- **Java** — сеть и хранилище
- **QUIC** через kwik — мультиплексированные потоки
- **MsgPack** — сериализация сообщений
- **SQLite** — локальная база
- **BouncyCastle** — самоподписанные сертификаты

Yggdrasil сам по себе обеспечивает end-to-end шифрование всего трафика в сети (XSalsa20-Poly1305 / Curve25519), промежуточные узлы не могут прочитать содержимое. Самоподписанные сертификаты используются как техническая заглушка — QUIC требует TLS-рукопожатие на уровне соединения, а реальная защита обеспечивается сетью Yggdrasil.

IPv6-адрес в Yggdrasil криптографически привязан к публичному ключу узла, что делает невозможной подмену адреса отправителя.

## Roadmap

- Голосовые сообщения
- Групповые чаты
- Импорт/экспорт истории
- CLI-режим (без GUI)

## Лицензия

MIT
