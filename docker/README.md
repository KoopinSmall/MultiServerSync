# Test stack (docker-compose)

A throwaway network for poking at MultiServerSync end-to-end: Redis, one proxy
(Velocity **or** BungeeCord, your pick), and a few self-registering Paper
backends. Built around the plugin's `MSS_*` env-overrides, so identity is
injected per-container — one image per role, no baked-in configs.

## Layout

```
docker/
  .env            # all the knobs (versions, ports, project, redis password)
  docker-compose.yml  # redis + proxies (profiles) + backends (YAML anchors)
  build.sh        # mvn package -> copy jars into ./jars
  velocity/velocity.toml   # legacy forwarding, lobby-1 pre-listed, rest dynamic
  bungee/config.yml        # legacy forwarding, lobby-1 pre-listed, rest dynamic
  backend/spigot.yml       # bungeecord: true (mounted into every backend)
  proxy-mss/config.yml     # plugin server-groups, mounted into the proxy
  jars/           # build output, git-ignored
```

## Run it

```bash
# 1. build the plugins and stage the jars (run from docker/)
./build.sh

# 2. bring up the stack with ONE proxy profile
docker compose --profile velocity up        # Velocity  -> localhost:25565
docker compose --profile bungee   up        # BungeeCord -> localhost:25566
```

Redis and all backends start regardless of profile (they have no profile); the
`--profile` flag only picks which proxy comes up. Run one proxy at a time.

After a code change: `./build.sh` again, then
`docker compose restart velocity hub-1 ...` — no image rebuild, the jars are
bind-mounted.

## What to check

```bash
# backends registered themselves + player locations land in Redis
docker compose exec redis redis-cli -a supersecret --scan --pattern 'mss:*'
docker compose exec redis redis-cli -a supersecret hgetall mss:server:lobby-1

# cross-server command dispatch — type straight into a server console.
# Services run with stdin_open + tty, so `docker attach` gives you a live prompt
# (start the stack detached first: `docker compose --profile velocity up -d`):
docker attach mss-test-velocity-1     # then type, e.g.:
#   Velocity   console:  /vsync all say hi      /vsync -g lobby say lobby-only
#   BungeeCord console:  /bsync all say hi      /bsync -g lobby say lobby-only
#   backend    console:  attach mss-test-lobby-1-1 -> /sync survival-1 time set day
# Ctrl-P then Ctrl-Q detaches WITHOUT stopping the container (don't use Ctrl-C).
```

The `-g lobby` group (lobby-1 + lobby-2) is defined in `proxy-mss/config.yml`.

Join `localhost:25565` (Velocity) or `localhost:25566` (BungeeCord) with an
offline client, then walk between servers with `/server lobby-2`, `/server
survival-1` — `mss:location:<you>` should update.

## Adding a backend

Copy any `lobby-*` block in `docker-compose.yml`, change the name in the three spots
(`service:`, `MSS_SERVER_NAME`, `MSS_SERVER_HOST`) and add a volume. It
self-registers — no proxy config change needed. To target it as a group, add
it to `proxy-mss/config.yml`; to reach it on first join, `/server <name>`
in-game or add it to the proxy's pre-list.

## Caveats

- **Forwarding is `legacy`** on purpose: it's the one mode both proxies share,
  so the same backends work under either. It is *not* a secure production
  setup (offline + legacy) — this is a test bench, not a deployment.
- Image tags (`itzg/minecraft-server`, `itzg/bungeecord`) and `MC_VERSION`
  track upstream; if a forwarding handshake breaks after a version bump, that's
  the first place to look.
- Wipe state with `docker compose --profile velocity --profile bungee down -v`
  (the `-v` drops the per-backend world volumes).
