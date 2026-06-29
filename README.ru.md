# MultiServerSync

[![License](https://img.shields.io/badge/license-MIT-green?style=flat)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KoopinSmall/MultiServerSync?style=flat)](https://github.com/KoopinSmall/MultiServerSync/releases)
![Java](https://img.shields.io/badge/Java-17+-orange?style=flat&logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-1.16+-blue?style=flat)
![Velocity](https://img.shields.io/badge/Velocity-3.4.x-blue?style=flat)
![Redis](https://img.shields.io/badge/Redis-backed-red?style=flat&logo=redis&logoColor=white)

[English](README.md) · **Русский**

Слой синхронизации на Redis для Minecraft-сети, где Velocity-прокси стоят перед Paper-бэкендами.

Я написал это для своей сети и примерно три года это гонял, ломал и допиливал. Всё это время оно крутилось в проде, и на пике держало синхронизацию между несколькими проксями при 1000+ онлайна, не падая. В этом репозитории — тот самый код, причёсанный для публичного использования.

Идея простая: каждый прокси и каждый бэкенд общаются с одним Redis, и этого достаточно, чтобы они находили друг друга и делили общее состояние. Бэкенды регистрируют себя сами при запуске, так что в `velocity.toml` лезть, чтобы добавить сервер, не нужно вообще. Сверху — трекинг игроков между серверами, диспетчер команд, который дотягивается до любого сервера в сети, плейсхолдеры с онлайном и небольшой типизированный pub/sub-фреймворк (`SyncBus`), если хочешь писать свою кросс-серверную логику.

## Почему не просто velocity.toml

Потому что статичный список серверов не вяжется с тем, как сеть живёт на самом деле. Когда в час пик поднимаешь ещё один `anarchy`-сервер, совсем не хочется править TOML на каждой проксе и делать reload. Здесь бэкенд просто заявляет о себе в Redis при старте, каждый прокси его подхватывает, и он так же исчезает в момент выключения. То же с локацией игрока: когда проксей несколько, «на каком сервере сейчас этот игрок» — вопрос, на который одна прокся ответить не может, поэтому ответ лежит в Redis, где его видят все.

## Модули

Это Maven-реактор из трёх модулей:

| Модуль | Артефакт | Что делает |
|---|---|---|
| `core` | `uz.koopin:mss-core` | Общая библиотека, от которой зависят оба плагина — менеджеры, DTO сообщений, storage-записи и фреймворк `SyncBus`. |
| `proxy` | `uz.koopin:mss-proxy` | Плагин для Velocity. Регистрирует/снимает бэкенды на лету, трекает игроков, рассылает команды, даёт `/vsync`. |
| `paper` | `uz.koopin:mss-paper` | Плагин для Paper. Заявляет о бэкенде, публикует свой онлайн, даёт `/sync` и плейсхолдеры PlaceholderAPI. |

Грубо сеть выглядит так:

```
         ┌──────────────┐        ┌──────────────┐
         │  Velocity #1 │        │  Velocity #2 │   проксей сколько угодно
         │ (mss-proxy)  │        │ (mss-proxy)  │
         └──────┬───────┘        └──────┬───────┘
                │                       │
                └───────────┬───────────┘
                            │  Redis (pub/sub + hashes)
        ┌───────────────────┼───────────────────┐
        │                   │                   │
 ┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐
 │   Paper     │     │   Paper     │     │   Paper     │   бэкенды регистрируют себя сами
 │ (mss-paper) │     │ (mss-paper) │     │ (mss-paper) │
 └─────────────┘     └─────────────┘     └─────────────┘
```

Всё внутри одной сети делит один Redis и одно имя `project`. В деле два механизма, и они намеренно разведены:

- **Состояние, которое читаешь по запросу**, лежит в хешах Redis (`managers/`): реестр серверов (`mss:server:*`), локации игроков (`mss:location:*`), счётчики онлайна (`mss:online:*`) плюс универсальное хранилище по сущностям с опциональным TTL (`DataManager`).
- **События, на которые реагируешь**, идут через Redis pub/sub: сообщения о жизненном цикле серверов и командах через встроенный `MessageBroker`, и твои собственные типизированные пакеты через `SyncBus`.

Хеши — под общее состояние, pub/sub — под события. Один раз попробовал слить их в одно; не надо, это разные задачи.

## Что нужно

- Java 17+
- Redis, до которого дотягивается каждый прокси и бэкенд
- Velocity 3.4.x (прокси)
- Paper 1.16+ (бэкенд)
- PlaceholderAPI, если нужны плейсхолдеры `%mss_*%` — опционально

Оба jar'а собраны как самодостаточные shaded-jar, ставить дополнительные плагины не надо.

## Сборка

Готовые jar'ы прикреплены к каждому [релизу](https://github.com/KoopinSmall/MultiServerSync/releases) — если просто хочешь запустить, бери оттуда. Чтобы собрать из исходников, сначала надо установить `mss-core`, потому что плагины тянут его из локального репозитория:

```bash
mvn -pl core -am clean install

mvn -pl proxy -am clean package    # proxy/target/mss-proxy-<version>.jar
mvn -pl paper -am clean package    # paper/target/mss-paper-<version>.jar
```

Или просто `mvn clean package` и забрать все три.

## Установка

1. Подними Redis там, куда дотянется всё остальное.
2. Закинь `mss-proxy` в `plugins/` каждой Velocity.
3. Закинь `mss-paper` в `plugins/` каждого Paper.
4. Запусти каждый один раз, чтобы сгенерировался конфиг (`plugins/multi-server-sync/config.yml` на проксе, `plugins/MultiServerSync/config.yml` на бэкенде).
5. Направь оба на один Redis и дай им **одинаковый `project`** — это та самая настройка, что связывает сеть в одно целое.
6. Перезапусти. Бэкенды появятся на каждой проксе сами.

### Конфиг прокси

```yaml
project: koopin       # id сети, должен совпадать везде
proxy: proxy-1        # имя ЭТОЙ проксы, уникальное на инстанс

redis:
  host: "127.0.0.1"
  port: 6379
  password: "supersecret"

# опционально: именованные группы, чтобы бить командой сразу по нескольким серверам
server-groups:
  hub:
    - 'hub-1'
    - 'hub-2'
  anarchy:
    - 'anarchy-1'
    - 'anarchy-2'
```

Группа `hub` работает за двоих: это ещё и пул, куда игрока перекидывает, когда бэкенд его кикает, — чтобы не выбрасывать его из сети целиком.

### Конфиг бэкенда

```yaml
project: koopin       # должен совпадать с проксями

server:
  register: true      # false = плагин работает, но сервер не заявляет о себе проксям
  host: "auto"        # "auto" = вычислить исходящий IP; либо вписать IP/хостнейм вручную
  port: 0             # 0 = взять порт из server.properties

redis:
  host: "127.0.0.1"
  port: 6379
  password: "supersecret"
```

Момент, на котором спотыкаются: **имя бэкенда — это имя папки world-container** (`Bukkit.getWorldContainer()`). Так что называй директории серверов осмысленно — `hub-1`, `anarchy-2`, как угодно — потому что под этим именем проксы его регистрируют и по нему бьют команды и плейсхолдеры.

Если прокси на другой машине, чем бэкенд, поставь `server.host` в адрес, по которому прокси реально должна достучаться. `auto` определяет основной исходящий интерфейс и обходит `InetAddress.getLocalHost()`, который любит падать на Linux-машинах, чей хостнейм не прописан в `/etc/hosts`. Проверено на собственной шкуре.

## Запуск в Docker / Kubernetes

Запекать `config.yml` под конкретный инстанс в образ — мимо кассы: ты хочешь один образ и N реплик, у каждой своя идентичность. Поэтому любое значение конфига можно переопределить на старте через **system property** или **переменную окружения** — ровно то, что задаёшь per-pod в Deployment.

Порядок разрешения для любой настройки: сперва system property (`-Dmss.<key>`), потом переменная окружения (`MSS_<KEY>`), потом `config.yml`, потом встроенный дефолт. Имя переменной — это ключ с точками, переведённый в верхний регистр, где точки заменены на подчёркивания.

То, что почти всегда хочешь задавать на каждую реплику, — имя сервера/прокси:

| Настройка | System property | Переменная окружения | Сторона |
|---|---|---|---|
| id сети | `mss.project` | `MSS_PROJECT` | обе |
| имя прокси | `mss.proxy.name` | `MSS_PROXY_NAME` | прокси |
| имя бэкенда | `mss.server.name` | `MSS_SERVER_NAME` | бэкенд |
| саморегистрация | `mss.server.register` | `MSS_SERVER_REGISTER` | бэкенд |
| анонсируемый host | `mss.server.host` | `MSS_SERVER_HOST` | бэкенд |
| анонсируемый port | `mss.server.port` | `MSS_SERVER_PORT` | бэкенд |
| Redis host | `mss.redis.host` | `MSS_REDIS_HOST` | обе |
| Redis port | `mss.redis.port` | `MSS_REDIS_PORT` | обе |
| Redis password | `mss.redis.password` | `MSS_REDIS_PASSWORD` | обе |

Бэкенд в StatefulSet, например, может взять имя прямо из пода:

```yaml
env:
  - name: MSS_SERVER_NAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name      # например anarchy-0, anarchy-1, ...
  - name: MSS_REDIS_HOST
    value: "redis.default.svc.cluster.local"
  - name: MSS_REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: redis
        key: password
```

Когда задан `MSS_SERVER_NAME`, бэкенд перестаёт выводить имя из папки world-container, и один и тот же образ нормально масштабируется за Deployment или StatefulSet. Пароль Redis держи вне образа и подавай из Secret, как выше.

## Команды

Запустить команду на удалённом сервере можно с проксы (`/vsync`) или с бэкенда (`/sync`) — синтаксис одинаковый:

```
/vsync <target> <command>        # один сервер, или '*' / 'all' для всех
/vsync -g <group> <command>      # каждый сервер из группы в конфиге
```

```
/vsync hub-1 say Привет с проксы
/vsync all kick Steve
/vsync -g hub broadcast Рестарт через 5 минут
/sync anarchy-2 time set day
```

Право — `mss.command.sync`.

## Плейсхолдеры

При установленном PlaceholderAPI на бэкенде:

| Плейсхолдер | Что возвращает |
|---|---|
| `%mss_online%` | Всего игроков по всему проекту |
| `%mss_online_<server>%` | Игроки на конкретном сервере, напр. `%mss_online_hub-1%` |
| `%mss_online_@<group>%` | Игроки по всем серверам, в имени которых есть `<group>`, напр. `%mss_online_@hub%` |
| `%mss_server%` | Отображаемое имя текущего сервера игрока |

Счётчики кешируются примерно на 3 секунды, чтобы загруженный скорборд не долбил Redis.

## SyncBus

`SyncBus` — это та часть, которую реально захочется тащить в свои плагины. Типизированная pub/sub-шина, живёт в `mss-core` и ничего не знает про Velocity или Paper, так что использовать её можно где угодно на JVM.

Как работает:

- Один Redis-канал на шину.
- Пакет — это просто обычный объект. Gson его сериализует и помечает полным именем класса (`__class`), чтобы на другой стороне восстановить ровно тот же тип.
- У каждой шины есть `origin`, и свои же эхо-сообщения из канала она отбрасывает.
- `send(...)` — fire-and-forget; `request(...)` отдаёт `CompletableFuture` под ответ.
- Сама переподключается с экспоненциальным backoff, если Redis моргнул.

Пакет — это вот столько:

```java
// fire-and-forget — обрабатывается в onReceive() на каждом пире, кроме отправителя
public class AlertPacket implements SyncPacket {
    public String message;

    @Override
    public void onReceive() {
        SyncContext.current().get(MyService.class).broadcast(message);
    }
}

// запрос/ответ — что вернёшь из handle(), то и уйдёт обратно вызвавшему
public class PingPacket implements ReplyablePacket<PongPacket> {
    @Override
    public PongPacket handle() {
        return new PongPacket();
    }
}

public class PongPacket implements SyncPacket { }
```

Поднять шину:

```java
SyncBus bus = SyncBus.builder()
        .redis(redisCredentials)        // uz.koopin.mss.storage.RedisCredentials
        .channel("mss:myfeature:" + project)
        .origin("proxy-1")              // уникальный id этого процесса
        .build();

bus.send(new AlertPacket() {{ message = "Сервер рестартует"; }});

bus.request(new PingPacket())
   .thenAccept(pong -> log.info("получен ответ"));

bus.close();    // при выключении
```

Хендлеры достают твои сервисы через `SyncContext` — обычный process-wide service locator. Зарегистрируй что нужно один раз на старте:

```java
SyncContext.put(MyService.class, myServiceInstance);
```

Грабли, которые стоит повторить: имя класса пакета **и есть** его идентификатор в протоколе. Переименуешь или перенесёшь класс пакета — сломаешь каждый пир, который ещё крутит старое имя. Держи типы пакетов в общем модуле и относись к их переносу как к изменению протокола.

Регистрация бэкендов в этом плагине устроена ровно так: бэкенд заявляет о себе пакетом [`ServerRegisterPacket`](core/src/main/java/uz/koopin/mss/sync/packets/ServerRegisterPacket.java), а прокси реагирует через `BackendRegistry`, который кладёт в `SyncContext` — поэтому пакет не трогает код Velocity, а бэкенды, у которых registry нет, просто его игнорируют. См. [`core/.../sync/packets`](core/src/main/java/uz/koopin/mss/sync/packets).

## Что лежит в Redis

Если захочется поковыряться через `redis-cli`:

| Ключ / канал | Тип | Кто пишет | Что хранит |
|---|---|---|---|
| `mss:server:<name>` | hash | бэкенды / прокси | `project`, `address`, `whitelist` |
| `mss:location:<player>` | hash | прокси | `proxy`, `server`, `previousServer`, `project` |
| `mss:online:<project>` | hash | бэкенды | счётчики онлайна по серверам |
| `mss:<path>:<project>:<id>` | hash | `DataManager` | универсальное состояние по сущностям, опциональный TTL |
| `mss:server-events:<project>` | pub/sub | все | жизненный цикл серверов + сообщения команд |
| свой канал | pub/sub | твой код | пакеты `SyncBus` |

## Планы

Чего не буду скрывать: сейчас всё держится на одном Redis, так что эта нода — единственная точка отказа в сети. У меня годами всё стабильно, но «пока не падало» — это не стратегия отказоустойчивости.

Поэтому **в планах поддержка Redis Sentinel** — автоматический failover на реплику, если основная нода ляжет, без изменения того, как разложены данные. Вот в эту сторону я и хочу двигать надёжность.

И для протокола: в сторону Redis Cluster я намеренно *не* иду. Cluster решает шардинг (датасет не влезает в одну ноду), а у этих данных такой проблемы нет — локации, реестр серверов и счётчики помещаются в одну ноду с большим запасом. Реально стоит решать «не дать одному упавшему Redis утянуть за собой всю сеть», а это Sentinel, а не Cluster.

## Лицензия

MIT, см. [LICENSE](LICENSE). Делай с этим что хочешь.
