#!/bin/zsh
set -eu

ROOT="${0:A:h:h}"
JAR="$(find "$ROOT/target" -maxdepth 1 -name 'portfolio-application-copilot-*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "$JAR" ]]; then
  print -u2 "Application Copilot native host is not built. Run ./mvnw package first."
  exit 1
fi

exec /usr/bin/java \
  -Dloader.main=site.yuqi.career.local.CodexNativeHost \
  -cp "$JAR" \
  org.springframework.boot.loader.launch.PropertiesLauncher
