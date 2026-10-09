# 설치 프로그램 만들기: dist\AttendanceSetup\ (폴더) + dist\AttendanceSetup.exe (단일 파일, Windows 내장 IExpress)
#   사용: powershell -ExecutionPolicy Bypass -File build_setup.ps1 [-RebuildApp] [-ApkPath 경로] [-NoSfx]
#   -RebuildApp : 앱/서버를 먼저 새로 빌드(build_dist.ps1). 없으면 dist\AttendanceManager가 이미 있어야 함
param(
    [switch]$RebuildApp,
    [switch]$NoSfx,
    [string]$ApkPath = ""     # 비우면 Android 프로젝트의 apk\debug 폴더에서 가장 최근 APK(파일 이름은 상관없음)를 사용
)
$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot
$dist  = Join-Path $root "dist"
$app   = Join-Path $dist "AttendanceManager"
$build = Join-Path $root "build"
$cache = Join-Path $root "build_cache"

# 0) 재료 확인: 앱 이미지, 런타임(build_dist.ps1이 만든 것), APK
if ($RebuildApp -or -not (Test-Path "$app\AttendanceManager.exe") -or -not (Test-Path "$build\runtime\bin\java.exe")) {
    Write-Host "앱/서버를 먼저 빌드합니다 (build_dist.ps1)..."
    & (Join-Path $root "build_dist.ps1")
    if ($LASTEXITCODE -ne 0) { throw "build_dist.ps1 실패" }
}
if ([string]::IsNullOrEmpty($ApkPath)) {
    $apkDir = "C:\Users\shkim\AndroidStudioProjects\AttenadanceApplication\app\build\outputs\apk\debug"
    $latest = Get-ChildItem $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest) { $ApkPath = $latest.FullName }
}
if ([string]::IsNullOrEmpty($ApkPath) -or -not (Test-Path $ApkPath)) { throw "Android APK를 찾을 수 없습니다. Android 프로젝트에서 gradlew assembleDebug를 실행하거나 -ApkPath로 지정하세요." }
Write-Host "Android APK: $ApkPath"

# 1) QR 코드 라이브러리(zxing core, Apache 2.0) - 한 번만 내려받음
New-Item -ItemType Directory $cache -Force | Out-Null
$zxing = Join-Path $cache "zxing-core-3.5.3.jar"
if (-not (Test-Path $zxing)) {
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar" -OutFile $zxing
}

