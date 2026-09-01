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
INSTALL_DIR="$HOME/Library/Application Support/Yuqi Application Copilot"
HOST_JAR="$INSTALL_DIR/portfolio-application-copilot.jar"
HOST_LAUNCHER="$INSTALL_DIR/run-native-host.sh"

"$ROOT/mvnw" -q -DskipTests clean package
JAR_SOURCE="$(find "$ROOT/target" -maxdepth 1 -name 'portfolio-application-copilot-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$JAR_SOURCE" ]]; then
  print -u2 "Application Copilot native host JAR was not built."
  exit 1
fi

mkdir -p "$HOST_DIR" "$INSTALL_DIR"
cp "$JAR_SOURCE" "$HOST_JAR"
cat > "$HOST_LAUNCHER" <<EOF
#!/bin/zsh
set -eu
exec /usr/bin/java \\
  -Dloader.main=site.yuqi.career.local.CodexNativeHost \\
  -cp "$HOST_JAR" \\
  org.springframework.boot.loader.launch.PropertiesLauncher
EOF
chmod 700 "$HOST_LAUNCHER"
cat > "$HOST_FILE" <<EOF
{
  "name": "site.yuqi.application_copilot",
  "description": "Local Codex advisor for Yuqi Application Copilot",
  "path": "$HOST_LAUNCHER",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$EXTENSION_ID/"]
}
EOF
chmod 600 "$HOST_FILE"
print "Installed $HOST_FILE and $HOST_LAUNCHER"
