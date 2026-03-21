#!/usr/bin/env bash
set -euo pipefail

# Deploy squire mod to Minecraft servers (LXC 111) and local client
# Usage: ./deploy.sh [--restart] [server]
#   server: minecolonies (default), stoneblock4, all
#   --restart: restart the server(s) after deploy

MC_HOST="minecraft"  # SSH config alias — must point to 192.168.10.36
CLIENT_DIR="$LOCALAPPDATA/.ftba/instances/minecolonies official/mods"

declare -A SERVERS=(
    [minecolonies]="/opt/minecraft/minecolonies/mods|minecraft-minecolonies"
    [stoneblock4]="/opt/minecraft/stoneblock4/mods|minecraft-stoneblock4"
)

RESTART=false
TARGET="minecolonies"

for arg in "$@"; do
    case "$arg" in
        --restart) RESTART=true ;;
        minecolonies|stoneblock4|all) TARGET="$arg" ;;
    esac
done

# Build
echo "Building..."
./gradlew build -q

# Find jar (exclude sources jar)
JAR=$(find build/libs -name 'squire-*.jar' ! -name '*-sources*' -newer build/libs | sort -r | head -1)
if [ -z "$JAR" ]; then
    JAR=$(find build/libs -name 'squire-*.jar' ! -name '*-sources*' | sort -r | head -1)
fi
if [ -z "$JAR" ]; then
    echo "ERROR: No jar found in build/libs/"
    exit 1
fi

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
echo "Deploying squire-${VERSION} ($(basename "$JAR"))"

deploy_server() {
    local name="$1"
    local mod_dir="${SERVERS[$name]%%|*}"
    local service="${SERVERS[$name]##*|}"

    echo "  Server: $name ($mod_dir)"
    ssh "$MC_HOST" "rm -f ${mod_dir}/squire-*.jar"
    scp "$JAR" "${MC_HOST}:${mod_dir}/"

    if $RESTART; then
        echo "  Restarting ${service}..."
        ssh "$MC_HOST" "systemctl restart ${service}"
    fi
}

# Deploy to server(s)
if [ "$TARGET" = "all" ]; then
    for srv in "${!SERVERS[@]}"; do
        deploy_server "$srv"
    done
else
    deploy_server "$TARGET"
fi

# Deploy to local client
if [ -d "$CLIENT_DIR" ]; then
    rm -f "$CLIENT_DIR"/squire-*.jar
    cp "$JAR" "$CLIENT_DIR/"
    echo "  Client: $(basename "$CLIENT_DIR") updated"
else
    echo "  Client: mods dir not found, skipping"
fi

echo "Done."
