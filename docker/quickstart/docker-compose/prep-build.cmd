@echo off
chcp 1251>nul

set project-web=metaheuristic-angular
set project=metaheuristic
set pr-path=%CD%

if not exist %project% git clone -b release --recursive https://github.com/sergmain/metaheuristic.git
cd %project%
git clean -df
git pull origin
git checkout origin/release

docker run -it --rm --name %project% -v "%pr-path%\%project%":/usr/src/%project% -w /usr/src/%project% --volume "%pr-path%\.m2":/root/.m2 maven:latest mvn -Dmaven.repo.local=/root/.m2 clean install -Dmaven.test.skip=true

copy %pr-path%\%project%\apps\metaheuristic\target\metaheuristic.jar %pr-path%\launchpad\
copy %pr-path%\%project%\apps\metaheuristic\target\metaheuristic.jar %pr-path%\processor\

cd %pr-path%
if not exist %project-web% git clone -b release --recursive https://github.com/sergmain/metaheuristic-angular.git
if exist %pr-path%\%project-web%\dist\metaheuristic-app rd /s/q "%pr-path%\%project-web%\dist"
cd %project-web%
git clean -df
git pull origin release
git checkout origin/release

echo f|xcopy /r /y "%pr-path%\environment.production.ts" "%pr-path%\%project-web%\src\environments\"

pushd "%pr-path%\web-client\files"
for /f "delims=" %%i in ('dir /b /ad ^| findstr /e /i /v ".git .idea docs"') do rd /s/q "%%i"
for /f "delims=" %%i in ('dir /b /a-d ^| findstr /e /i /v ".gitattributes .gitignore CNAME"') do del /f /q "%%i"
popd

call %pr-path%\mk-dcr-web.cmd

echo d|xcopy /e /y "%pr-path%\%project-web%\dist\metaheuristic-app" "%pr-path%\web-client\files" >nul

#echo f|xcopy /r /y "%pr-path%\%project%\sql\*.sql" "%pr-path%\mysql-dump\"