
@ECHO OFF
SET DIR=%~dp0
SET APP_HOME=%DIR%
SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
IF EXIST "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  java %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)
