# 데스크톱 앱 배포본 생성 스크립트 (Java 설치가 없는 PC에서도 실행되는 폴더를 만든다)
#   결과: dist\AttendanceManager\AttendanceManager.exe  (이 폴더째로 학원 PC에 복사)
# 사용: powershell -ExecutionPolicy Bypass -File build_dist.ps1 [-ServerJar 경로]
param(
    [string]$ServerJar = "C:\Attendance_Server\attendance-server\target\attendance-server-1.0.0.jar"
)
$ErrorActionPreference = "Stop"
$root  = $PSScriptRoot
$app   = Join-Path $root "Attendance_register"
$build = Join-Path $root "build"
$dist  = Join-Path $root "dist"

if (-not (Test-Path $ServerJar)) {
    throw "서버 jar가 없습니다: $ServerJar  (백엔드에서 먼저 mvn package 실행)"
}

Remove-Item $build, $dist -Recurse -Force -ErrorAction SilentlyContinue
$classes = Join-Path $build "classes"
$stage   = Join-Path $build "input"
New-Item -ItemType Directory $classes, $stage, "$stage\server", $dist | Out-Null

# 1) 컴파일 + 앱 jar
$libJars = Get-ChildItem "$app\lib" -Recurse -Filter *.jar
$cp = ($libJars | ForEach-Object { $_.FullName }) -join ";"
$sources = Get-ChildItem "$app\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -cp $cp -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "컴파일 실패" }
& jar --create --file "$stage\attendance-desktop.jar" --main-class main.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar 생성 실패" }

# 2) 라이브러리 + 내장 서버 jar를 입력 폴더로 (서버 jar는 앱 옆 server\ 에 있어야 ServerLauncher가 찾음)
$libJars | ForEach-Object { Copy-Item $_.FullName $stage }
Copy-Item $ServerJar "$stage\server\attendance-server.jar"

# 3) 최소 런타임(jlink): 데스크톱 앱 + Spring Boot 서버가 같은 JVM 런타임을 쓰므로 둘 다 필요한 모듈
$modules = "java.base,java.desktop,java.sql,java.naming,java.management,java.instrument," +
           "java.net.http,java.security.jgss,java.security.sasl,java.xml,java.xml.crypto,java.logging," +
           "java.compiler,java.rmi,java.prefs,java.scripting,java.transaction.xa,java.datatransfer," +
           "jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.naming.dns,jdk.zipfs,jdk.httpserver,jdk.jfr"
$runtime = Join-Path $build "runtime"
& jlink --add-modules $modules --output $runtime --strip-debug --no-header-files --no-man-pages
if ($LASTEXITCODE -ne 0) { throw "jlink 실패" }

# 4) jpackage app-image (WiX 같은 추가 도구 없이 exe 포함 폴더 생성)
& jpackage --type app-image --name AttendanceManager --input $stage `
    --main-jar attendance-desktop.jar --main-class main.Main `
    --runtime-image $runtime --dest $dist
if ($LASTEXITCODE -ne 0) { throw "jpackage 실패" }

Write-Host "완료: $dist\AttendanceManager\AttendanceManager.exe"
