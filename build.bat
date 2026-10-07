@echo off
rem Builds LastStand.jar (runnable with `java -jar`). Needs a JDK 17+ (javac + jar on the PATH).
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
rem Compiling Main with -sourcepath pulls in every class the game uses -- no file list needed.
javac --release 17 -d out -sourcepath src src\laststand\Main.java || exit /b 1
jar --create --file LastStand.jar --main-class laststand.Main -C out . || exit /b 1
echo Built LastStand.jar  -^>  run it with:  java -jar LastStand.jar
