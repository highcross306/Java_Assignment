# 설치 프로그램 통합 시험 (임시 폴더 + 시험용 레지스트리 키 HKCU:\Software\AttSetupTest 만 사용, 시스템 설정은 건드리지 않음)
#   사용: 먼저 build_setup.ps1 -NoSfx 로 dist\AttendanceSetup을 만든 뒤
#         powershell -ExecutionPolicy Bypass -File setup	ests\installer-tests.ps1
#   시험 내용: 새 설치(한글+공백 경로), 설치된 앱 실행, 실행 중 업데이트, 기본 포트 점유 시 다른 포트 선택, --autostart/--background, 제거(데이터 유지/삭제)
#   (실행 중 약 1~2분 동안 앱 창이 잠깐 나타나고, 마지막에 java 프로세스 중 이 시험이 띄운 것만 정리합니다)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$T  = Join-Path $env:TEMP 'att-setup-test'
$S  = (Resolve-Path (Join-Path $PSScriptRoot '..\..\dist\AttendanceSetup')).Path
$Inst  = "$T\설치 폴더\출석 관리"
$RK = 'HKCU:\Software\AttSetupTest'
$script:fails = 0
function Check($name, $ok) { if ($ok) { "PASS $name" } else { "FAIL $name"; $script:fails++ } }

# 정리
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'att-setup-test|attendance-server' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
foreach ($d in "$T\설치 폴더", "$T\Desktop", "$T\Programs", "$T\home") { if (Test-Path -LiteralPath $d) { Get-ChildItem -LiteralPath $d -Force | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force } } }
if (Test-Path $RK) { Remove-Item $RK -Recurse -Force }
New-Item -ItemType Directory -Force "$T\home", "$T\Desktop", "$T\Programs" | Out-Null

$q = '"'   # 공백이 든 경로를 한 문자열로 넘기기 위한 따옴표

function Setup { param([string[]]$a)
  & "$S\runtime\bin\java.exe" "-Dstdout.encoding=UTF-8" "-Duser.home=$T\home" "-Dsetup.regRoot=HKCU\Software\AttSetupTest" "-Dsetup.desktopDir=$T\Desktop" "-Dsetup.programsDir=$T\Programs" -cp "$S\app\*" setup.SetupMain @a 2>&1
}
function Installed-Java { param([string]$extra)  # 설치된 폴더의 런타임으로 앱 실행 (임시 사용자 폴더)
  $argline = "-Duser.home=$q$T\home$q -cp $q$Inst\app\*$q main.Main $extra"
  Start-Process -FilePath "$Inst\runtime\bin\java.exe" -ArgumentList $argline -PassThru -WindowStyle Hidden
}
function Uninstall-Java { param([string]$extra)  # 앱 목록의 UninstallString과 같은 형태: 설치 폴더의 런타임 + setup.jar;setup\*
  $argline = "-Duser.home=$q$T\home$q -Dsetup.regRoot=HKCU\Software\AttSetupTest -Dsetup.desktopDir=$q$T\Desktop$q -Dsetup.programsDir=$q$T\Programs$q " +
             "-cp $q$Inst\setup\setup.jar;$Inst\setup\*$q setup.SetupMain --uninstall --silent --no-firewall $extra"
  Start-Process -FilePath "$Inst\runtime\bin\java.exe" -ArgumentList $argline -PassThru -WindowStyle Hidden -Wait
}
function Health($port) { try { (Invoke-WebRequest "http://localhost:$port/api/health" -UseBasicParsing -TimeoutSec 2).StatusCode } catch { 0 } }
function Wait-Health($port, $sec) { for ($i = 0; $i -lt $sec; $i++) { if ((Health $port) -eq 200) { return $true }; Start-Sleep 1 }; return $false }
function Reg-Values($path) { $h = @{}; if (Test-Path $path) { $k = Get-Item $path; foreach ($n in $k.GetValueNames()) { $h[$n] = $k.GetValue($n) } }; return $h }

