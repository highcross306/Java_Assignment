package setup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// 제거 절차: 실행 중인 프로그램 종료 -> 바로가기/앱 목록 등록/자동 실행/방화벽 규칙 제거 -> 설치 폴더 삭제.
// 출석 데이터(DB, 백업, 구글 로그인 정보)는 사용자 폴더(~/.attendance-desktop)에 따로 있어서 기본적으로 남기고,
// 요청했을 때만 지운다 (실수로 데이터를 잃지 않도록).
final class Uninstaller {

    private final Options o;
    private final Installer.Listener l;
    final List<String> warnings = new ArrayList<>();

    Uninstaller(Options options, Installer.Listener listener) {
        this.o = options;
        this.l = listener;
    }

    // 설치 위치: 지정값 > 이 프로그램(setup.jar)이 있는 설치 폴더 > 레지스트리
    static Path locateInstallDir(Options o) {
        String override = System.getProperty("setup.installDir");
        if (override != null) return Path.of(override);
        try {
            File jar = new File(Uninstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile() && jar.getParentFile() != null && jar.getParentFile().getName().equals("setup")
                    && jar.getParentFile().getParentFile() != null) {
                return jar.getParentFile().getParentFile().toPath();
            }
        } catch (Exception ignored) {
            // 레지스트리에서 찾기
        }
        try {
            String v = Win.regGet(Win.uninstallKey(), "InstallLocation");
            if (!v.isEmpty()) return Path.of(v);
        } catch (IOException ignored) {
            // 아래 기본값
        }
        return o.installDir;
    }

    boolean run(Path installDir) {
        l.stage(1, 6, "실행 중인 프로그램 종료");
        if (!Installer.requestExit(Installer.dataDir(), l::log)) {
            l.log("[실패] 실행 중인 출석 관리 프로그램을 종료하지 못했습니다. 프로그램을 직접 종료(알림 영역 아이콘 → 완전히 종료)한 뒤 다시 시도해주세요.");
            return false;
        }

        l.stage(2, 6, "바로가기 삭제");
        try {
            Files.deleteIfExists(Win.desktopDir().resolve("출석 관리.lnk"));
            Win.deleteRecursively(Win.programsDir().resolve("출석 관리"));
            l.log("바로가기를 삭제했습니다.");
        } catch (IOException e) {
            warn("바로가기를 삭제하지 못했습니다: " + e.getMessage());
        }

        l.stage(3, 6, "앱 목록/자동 실행 등록 삭제");
        try {
            Win.regDeleteKey(Win.uninstallKey());
            Win.regDeleteValue(Win.runKey(), Win.APP_KEY_NAME);
            l.log("등록 정보를 삭제했습니다.");
        } catch (IOException e) {
            warn("등록 정보를 삭제하지 못했습니다: " + e.getMessage());
        }

        l.stage(4, 6, "방화벽 규칙 삭제");
        if (o.firewall) {
            removeFirewallRules();
        } else {
            l.log("방화벽 규칙 삭제: 건너뜀");
        }

        l.stage(5, 6, "사용자 데이터");
        if (o.deleteUserData) {
            try {
                Win.deleteRecursively(Installer.dataDir());
                l.log("출석 데이터와 설정을 삭제했습니다: " + Installer.dataDir());
            } catch (IOException e) {
                warn("사용자 데이터를 삭제하지 못했습니다: " + e.getMessage());
            }
        } else {
            l.log("출석 데이터는 그대로 남겨 두었습니다: " + Installer.dataDir());
        }

        l.stage(6, 6, "프로그램 파일 삭제");
        scheduleDeleteInstallDir(installDir);
        return true;
    }

    private void warn(String m) {
        warnings.add(m);
        l.log("[주의] " + m);
    }

    private void removeFirewallRules() {
        int port = 18080;
        Path settings = Installer.dataDir().resolve("settings.properties");
        if (Files.exists(settings)) {
            try (var in = new java.io.InputStreamReader(Files.newInputStream(settings), StandardCharsets.UTF_8)) {
                Properties p = new Properties();
                p.load(in);
                port = Integer.parseInt(p.getProperty("server.port", "18080").trim());
            } catch (Exception ignored) {
                // 기본값
            }
        }
        String[] names = { "Attendance Server (TCP " + port + ")", "Attendance Discovery (UDP 18081)",
                "Attendance Server (TCP)", "Attendance Discovery (UDP)" };
        StringBuilder script = new StringBuilder();
        for (String n : names) script.append("netsh advfirewall firewall delete rule name=").append(Win.q(n)).append("; ");
        l.log("방화벽 규칙을 삭제합니다. 곧 나오는 '관리자 권한 요청'에서 [예]를 눌러주세요.");
        if (Win.runElevated(script.toString())) l.log("방화벽 규칙을 삭제했습니다.");
        else warn("방화벽 규칙을 삭제하지 못했습니다 (관리자 권한 요청을 거부했을 수 있습니다). Windows 방화벽 설정에서 'Attendance'로 시작하는 규칙을 직접 삭제할 수 있습니다.");
    }

    // 이 제거 프로그램이 설치 폴더의 런타임으로 실행 중이라 지금은 그 파일들을 지울 수 없다. 별도의 PowerShell을 띄워서
    // 이 프로그램이 끝날 때까지 기다리며(최대 2분) 설치 폴더를 삭제하게 한다
    private void scheduleDeleteInstallDir(Path dir) {
        try {
            String script = "for($i=0;$i -lt 120;$i++){ try { if (Test-Path -LiteralPath " + Win.q(dir.toString())
                    + ") { Remove-Item -LiteralPath " + Win.q(dir.toString()) + " -Recurse -Force -ErrorAction Stop }; break } catch { Start-Sleep -Seconds 1 } }";
            String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded).start();
            l.log("프로그램 파일은 이 창이 닫힌 뒤 삭제됩니다: " + dir);
        } catch (IOException e) {
            warn("프로그램 파일을 삭제하지 못했습니다. 폴더를 직접 삭제해주세요: " + dir);
        }
    }
}
