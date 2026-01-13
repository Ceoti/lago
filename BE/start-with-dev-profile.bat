@echo off
set NAVER_CLIENT_ID=TQeJEcmiR1hnZ6CNiXnA
set NAVER_CLIENT_SECRET=lVWp4C4vUb
set SPRING_PROFILES_ACTIVE=dev
echo Environment variables set:
echo NAVER_CLIENT_ID=%NAVER_CLIENT_ID%
echo NAVER_CLIENT_SECRET=%NAVER_CLIENT_SECRET%
echo SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%
gradlew.bat bootRun --args='--spring.profiles.active=dev'