"=== T1. 새로 설치 (한글+공백 경로)"
$out = Setup @('--silent', '--install-dir', $Inst, '--no-firewall', '--no-android', '--no-launch')
Check "T1 설치 성공 메시지" (($out -join "`n") -match '설치 완료')
foreach ($f in 'AttendanceManager.exe', 'app\attendance-desktop.jar', 'app\server\attendance-server.jar', 'app\server\h2.jar', 'runtime\bin\java.exe', 'runtime\bin\javaw.exe', 'setup\setup.jar', 'AttendanceApp.apk') {
  Check "T1 파일 $f" (Test-Path -LiteralPath "$Inst\$f")
}
Check "T1 바탕화면 바로가기" (Test-Path -LiteralPath "$T\Desktop\출석 관리.lnk")
Check "T1 시작 메뉴 바로가기 2개" ((Get-ChildItem -LiteralPath "$T\Programs\출석 관리" -Filter *.lnk).Count -eq 2)
$ws = New-Object -ComObject WScript.Shell
$lnk = $ws.CreateShortcut("$T\Desktop\출석 관리.lnk")
Check "T1 바로가기 대상이 설치된 exe" ($lnk.TargetPath -eq "$Inst\AttendanceManager.exe")
$u = Reg-Values "$RK\Uninstall\AttendanceManager"
Check "T1 앱 목록 등록: 이름/버전/위치" ($u['DisplayName'] -eq '출석 관리 프로그램' -and $u['DisplayVersion'] -eq '1.0.0' -and $u['InstallLocation'] -eq $Inst)
Check "T1 앱 목록 등록: 제거 명령" ($u['UninstallString'] -match 'javaw\.exe' -and $u['UninstallString'] -match '--uninstall')
Check "T1 NoModify/NoRepair" ($u['NoModify'] -eq 1 -and $u['NoRepair'] -eq 1)
Check "T1 자동 실행은 꺼져 있음" (-not (Reg-Values "$RK\Run").ContainsKey('AttendanceManager'))
Check "T1 기본 포트라 settings.properties 없음" (-not (Test-Path "$T\home\.attendance-desktop\settings.properties"))

"=== T2. 설치된 앱 실행 (설치된 런타임/서버로)"
$app = Installed-Java ''
Check "T2 서버 기동 (18080)" (Wait-Health 18080 40)
Check "T2 중복 실행 방지 락 존재" (Test-Path "$T\home\.attendance-desktop\session.lock")

"=== T3. 실행 중에 다시 설치(업데이트): 프로그램을 정상 종료시키고 진행"
$out = Setup @('--silent', '--install-dir', $Inst, '--no-firewall', '--no-android', '--no-launch')
Check "T3 업데이트 설치 성공" (($out -join "`n") -match '설치 완료')
Start-Sleep 1; $app.Refresh()
Check "T3 실행 중이던 앱이 정상 종료됨" ($app.HasExited)
Check "T3 서버도 함께 종료" ((Health 18080) -eq 0)
$state = if (Test-Path "$T\home\.attendance-desktop\session.state") { (Get-Content "$T\home\.attendance-desktop\session.state") } else { '(없음)' }
Check "T3 종료 상태 clean ($state)" ($state -eq 'clean')

"=== T4. 기본 포트(18080)를 다른 프로그램이 쓰는 PC: 자동으로 다른 포트 선택"
$blocker = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, 18080); $blocker.Start()
$out = Setup @('--silent', '--install-dir', $Inst, '--no-firewall', '--no-android', '--no-launch')
Check "T4 설치 성공" (($out -join "`n") -match '설치 완료')
$settings = Get-Content "$T\home\.attendance-desktop\settings.properties" -ErrorAction SilentlyContinue
Check "T4 settings.properties에 server.port=28080" ($settings -match 'server\.port=28080')
Check "T4 안내 메시지 출력" (($out -join "`n") -match '28080')
$app = Installed-Java ''
Check "T4 앱이 28080 포트로 서버 기동 (18080은 다른 프로그램이 사용 중)" (Wait-Health 28080 40)
$udp = New-Object System.Net.Sockets.UdpClient; $udp.Client.ReceiveTimeout = 3000
$qb = [Text.Encoding]::UTF8.GetBytes('ATTENDANCE_DISCOVER_V1'); $null = $udp.Send($qb, $qb.Length, '127.0.0.1', 18081)
$ep = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 0)
$reply = try { [Text.Encoding]::UTF8.GetString($udp.Receive([ref]$ep)) } catch { '(응답 없음)' }
Check "T4 UDP 자동 검색 응답이 새 포트를 알림 ($reply)" ($reply -eq 'ATTENDANCE_SERVER_V1:28080')
$udp.Close()
$blocker.Stop()

