# 예전 버전(MariaDB) 데이터 옮기기 도구 만들기
#   결과: dist_tools\AttendanceMigrate\AttendanceMigrate.exe (폴더판, Java 설치 불필요)
#         dist_tools\AttendanceMigrate.exe                    (단일 파일, 다른 PC에 이 파일 하나만 복사해서 더블클릭)
#   dist\가 아닌 dist_tools\에 만드는 이유: build_dist.ps1이 실행될 때마다 dist\를 통째로 지움
# 사용: powershell -ExecutionPolicy Bypass -File build_migrate.ps1 [-NoSfx]
param(
    [switch]$NoSfx
)
$ErrorActionPreference = "Stop"
$root   = $PSScriptRoot
$src    = Join-Path $root "migrate\src"
$cache  = Join-Path $root "build_cache"
$work   = Join-Path $root "build\migrate"
$out    = Join-Path $root "dist_tools"

# 0) MariaDB JDBC 드라이버 (저장소에는 올리지 않고 build_cache에 받아 둠)
$driverVersion = "3.5.10"
$driver = Join-Path $cache "mariadb-java-client-$driverVersion.jar"
if (-not (Test-Path $driver)) {
    New-Item -ItemType Directory $cache -Force | Out-Null
    $url = "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/$driverVersion/mariadb-java-client-$driverVersion.jar"
    Write-Host "MariaDB 드라이버 내려받는 중: $url"
    Invoke-WebRequest $url -OutFile $driver -UseBasicParsing
}
$gson = Get-ChildItem (Join-Path $root "Attendance_register\lib") -Filter "gson-*.jar" | Select-Object -First 1
if (-not $gson) { throw "Attendance_register\lib에 gson jar가 없습니다" }

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
$classes = Join-Path $work "classes"
$stage   = Join-Path $work "input"
New-Item -ItemType Directory $classes, $stage, $out -Force | Out-Null

# 1) 컴파일 + jar
$sources = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -cp "$driver;$($gson.FullName)" -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "컴파일 실패" }
& jar --create --file "$stage\attendance-migrate.jar" --main-class migrate.MigrateMain -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 생성 실패" }
Copy-Item $driver, $gson.FullName $stage

# 2) 최소 런타임 (Swing 화면 + JDBC + HTTP)
$modules = "java.base,java.desktop,java.sql,java.naming,java.management,java.net.http,java.logging," +
           "java.security.jgss,java.security.sasl,java.transaction.xa,jdk.crypto.ec,jdk.localedata"
$runtime = Join-Path $work "runtime"
& jlink --add-modules $modules --include-locales=ko,en --output $runtime --strip-debug --no-header-files --no-man-pages
if ($LASTEXITCODE -ne 0) { throw "jlink 실패" }

# 3) jpackage app-image
$appDir = Join-Path $out "AttendanceMigrate"
Remove-Item $appDir -Recurse -Force -ErrorAction SilentlyContinue
& jpackage --type app-image --name AttendanceMigrate --input $stage `
    --main-jar attendance-migrate.jar --main-class migrate.MigrateMain `
    --runtime-image $runtime --dest $out
if ($LASTEXITCODE -ne 0) { throw "jpackage 실패" }
Write-Host ("폴더판 완료: {0}  ({1:N0} MB)" -f $appDir, ((Get-ChildItem $appDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB))

# 4) 단일 파일: build_setup.ps1과 같은 방식 (zip + 풀고 실행하는 run.cmd를 Windows 내장 IExpress로 묶음)
if (-not $NoSfx) {
    $sfx = Join-Path $work "sfx"
    New-Item -ItemType Directory $sfx | Out-Null
    # Git Bash의 GNU tar가 잡히지 않도록 Windows 내장 tar를 전체 경로로 지정
    $winTar = Join-Path $env:SystemRoot "System32\tar.exe"
    & $winTar -a -c -f "$sfx\migrate.zip" -C $out AttendanceMigrate
    if ($LASTEXITCODE -ne 0) { throw "zip 생성 실패" }
    # run.cmd는 ASCII만 사용 (cmd.exe가 UTF-8 한글을 명령으로 해석함)
    $runCmd = @'
@echo off
setlocal
set "DEST=%TEMP%\AttendanceMigrate_%RANDOM%%RANDOM%"
mkdir "%DEST%"
"%SystemRoot%\System32\tar.exe" -xf "%~dp0migrate.zip" -C "%DEST%"
if errorlevel 1 (
  echo Failed to extract the program files.
  pause
  exit /b 1
)
"%DEST%\AttendanceMigrate\AttendanceMigrate.exe"
rmdir /s /q "%DEST%"
'@
    [System.IO.File]::WriteAllText("$sfx\run.cmd", $runCmd, [System.Text.Encoding]::ASCII)
    $target = Join-Path $out "AttendanceMigrate.exe"
    Remove-Item $target -Force -ErrorAction SilentlyContinue
    # AppLaunched는 "cmd /c run.cmd"가 아니라 "run.cmd"로 직접 지정해야 실행됨 (build_setup.ps1 참고)
    $sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$target
FriendlyName=Attendance Data Migration
AppLaunched=run.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="run.cmd"
FILE1="migrate.zip"
[SourceFiles]
SourceFiles0=$sfx\
[SourceFiles0]
%FILE0%=
%FILE1%=
"@
    [System.IO.File]::WriteAllText("$sfx\migrate.sed", $sed, [System.Text.Encoding]::ASCII)
    Write-Host "IExpress로 단일 파일을 만드는 중..."
    $p = Start-Process iexpress -ArgumentList "/N", "/Q", "$sfx\migrate.sed" -Wait -PassThru -WindowStyle Hidden
    if (-not (Test-Path $target)) { throw "IExpress가 단일 파일을 만들지 못했습니다 (종료 코드 $($p.ExitCode)). -NoSfx로 폴더판만 쓸 수 있습니다." }
    Write-Host ("단일 파일 완료: {0}  ({1:N0} MB)" -f $target, ((Get-Item $target).Length / 1MB))
}