# 2) 설치 프로그램 컴파일 -> setup.jar (방화벽 설정 코드는 앱과 같은 소스를 사용)
$work = Join-Path $build "setup"
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
$classes = Join-Path $work "classes"; $stage = Join-Path $work "stage"; $out = Join-Path $work "out"
New-Item -ItemType Directory $classes, $stage, $out | Out-Null
$sources = @(Get-ChildItem (Join-Path $root "setup\src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += (Join-Path $root "Attendance_register\src\server\FirewallSetup.java")
& javac -encoding UTF-8 -cp $zxing -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "설치 프로그램 컴파일 실패" }
& jar --create --file "$stage\setup.jar" --main-class setup.SetupMain -C $classes .
if ($LASTEXITCODE -ne 0) { throw "setup.jar 생성 실패" }
Copy-Item $zxing $stage

# 3) 설치 프로그램 실행 파일(jpackage). 앱과 같은 런타임을 사용 (설치할 때 이 런타임을 그대로 복사함)
& jpackage --type app-image --name AttendanceSetup --input $stage --main-jar setup.jar --main-class setup.SetupMain `
    --runtime-image (Join-Path $build "runtime") --dest $out
if ($LASTEXITCODE -ne 0) { throw "jpackage 실패" }
$setupDir = Join-Path $out "AttendanceSetup"

# 4) payload = 앱 이미지에서 런타임만 뺀 것 + Android APK
$payload = Join-Path $setupDir "payload"
New-Item -ItemType Directory $payload | Out-Null
Get-ChildItem $app | Where-Object { $_.Name -ne "runtime" } | ForEach-Object { Copy-Item $_.FullName $payload -Recurse }
Copy-Item $ApkPath (Join-Path $payload "AttendanceApp.apk")

# 5) dist\AttendanceSetup\ 로 내보내기
$final = Join-Path $dist "AttendanceSetup"
Remove-Item $final -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item $setupDir $final -Recurse
Write-Host ("폴더판 완료: {0}  ({1:N0} MB)" -f $final, ((Get-ChildItem $final -Recurse -File | Measure-Object Length -Sum).Sum / 1MB))

# 6) 단일 파일(AttendanceSetup.exe): zip + 압축 해제 후 실행하는 run.cmd를 IExpress로 묶음
if (-not $NoSfx) {
    $sfx = Join-Path $work "sfx"
    New-Item -ItemType Directory $sfx | Out-Null
    # Windows 내장 tar(bsdtar)를 전체 경로로 지정: Git Bash 등에서 실행하면 PATH의 GNU tar가 잡혀서 "C:"를 원격 호스트로 해석함
    $winTar = Join-Path $env:SystemRoot "System32\tar.exe"
    & $winTar -a -c -f "$sfx\setup.zip" -C $dist AttendanceSetup
    if ($LASTEXITCODE -ne 0) { throw "zip 생성 실패" }
    # run.cmd는 ASCII만 사용 (cmd.exe가 UTF-8 한글을 명령으로 해석함). 한글 문구는 AttendanceLoading.exe에 있음.
    # 콘솔 창은 숨김(IExpress ShowInstallProgramWindow=1). 대신 압축을 푸는 동안 AttendanceLoading.exe가 "준비 중" 창을 보여주고,
    # 설치 프로그램(Java)이 자기 준비 창을 띄우며 READY 파일을 만들면 닫힘 (setup\src\setup\LoadingWindow.java).
    # 준비 창 프로그램은 Windows에 기본으로 있는 .NET Framework 컴파일러로 만듦 (setup\loading\Loading.cs)
    $csc = Join-Path $env:SystemRoot "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    & $csc /nologo /target:winexe /codepage:65001 /optimize+ /out:"$sfx\AttendanceLoading.exe" (Join-Path $root "setup\loading\Loading.cs")
    if ($LASTEXITCODE -ne 0) { throw "준비 창(AttendanceLoading.exe) 컴파일 실패" }
    $runCmd = @'
@echo off
setlocal
set "DEST=%TEMP%\AttendanceSetup_%RANDOM%%RANDOM%"
set "ATTENDANCE_SETUP_READY=%DEST%.ready"
mkdir "%DEST%"
start "" "%~dp0AttendanceLoading.exe" "%ATTENDANCE_SETUP_READY%"
"%SystemRoot%\System32\tar.exe" -xf "%~dp0setup.zip" -C "%DEST%"
if errorlevel 1 (
  echo ready> "%ATTENDANCE_SETUP_READY%"
  "%~dp0AttendanceLoading.exe" --extract-failed
  rmdir /s /q "%DEST%"
  exit /b 1
)
"%DEST%\AttendanceSetup\AttendanceSetup.exe"
echo ready> "%ATTENDANCE_SETUP_READY%"
rmdir /s /q "%DEST%"
ping -n 2 127.0.0.1 >nul
del "%ATTENDANCE_SETUP_READY%" >nul 2>&1
'@
    [System.IO.File]::WriteAllText("$sfx\run.cmd", $runCmd, [System.Text.Encoding]::ASCII)
    $target = Join-Path $dist "AttendanceSetup.exe"
    Remove-Item $target -Force -ErrorAction SilentlyContinue
    # 주의: AppLaunched는 "cmd /c run.cmd"가 아니라 "run.cmd"처럼 파일을 직접 지정해야 실행된다 (cmd /c 형태는 아무것도 실행되지
    # 않고 조용히 끝남 - 실제로 확인함). 실행 위치는 IExpress가 풀어놓은 임시 폴더(%TEMP%\IXPnnn.TMP)
    $sed = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=0
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
FriendlyName=Attendance Manager Setup
AppLaunched=run.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="run.cmd"
FILE1="setup.zip"
FILE2="AttendanceLoading.exe"
[SourceFiles]
SourceFiles0=$sfx\
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
"@
    [System.IO.File]::WriteAllText("$sfx\setup.sed", $sed, [System.Text.Encoding]::ASCII)
    Write-Host "IExpress로 단일 파일을 만드는 중... (수 분 걸릴 수 있습니다)"
    $p = Start-Process iexpress -ArgumentList "/N", "/Q", "$sfx\setup.sed" -Wait -PassThru -WindowStyle Hidden
    if (-not (Test-Path $target)) { throw "IExpress가 단일 파일을 만들지 못했습니다 (종료 코드 $($p.ExitCode)). -NoSfx로 폴더판만 사용할 수 있습니다." }
    Write-Host ("단일 파일 완료: {0}  ({1:N0} MB)" -f $target, ((Get-Item $target).Length / 1MB))
}
