# MultiServerSync

[![License](https://img.shields.io/badge/license-MIT-green?style=flat)](LICENSE)
[![Release](https://img.shields.io/github/v/release/KoopinSmall/MultiServerSync?style=flat)](https://github.com/KoopinSmall/MultiServerSync/releases)
![Java](https://img.shields.io/badge/Java-17+-orange?style=flat&logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-1.16+-blue?style=flat)
![Velocity](https://img.shields.io/badge/Velocity-3.4.x-blue?style=flat)
![BungeeCord](https://img.shields.io/badge/BungeeCord-1.21-blue?style=flat)
![Redis](https://img.shields.io/badge/Redis-backed-red?style=flat&logo=redis&logoColor=white)

**English** · [Русский](README.ru.md)

A Redis-backed sync layer for a Minecraft network running Velocity or BungeeCord proxies in front of Paper backends.

I built this for my own network and have been running, breaking and refining it for about three years. It's been in production the whole time, and at its peak it kept things in sync across multiple proxies at 1000+ concurrent players without falling over. This repo is that code, cleaned up for public use.

The core idea: every proxy and backend talks to one Redis, and that's enough for them to discover each other and share state. Backends register themselves when they start, so you never touch `velocity.toml` to add a server. On top of that you get cross-server player tracking, a command dispatcher that reaches any server in the network, online-count placeholders, and a small typed pub/sub framework (`SyncBus`) if you want to build your own cross-server logic.

## Why I didn't just use velocity.toml

Because a static server list doesn't scale with how you actually run a network. When you spin up another `anarchy` box during peak hours, you don't want to edit a TOML file on every proxy and reload. Here a backend just announces itself to Redis on startup, every proxy picks it up, and it's gone again the moment it shuts down. Same goes for player location: with several proxies, "which server is this player on right now" isn't a question any single proxy can answer, so it lives in Redis where everyone can see it.

## Modules

It's a Maven reactor with four modules:

| Module | Artifact | What it does |
|---|---|---|
| `core` | `uz.koopin:mss-core` | The shared library every plugin depends on — managers, message DTOs, storage records, and the `SyncBus` framework. |
| `proxy` | `uz.koopin:mss-proxy` | Velocity plugin. Registers/unregisters backends at runtime, tracks players, dispatches commands, ships `/vsync`. |
| `bungee` | `uz.koopin:mss-bungee` | BungeeCord plugin. Same behaviour as `proxy` on the BungeeCord API — registers/unregisters backends, tracks players, dispatches commands, ships `/bsync`. |
| `paper` | `uz.koopin:mss-paper` | Paper plugin. Announces the backend, publishes its online count, ships `/sync` and the PlaceholderAPI placeholders. |

`proxy` and `bungee` are interchangeable — pick whichever matches your proxy software. They share the same `core`, the same Redis layout and the same config, so a Velocity proxy and a BungeeCord proxy can sit in the same network and see each other's backends.

Roughly, the network looks like this:

```
         ┌──────────────┐        ┌──────────────┐
         │  Velocity #1 │        │  Velocity #2 │   any number of proxies
         │ (mss-proxy)  │        │ (mss-proxy)  │
         └──────┬───────┘        └──────┬───────┘
                │                       │
                └───────────┬───────────┘
                            │  Redis (pub/sub + hashes)
        ┌───────────────────┼───────────────────┐
        │                   │                   │
 ┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐
 │   Paper     │     │   Paper     │     │   Paper     │   backends self-register
 │ (mss-paper) │     │ (mss-paper) │     │ (mss-paper) │
 └─────────────┘     └─────────────┘     └─────────────┘
```

Everything in one network shares a single Redis and a single `project` name. There are two mechanisms in play, and they're deliberately kept separate:

- **State you read on demand** lives in Redis hashes (`managers/`): the server registry (`mss:server:*`), player locations (`mss:location:*`), online counts (`mss:online:*`), plus a generic per-entity store with optional TTL (`DataManager`).
- **Events you react to** go over Redis pub/sub: server-lifecycle and command messages through the built-in `MessageBroker`, and your own typed packets through `SyncBus`.

Hashes for shared state, pub/sub for events. I tried collapsing them into one once; don't, they solve different problems.

## Requirements

- Java 17+
- A Redis instance every proxy and backend can reach
- Velocity 3.4.x **or** BungeeCord 1.21 (proxy — pick one per instance)
- Paper 1.16+ (backend)
- PlaceholderAPI if you want the `%mss_*%` placeholders — optional

All jars are shaded and self-contained, no extra plugins to install.

## Building

Prebuilt jars are attached to every [release](https://github.com/KoopinSmall/MultiServerSync/releases) — grab those if you just want to run it. To build from source, `mss-core` has to be installed first since the plugins resolve it from your local repo:

```bash
mvn -pl core -am clean install

mvn -pl proxy -am clean package    # proxy/target/mss-proxy-<version>.jar
mvn -pl bungee -am clean package   # bungee/target/mss-bungee-<version>.jar
mvn -pl paper -am clean package    # paper/target/mss-paper-<version>.jar
```

Or just `mvn clean package` and grab them all.

## Setup

1. Get Redis running somewhere everything can reach.
2. Put `mss-proxy` in each Velocity's `plugins/` — or `mss-bungee` in each BungeeCord (or Waterfall) proxy's `plugins/`.
3. Put `mss-paper` in each Paper's `plugins/`.
4. Start each once to generate its config (`plugins/multi-server-sync/config.yml` on either proxy, `plugins/MultiServerSync/config.yml` on the backend).
5. Point both at the same Redis and give them the **same `project`** — that's the one setting that ties a network together.
6. Restart. Backends show up on every proxy on their own.

### Proxy config

```yaml
project: koopin       # network id, must match everywhere
proxy: proxy-1        # name of THIS proxy, unique per instance

redis:
  host: "127.0.0.1"
  port: 6379
  password: "supersecret"

# optional: named groups so you can target several servers with one command
server-groups:
  hub:
    - 'hub-1'
    - 'hub-2'
  anarchy:
    - 'anarchy-1'
    - 'anarchy-2'
```

The `hub` group does double duty: it's also the pool a player gets bounced to when a backend kicks them, instead of being dropped from the network entirely.

The BungeeCord plugin (`mss-bungee`) reads the **exact same config** at `plugins/multi-server-sync/config.yml` — same keys, same meaning. The only user-visible difference is the command name (`/bsync` instead of `/vsync`, see below).

### Backend config

```yaml
project: koopin       # has to match the proxies

server:
  register: true      # false = run the plugin but don't announce this server to proxies
  host: "auto"        # "auto" = figure out the outbound IP; or hardcode an IP/hostname
  port: 0             # 0 = take the port from server.properties

redis:
  host: "127.0.0.1"
  port: 6379
  password: "supersecret"
```

One thing that trips people up: the backend's **name is its world-container folder name** (`Bukkit.getWorldContainer()`). So name your server directories something meaningful — `hub-1`, `anarchy-2`, whatever — because that's the name proxies register it under and what commands and placeholders target.

If the proxy lives on a different box than the backend, set `server.host` to the address the proxy should actually dial. `auto` resolves the primary outbound interface and skips `InetAddress.getLocalHost()`, which loves to throw on Linux boxes whose hostname isn't in `/etc/hosts`. Learned that one the hard way.

## Running in Docker / Kubernetes

Baking a per-instance `config.yml` into an image defeats the point — you want one image and N replicas, each with its own identity. So every config value can be overridden at startup by a **system property** or an **environment variable**, which is exactly what you set per-pod in a Deployment.

Resolution order for any setting is: system property (`-Dmss.<key>`) first, then environment variable (`MSS_<KEY>`), then `config.yml`, then the built-in default. The env var name is the dotted key uppercased with dots turned into underscores.

The one you'll always want to set per replica is the server/proxy name:

| Setting | System property | Environment variable | Side |
|---|---|---|---|
| Network id | `mss.project` | `MSS_PROJECT` | both |
| Proxy name | `mss.proxy.name` | `MSS_PROXY_NAME` | proxy |
| Backend name | `mss.server.name` | `MSS_SERVER_NAME` | backend |
| Self-register | `mss.server.register` | `MSS_SERVER_REGISTER` | backend |
| Advertised host | `mss.server.host` | `MSS_SERVER_HOST` | backend |
| Advertised port | `mss.server.port` | `MSS_SERVER_PORT` | backend |
| Redis host | `mss.redis.host` | `MSS_REDIS_HOST` | both |
| Redis port | `mss.redis.port` | `MSS_REDIS_PORT` | both |
| Redis password | `mss.redis.password` | `MSS_REDIS_PASSWORD` | both |

A backend in a StatefulSet, for example, can take its name straight from the pod:

```yaml
env:
  - name: MSS_SERVER_NAME
    valueFrom:
      fieldRef:
        fieldPath: metadata.name      # e.g. anarchy-0, anarchy-1, ...
  - name: MSS_REDIS_HOST
    value: "redis.default.svc.cluster.local"
  - name: MSS_REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: redis
        key: password
```

With `MSS_SERVER_NAME` set, the backend stops deriving its name from the world-container folder, so the same image scales cleanly behind a Deployment or StatefulSet. Keep the Redis password out of the image and feed it from a Secret like above.

## Commands

Run a command on a remote server from a Velocity proxy (`/vsync`), a BungeeCord proxy (`/bsync`), or from a backend (`/sync`) — same syntax either way:

```
/vsync <target> <command>        # one server, or '*' / 'all' for everything
/vsync -g <group> <command>      # every server in a config group
```

```
/vsync hub-1 say Hello from the proxy
/vsync all kick Steve
/vsync -g hub broadcast Restarting in 5 minutes
/bsync hub-2 say Hello from the BungeeCord proxy
/sync anarchy-2 time set day
```

Permission node is `mss.command.sync` for all three.

## Placeholders

With PlaceholderAPI on the backend:

| Placeholder | Returns |
|---|---|
| `%mss_online%` | Total players across the whole project |
| `%mss_online_<server>%` | Players on one server, e.g. `%mss_online_hub-1%` |
| `%mss_online_@<group>%` | Players across every server whose name contains `<group>`, e.g. `%mss_online_@hub%` |
| `%mss_server%` | A display name for the player's current server |

Counts are cached for about 3 seconds so a busy scoreboard doesn't hammer Redis.

## SyncBus

`SyncBus` is the part you'd actually pull into your own plugins. It's a typed pub/sub bus that lives in `mss-core` and knows nothing about Velocity or Paper, so you can use it anywhere on the JVM.

How it works:

- One Redis channel per bus.
- A packet is just a plain object. Gson serializes it and tags it with its fully-qualified class name (`__class`) so the other side rebuilds the exact type.
- Each bus has an `origin` id and drops its own echoes off the channel.
- `send(...)` is fire-and-forget; `request(...)` gives you a `CompletableFuture` for the reply.
- It reconnects on its own with exponential backoff if Redis blips.

A packet is as simple as:

```java
// fire-and-forget — handled in onReceive() on every peer except the sender
public class AlertPacket implements SyncPacket {
    public String message;

    @Override
    public void onReceive() {
        SyncContext.current().get(MyService.class).broadcast(message);
    }
}

// request/response — whatever you return from handle() goes back to the caller
public class PingPacket implements ReplyablePacket<PongPacket> {
    @Override
    public PongPacket handle() {
        return new PongPacket();
    }
}

public class PongPacket implements SyncPacket { }
```

Wiring up a bus:

```java
SyncBus bus = SyncBus.builder()
        .redis(redisCredentials)        // uz.koopin.mss.storage.RedisCredentials
        .channel("mss:myfeature:" + project)
        .origin("proxy-1")              // unique id for this process
        .build();

bus.send(new AlertPacket() {{ message = "Server restarting"; }});

bus.request(new PingPacket())
   .thenAccept(pong -> log.info("got a reply"));

bus.close();    // on shutdown
```

Handlers get at your services through `SyncContext`, a plain process-wide service locator. Register what you need once at startup:

```java
SyncContext.put(MyService.class, myServiceInstance);
```

One gotcha worth repeating: a packet's class name **is** its wire identifier. Rename or move a packet class and you've broken every peer still running the old name. Keep packet types in a shared module and treat moving them like a protocol change.

This plugin's own backend registration works exactly this way: a backend announces itself with [`ServerRegisterPacket`](core/src/main/java/uz/koopin/mss/sync/packets/ServerRegisterPacket.java), and the proxy reacts through a `BackendRegistry` it puts into `SyncContext` — so the packet never touches Velocity code and backends, having no registry, simply ignore it. See [`core/.../sync/packets`](core/src/main/java/uz/koopin/mss/sync/packets).

## What's in Redis

If you ever want to poke around with `redis-cli`:

| Key / channel | Type | Written by | Holds |
|---|---|---|---|
| `mss:server:<name>` | hash | backends / proxy | `project`, `address`, `whitelist` |
| `mss:location:<player>` | hash | proxy | `proxy`, `server`, `previousServer`, `project` |
| `mss:online:<project>` | hash | backends | per-server online counts |
| `mss:<path>:<project>:<id>` | hash | `DataManager` | generic per-entity state, optional TTL |
| `mss:server-events:<project>` | pub/sub | everyone | server lifecycle + command messages |
| custom channel | pub/sub | your code | `SyncBus` packets |

## Roadmap

The one thing I'd call out honestly: right now everything hangs off a single Redis, so that node is the network's single point of failure. It's been rock-solid for me over the years, but "it hasn't fallen over yet" isn't a high-availability strategy.

So **Redis Sentinel support is planned** — automatic failover to a replica if the primary goes down, with no change to how the data is laid out. That's the direction I want to take reliability.

For the record, I'm deliberately *not* going toward Redis Cluster. Cluster solves sharding (dataset too big for one node), which isn't a problem this kind of data has — locations, a server registry and some counters fit in one node with room to spare. The thing actually worth solving is "don't let one Redis dying take the network with it", and that's Sentinel, not Cluster.

## License

MIT, see [LICENSE](LICENSE). Do what you want with it.
