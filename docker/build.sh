#!/usr/bin/env bash
# Builds the reactor and drops freshly-shaded plugin jars into ./jars with
# stable, version-less names so compose mounts never need touching.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"

( cd "$root" && mvn clean package -DskipTests )

mkdir -p "$here/jars"
copy() { local src; src="$(ls "$root"/$1 | head -n1)"; cp -f "$src" "$here/jars/$2"; echo "  -> jars/$2  ($(basename "$src"))"; }
copy "velocity/target/mss-velocity-*.jar" mss-velocity.jar
copy "bungee/target/mss-bungee-*.jar" mss-bungee.jar
copy "paper/target/mss-paper-*.jar"   mss-paper.jar
echo "Done. Now: docker compose --profile velocity up   (or --profile bungee)"