"=== T5. 포트가 다시 비면(업데이트) 기본 포트로 복귀 + --autostart 등록"
$out = Setup @('--silent', '--install-dir', $Inst, '--no-firewall', '--no-android', '--no-launch', '--autostart')
Check "T5 설치 성공" (($out -join "`n") -match '설치 완료')
$app.Refresh(); Check "T5 실행 중이던 앱 종료됨" ($app.HasExited)
$settings = if (Test-Path "$T\home\.attendance-desktop\settings.properties") { Get-Content "$T\home\.attendance-desktop\settings.properties" } else { @() }
Check "T5 이전 포트 설정이 지워짐" (-not ($settings -match 'server\.port'))
$run = Reg-Values "$RK\Run"
"   (Run 값: $($run['AttendanceManager']))"
Check "T5 자동 실행 등록: --background" ($run['AttendanceManager'] -match 'AttendanceManager\.exe' -and $run['AttendanceManager'] -match '--background')

"=== T6. --background 로 시작하면 창 없이 숨김 상태로 서버만 실행"
$app = Installed-Java '--background'
Check "T6 서버 기동" (Wait-Health 18080 40)
Start-Sleep 3; $app.Refresh()
Check "T6 보이는 창 없음 (MainWindowHandle=0)" ($app.MainWindowHandle -eq 0)
New-Item -ItemType File -Force "$T\home\.attendance-desktop\exit.signal" | Out-Null
$null = $app.WaitForExit(30000)
Check "T6 exit.signal로 정상 종료됨" ($app.HasExited)

"=== T7. 제거 (설치된 런타임으로 실행 = 앱 목록의 [제거]와 같은 방식), 출석 데이터는 유지"
Set-Content "$T\home\.attendance-desktop\marker.txt" 'user-data'
$un = Uninstall-Java ''
Check "T7 제거 프로그램 정상 종료(코드 0)" ($un.ExitCode -eq 0)
Check "T7 바탕화면 바로가기 삭제" (-not (Test-Path -LiteralPath "$T\Desktop\출석 관리.lnk"))
Check "T7 시작 메뉴 폴더 삭제" (-not (Test-Path -LiteralPath "$T\Programs\출석 관리"))
Check "T7 앱 목록 등록 삭제" (-not (Test-Path "$RK\Uninstall\AttendanceManager"))
Check "T7 자동 실행 등록 삭제" (-not (Reg-Values "$RK\Run").ContainsKey('AttendanceManager'))
for ($i = 0; $i -lt 40 -and (Test-Path -LiteralPath $Inst); $i++) { Start-Sleep 1 }
Check "T7 설치 폴더 삭제됨 (제거 프로그램이 끝난 뒤)" (-not (Test-Path -LiteralPath $Inst))
Check "T7 출석 데이터(사용자 폴더)는 그대로 남음" (Test-Path "$T\home\.attendance-desktop\marker.txt")

"=== T8. 다시 설치 후 --delete-data 로 제거하면 데이터도 삭제"
$out = Setup @('--silent', '--install-dir', $Inst, '--no-firewall', '--no-android', '--no-launch')
Check "T8 재설치 성공 (이전 데이터가 남아 있어도)" (($out -join "`n") -match '설치 완료')
$un = Uninstall-Java '--delete-data'
Check "T8 데이터 삭제 제거 성공" ($un.ExitCode -eq 0)
Check "T8 사용자 데이터 폴더 삭제됨" (-not (Test-Path "$T\home\.attendance-desktop"))
for ($i = 0; $i -lt 40 -and (Test-Path -LiteralPath $Inst); $i++) { Start-Sleep 1 }
Check "T8 설치 폴더 삭제됨" (-not (Test-Path -LiteralPath $Inst))

if ($script:fails -eq 0) { "ALL PASS" } else { "$($script:fails) FAILED" }
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'att-setup-test' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
