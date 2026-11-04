@echo off
chcp 65001 >nul
setlocal
set "JAVA_HOME=D:\jdk17"
cd /d "%~dp0"

echo [1/4] 构建前端资源...
call npm run build || goto :err

echo [2/4] 同步到 Android 工程...
call npx cap sync android || goto :err

echo [3/4] 适配本机 JDK17（把 Capacitor 的 Java 21 编译目标降为 17）...
powershell -NoProfile -Command "$f1='node_modules\@capacitor\android\capacitor\build.gradle'; $f2='android\app\capacitor.build.gradle'; if(Test-Path $f1){ (Get-Content $f1 -Raw) -replace 'VERSION_21','VERSION_17' | Set-Content $f1 -NoNewline }; if(Test-Path $f2){ (Get-Content $f2 -Raw) -replace 'VERSION_21','VERSION_17' | Set-Content $f2 -NoNewline }"

echo [4/4] 编译 APK...
cd android
call gradlew.bat assembleDebug --no-daemon || goto :err

echo.
echo ================ 构建成功 ================
echo APK 位置: android\app\build\outputs\apk\debug\app-debug.apk
goto :eof

:err
echo.
echo [失败] 请查看上方错误信息。
