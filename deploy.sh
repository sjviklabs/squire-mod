#!/usr/bin/env bash
set -euo pipefail

# Deploy squire mod to Minecraft server (LXC 111)
# Usage: ./deploy.sh [--restart]

MC_HOST="minecraft"  # SSH config alias
MOD_DIR="/opt/minecraft/stoneblock4/mods"
SERVICE="minecraft-stoneblock4"

# Build
echo "Building..."
./gradlew build -q

# Find jar (exclude sources jar)
JAR=$(find build/libs -name 'squire-*.jar' ! -name '*-sources*' | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: No jar found in build/libs/"
    exit 1
fi

VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
echo "Deploying squire-${VERSION} (${JAR})"

# Remove old squire jars on server
ssh "$MC_HOST" "rm -f ${MOD_DIR}/squire-*.jar"

# Copy new jar
scp "$JAR" "${MC_HOST}:${MOD_DIR}/"
echo "Copied to ${MC_HOST}:${MOD_DIR}/"

# Restart if requested
if [[ "${1:-}" == "--restart" ]]; then
    echo "Restarting ${SERVICE}..."
    ssh "$MC_HOST" "systemctl restart ${SERVICE}"
    echo "Server restarted."
else
    echo "Deployed. Restart server manually or run: ./deploy.sh --restart"
fi

echo "Done."
