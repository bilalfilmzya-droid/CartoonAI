#!/usr/bin/env sh
set -e
APP_HOME="$(cd "$(dirname "$0")"; pwd)"
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download gradle-wrapper.jar if missing
if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar..."
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" -o "$GRADLE_WRAPPER_JAR" 2>/dev/null || \
    wget -q "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" -O "$GRADLE_WRAPPER_JAR" 2>/dev/null || true
fi

exec java -classpath "$GRADLE_WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
