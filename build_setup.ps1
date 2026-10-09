# 설치 프로그램 만들기: dist\AttendanceSetup\ (폴더) + dist\AttendanceSetup.exe (단일 파일, setup\bootstrap\Bootstrap.cs - 실행하면 준비 창부터 뜸)
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

# 6) 단일 파일(AttendanceSetup.exe): 폴더판을 zip으로 묶어서, 그 zip을 리소스로 넣은 작은 C# 프로그램으로 만듦
#    (setup\bootstrap\Bootstrap.cs, Windows에 기본으로 있는 .NET Framework 컴파일러 사용 - 따로 설치할 것 없음).
#    실행하면 "준비 중" 창을 먼저 띄우고 -> 임시 폴더에 풀면서 진행 막대 -> 설치 프로그램(Java) 실행 -> 끝나면 임시 폴더 삭제.
#    예전 IExpress 방식은 Windows가 전부 풀고 나서야 우리 코드가 돌아서 창이 6초쯤 뒤에야 보였음
if (-not $NoSfx) {
    $sfx = Join-Path $work "sfx"
    New-Item -ItemType Directory $sfx | Out-Null
    # Windows 내장 tar(bsdtar)를 전체 경로로 지정: Git Bash 등에서 실행하면 PATH의 GNU tar가 잡혀서 "C:"를 원격 호스트로 해석함
    $winTar = Join-Path $env:SystemRoot "System32\tar.exe"
    & $winTar -a -c -f "$sfx\setup.zip" -C $dist AttendanceSetup
    if ($LASTEXITCODE -ne 0) { throw "zip 생성 실패" }
    $target = Join-Path $dist "AttendanceSetup.exe"
    Remove-Item $target -Force -ErrorAction SilentlyContinue
    $fw = Join-Path $env:SystemRoot "Microsoft.NET\Framework64\v4.0.30319"
    # 아이콘은 설치 프로그램(jpackage) exe와 같은 것을 씀
    $icon = Join-Path $sfx "setup.ico"
    Add-Type -AssemblyName System.Drawing
    $ico = [System.Drawing.Icon]::ExtractAssociatedIcon((Join-Path $final "AttendanceSetup.exe"))
    $fs = [System.IO.File]::Create($icon); $ico.Save($fs); $fs.Close()
    Write-Host "단일 파일을 만드는 중..."
    & (Join-Path $fw "csc.exe") /nologo /target:winexe /codepage:65001 /optimize+ /platform:anycpu `
        /reference:System.IO.Compression.dll `
        "/resource:$sfx\setup.zip,setup.zip" `
        "/win32icon:$icon" `
        "/out:$target" (Join-Path $root "setup\bootstrap\Bootstrap.cs")
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $target)) { throw "단일 파일(AttendanceSetup.exe)을 만들지 못했습니다. -NoSfx로 폴더판만 사용할 수 있습니다." }
    Write-Host ("단일 파일 완료: {0}  ({1:N0} MB)" -f $target, ((Get-Item $target).Length / 1MB))
}
