#!/bin/sh
# Gradle wrapper script
APP_HOME=$( cd "${APP_HOME:-$(dirname "$0")}" > /dev/null 2>&1 && pwd -P )
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
exec java $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
