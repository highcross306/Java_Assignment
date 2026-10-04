package setup;

import java.nio.file.Path;

// 설치/제거 옵션. 마법사 화면에서 고르거나, 명령줄(--silent 등)로 지정한다.
final class Options {

    boolean silent = false;
    boolean uninstall = false;
    boolean deleteUserData = false;   // 제거할 때 출석 데이터(DB, 백업)도 지울지 (기본: 남김)

    Path installDir = defaultInstallDir();
    boolean desktopShortcut = true;
    boolean startMenu = true;
    boolean autostart = true;         // Windows 시작 시 백그라운드로 자동 실행 (저장된 정보로 자동 로그인)
    boolean firewall = true;          // 방화벽 규칙 자동 등록 (관리자 권한 요청이 한 번 뜸)
    boolean android = true;           // Android 앱 설치(USB 또는 QR)
    boolean launchAfter = true;
    int port = 0;                     // 0 = 자동 (기본 18080, 쓸 수 없으면 다른 포트)

    static Path defaultInstallDir() {
        String local = System.getenv("LOCALAPPDATA");
        return Path.of(local != null ? local : System.getProperty("user.home"), "Programs", "AttendanceManager");
    }

    static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--silent" -> o.silent = true;
                case "--uninstall" -> o.uninstall = true;
                case "--delete-data" -> o.deleteUserData = true;
                case "--install-dir" -> o.installDir = Path.of(args[++i]);
                case "--no-desktop-shortcut" -> o.desktopShortcut = false;
                case "--no-start-menu" -> o.startMenu = false;
                case "--autostart" -> o.autostart = true;
                case "--no-autostart" -> o.autostart = false;
                case "--no-firewall" -> o.firewall = false;
                case "--no-android" -> o.android = false;
                case "--no-launch" -> o.launchAfter = false;
                case "--port" -> o.port = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("알 수 없는 옵션: " + args[i]);
            }
        }
        return o;
    }
}
