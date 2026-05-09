#!/bin/bash
# Gradle wrapper stub — downloads Gradle if not present
# For full functionality, generate via: gradle wrapper --gradle-version 8.9

GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-8.9-bin.zip"

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

warn () { echo "$*"; }
die () { echo "$*"; exit 1; }

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set but java is not found at $JAVACMD"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command found"
fi

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
