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

# 2-1) 동기화가 "DB 내용이 바뀌었는지" 비교할 때 쓰는 H2 도구: 서버 jar 안에 들어 있는 h2 jar를 그대로 꺼내서
#      server\h2.jar로 둠 (서버와 같은 버전이어야 DB 파일을 읽을 수 있음)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ServerJar))
try {
    $h2Entry = $zip.Entries | Where-Object { $_.FullName -like "BOOT-INF/lib/h2-*.jar" } | Select-Object -First 1
    if (-not $h2Entry) { throw "서버 jar 안에서 h2 jar를 찾지 못했습니다" }
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($h2Entry, "$stage\server\h2.jar", $true)
} finally {
    $zip.Dispose()
}

# 2-2) 구글 로그인 설정(OAuth 클라이언트 ID). 저장소에는 올리지 않으므로 이 폴더에 있을 때만 포함됨
#      없으면 구글 드라이브 동기화 메뉴가 "설정 파일 없음"으로 안내하고 나머지 기능은 정상 동작
#      베타판(Attendance_register\src\main\AppEdition.java의 BETA = true)은 동기화 기능을 감추므로 넣지 않음
$googleClient = Join-Path $root "google-client.json"
$isBeta = Select-String -Path (Join-Path $root "Attendance_register\src\main\AppEdition.java") -Pattern "BETA = true;" -SimpleMatch -Quiet
if ($isBeta) {
    Write-Host "베타판: 구글 드라이브 동기화·라이선스 기능을 감춘 상태로 빌드합니다 (google-client.json 미포함)"
} elseif (Test-Path $googleClient) {
    Copy-Item $googleClient "$stage\google-client.json"
} else {
    Write-Host "참고: google-client.json이 없어 구글 드라이브 동기화는 비활성 상태로 배포됩니다 (docs\구글드라이브_동기화_설정.md 참고)"
}
if (-not $isBeta) {
    Copy-Item (Join-Path $root "docs\구글드라이브_동기화_설정.md") "$stage\구글드라이브_동기화_설정.md" -ErrorAction SilentlyContinue
}

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
