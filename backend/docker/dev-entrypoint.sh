#!/bin/sh
set -eu

cd /app
chmod +x ./mvnw

compile_app() {
  echo "[dev] Compiling application sources..."
  if ./mvnw -Dmaven.test.skip=true compile; then
    echo "[dev] Compilation finished."
  else
    echo "[dev] Compilation failed. Waiting for the next change."
  fi
}

watch_sources() {
  while inotifywait -r -e close_write,create,delete,move src/main/java src/main/resources pom.xml >/dev/null 2>&1; do
    compile_app
  done
}

compile_app
watch_sources &
WATCHER_PID=$!
trap 'kill "$WATCHER_PID" >/dev/null 2>&1 || true' EXIT INT TERM

exec ./mvnw -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}"
