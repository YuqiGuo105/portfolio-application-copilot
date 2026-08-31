#!/bin/zsh
set -eu

if [[ $# -ne 1 ]]; then
  print -u2 "Usage: $0 <chrome-extension-id>"
  exit 2
fi

ROOT="${0:A:h:h}"
EXTENSION_ID="$1"
HOST_DIR="$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
HOST_FILE="$HOST_DIR/site.yuqi.application_copilot.json"

"$ROOT/mvnw" -q -DskipTests package
chmod 755 "$ROOT/scripts/run-native-host.sh"
mkdir -p "$HOST_DIR"
cat > "$HOST_FILE" <<EOF
{
  "name": "site.yuqi.application_copilot",
  "description": "Local Codex advisor for Yuqi Application Copilot",
  "path": "$ROOT/scripts/run-native-host.sh",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$EXTENSION_ID/"]
}
EOF
chmod 600 "$HOST_FILE"
print "Installed $HOST_FILE"
