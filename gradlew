#!/usr/bin/env sh

APP_HOME="$(cd "$(dirname "$0")" && pwd)"

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: gradle-wrapper.jar not found at $WRAPPER_JAR"
  exit 1
fi

exec java \
  -classpath "$WRAPPER_JAR" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
