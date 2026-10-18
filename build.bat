@echo off
rem Build LavaDigger.jar (JDK 8+)
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes
javac -encoding UTF-8 --release 8 -nowarn -d build\classes src\lavadigger\*.java || goto :err
echo Main-Class: lavadigger.Main> build\manifest.txt
jar cfm LavaDigger.jar build\manifest.txt -C build\classes . || goto :err
echo Done: LavaDigger.jar   Run: java -jar LavaDigger.jar
exit /b 0
:err
echo BUILD FAILED
exit /b 1
