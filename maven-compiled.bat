@echo off
set LOGFILE=D:\code\ai_project\javaclawbot\maven-compiled.log
cd /d D:\code\ai_project\javaclawbot
echo [%date% %time%] Maven compile started... > %LOGFILE%
"C:\Program Files\Java\jdk-17\bin\java.exe" -Dmaven.multiModuleProjectDirectory=D:\code\ai_project\javaclawbot -Djansi.passthrough=true -Dmaven.home=D:\IDEA20240307\plugins\maven\lib\maven3 -Dclassworlds.conf=D:\IDEA20240307\plugins\maven\lib\maven3\bin\m2.conf -Dmaven.ext.class.path=D:\IDEA20240307\plugins\maven\lib\maven-event-listener.jar -javaagent:D:\IDEA20240307\lib\idea_rt.jar=53924 -Dfile.encoding=UTF-8 -classpath D:\IDEA20240307\plugins\maven\lib\maven3\boot\plexus-classworlds-2.8.0.jar;D:\IDEA20240307\plugins\maven\lib\maven3\boot\plexus-classworlds.license org.codehaus.classworlds.Launcher -Didea.version=2024.3.7 -Dmaven.repo.local=D:\apps\maven\repository compile >> %LOGFILE% 2>&1
echo [%date% %time%] Exit code: %ERRORLEVEL% >> %LOGFILE%
