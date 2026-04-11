@ECHO OFF
SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

IF NOT "%JAVA_HOME%"=="" GOTO findJavaFromJavaHome
SET JAVA_EXE=java.exe
GOTO execute

:findJavaFromJavaHome
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe

:execute
"%JAVA_EXE%" "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
