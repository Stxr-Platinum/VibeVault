@rem Gradle wrapper stub for Windows
@rem For full functionality, generate via: gradle wrapper --gradle-version 8.9

@if "%DEBUG%"=="" @echo off
setlocal

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set APP_BASE_NAME=%~n0
set APP_HOME=%~dp0

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo ERROR: JAVA_HOME is not set and no 'java' command could be found.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set but java.exe not found at %JAVA_EXE%
goto fail

:execute
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
endlocal

:fail
exit /b 1
