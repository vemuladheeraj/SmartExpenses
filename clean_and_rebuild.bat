@echo off
echo Cleaning Android project to fix icon issues...
echo.

echo Removing build directories...
if exist "app\build" rmdir /s /q "app\build"
if exist "build" rmdir /s /q "build"
if exist ".gradle" rmdir /s /q ".gradle"

echo.
echo Cleaning completed!
echo.
echo Now rebuild your project:
echo 1. In Android Studio: Build -> Clean Project
echo 2. Then: Build -> Rebuild Project
echo.
echo Or run: .\gradlew clean build
echo.
pause
