#!/usr/bin/env bash
# Builds LastStand.jar (runnable with `java -jar`). Needs a JDK 17+ (javac + jar on the PATH).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir out
# Compiling Main with -sourcepath pulls in every class the game uses -- no file list needed.
javac --release 17 -d out -sourcepath src src/laststand/Main.java
jar --create --file LastStand.jar --main-class laststand.Main -C out .
echo "Built LastStand.jar  ->  run it with:  java -jar LastStand.jar"
