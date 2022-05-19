@echo off

if "%JAVA_HOME%"=="" set JAVA_HOME=C:\Program Files\Java\jdk-16.0.1

start "Villagers 00 -> 04" "%JAVA_HOME%\bin\java.exe" -jar ..\out\artifacts\TokenPassing_jar\TokenPassing.jar -a 127.0.0.1 -p 20000 -n 5 -i 0
start "Villagers 05 -> 09" "%JAVA_HOME%\bin\java.exe" -jar ..\out\artifacts\TokenPassing_jar\TokenPassing.jar -a 127.0.0.1 -p 20000 -n 5 -i 5
start "Villagers 10 -> 14" "%JAVA_HOME%\bin\java.exe" -jar ..\out\artifacts\TokenPassing_jar\TokenPassing.jar -a 127.0.0.1 -p 20000 -n 5 -i 10
start "Villagers 15 -> 19" "%JAVA_HOME%\bin\java.exe" -jar ..\out\artifacts\TokenPassing_jar\TokenPassing.jar -a 127.0.0.1 -p 20000 -n 5 -i 15
start "Villagers 20 -> 24" "%JAVA_HOME%\bin\java.exe" -jar ..\out\artifacts\TokenPassing_jar\TokenPassing.jar -a 127.0.0.1 -p 20000 -n 5 -i 20
