package setup;

import server.FirewallSetup;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// 설치 절차. 화면(마법사)과 분리해 두어서 명령줄(--silent)로도, 시험에서도 똑같이 실행된다.
// 필수 단계(파일 복사 등)가 실패하면 설치를 중단하고, 선택 단계(바로가기, 방화벽, Android 등)가 실패하면
// 경고로 남기고 계속한다. 설치가 끝나면 무엇이 되었고 무엇이 안 되었는지 결과에 모두 담는다.
final class Installer {

    static final String VERSION = "1.0.0";
    static final long REQUIRED_BYTES = 700L * 1024 * 1024;
    private static final int DISCOVERY_UDP_PORT = 18081;
    private static final int[] PORT_CANDIDATES = { 18080, 28080, 38080, 48080, 18180, 28180, 38180, 48180 };

    interface Listener {
        void stage(int index, int total, String title);
        void log(String message);
    }

    static final class Result {
        boolean success;
        String failure;                       // success == false일 때 이유
        int port = 18080;
        Path installDir;
        final List<String> warnings = new ArrayList<>();
        // Android: 자동 설치 결과 또는, 폰이 내려받을 수 있게 제공 중인 주소
        String androidMessage = "";
        String androidUrl;                    // null이 아니면 QR/주소로 설치 안내
        AndroidInstall.ApkServer apkServer;   // 설치 창이 열려 있는 동안 유지 (마법사가 닫을 때 close)
        Path apkPath;
    }

    private static final int TOTAL_STAGES = 9;

    private final Options o;
    private final Listener l;
    private final Result r = new Result();
    private int stageNo = 0;

    Installer(Options options, Listener listener) {
        this.o = options;
        this.l = listener;
        r.installDir = options.installDir;
    }

    Result run() {
        try {
            checkEnvironment();
            stopRunningApp();
            choosePort();
            copyFiles();
            createShortcuts();
            registerUninstall();
            configureAutostart();
            configureFirewall();
            installAndroid();
            r.success = true;
            l.log("설치가 완료되었습니다.");
        } catch (FatalException e) {
            r.success = false;
            r.failure = e.getMessage();
            l.log("[실패] " + e.getMessage());
        } catch (Exception e) {
            r.success = false;
            r.failure = "예상하지 못한 오류: " + e;
            l.log("[실패] " + r.failure);
        }
        return r;
    }

    // 설치를 계속할 수 없는 오류
    private static final class FatalException extends Exception {
        FatalException(String message) { super(message); }
    }

    private void stage(String title) {
        stageNo++;
        l.stage(stageNo, TOTAL_STAGES, title);
    }

    private void warn(String message) {
        r.warnings.add(message);
        l.log("[주의] " + message);
    }

    // ---------------------------------------------------------------- 1. 환경 확인

    private void checkEnvironment() throws FatalException {
        stage("설치 환경 확인");
        if (!Win.isWindows()) throw new FatalException("Windows에서만 설치할 수 있습니다.");
        if (!Files.isDirectory(payloadDir())) {
            throw new FatalException("설치 파일(payload 폴더)을 찾을 수 없습니다: " + payloadDir()
                    + " - 설치 프로그램을 압축을 푼 폴더에서 실행해주세요.");
        }
        Path probe = o.installDir;
        while (probe != null && !Files.exists(probe)) probe = probe.getParent();
        try {
            long free = probe == null ? Long.MAX_VALUE : Files.getFileStore(probe).getUsableSpace();
            if (free < REQUIRED_BYTES) {
                throw new FatalException("설치할 드라이브의 여유 공간이 부족합니다. (필요 약 " + (REQUIRED_BYTES >> 20) + "MB, 여유 " + (free >> 20) + "MB)");
            }
            l.log("설치 위치: " + o.installDir + " (여유 공간 " + (free >> 20) + "MB)");
        } catch (IOException e) {
            warn("여유 공간을 확인하지 못했습니다: " + e.getMessage());
        }
    }

    // payload 폴더: 설치 프로그램 폴더 안의 payload (개발/시험 중에는 -Dsetup.payload로 지정)
    static Path payloadDir() {
        String override = System.getProperty("setup.payload");
        if (override != null) return Path.of(override);
        return appHome().resolve("payload");
    }

    // 설치 프로그램이 있는 폴더 (jpackage 구조에서는 app 폴더의 부모)
    static Path appHome() {
        try {
            File jar = new File(Installer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jar.isFile() ? jar.getParentFile() : jar;           // app 폴더 (setup.jar가 있는 곳)
            File parent = dir.getParentFile();
            return (parent != null ? parent : dir).toPath();
        } catch (Exception e) {
            return Path.of(".").toAbsolutePath();
        }
    }

    static Path dataDir() {
        return Path.of(System.getProperty("user.home"), ".attendance-desktop");
    }

    // ---------------------------------------------------------------- 2. 실행 중인 프로그램 종료

    // 이미 설치돼서 실행 중이면(업데이트 등) 정상 종료를 요청한다: 저장/백업/동기화까지 하고 끝나도록
    // exit.signal 파일을 만들면 실행 중인 프로그램이 알아보고 스스로 종료함. 실행 중인지는 파일 잠금으로 확인
    private void stopRunningApp() throws FatalException {
        stage("실행 중인 프로그램 확인");
        if (!requestExit(dataDir(), l::log)) {
            throw new FatalException("실행 중인 출석 관리 프로그램을 종료하지 못했습니다. "
                    + "프로그램을 직접 종료(알림 영역 아이콘 → 완전히 종료)한 뒤 다시 시도해주세요.");
        }
    }

    // 실행 중인 프로그램이 있으면 정상 종료를 요청하고 끝날 때까지(최대 45초) 기다림. 종료됐거나 원래 없었으면 true
    static boolean requestExit(Path dataDir, java.util.function.Consumer<String> log) {
        if (!isAppRunning(dataDir)) {
            log.accept("실행 중인 프로그램이 없습니다.");
            return true;
        }
        log.accept("실행 중인 출석 관리 프로그램을 종료하는 중... (저장과 백업을 마치고 끝납니다)");
        try {
            Files.createDirectories(dataDir);
            Files.createFile(dataDir.resolve("exit.signal"));
        } catch (IOException e) {
            // 이미 있어도 상관없음
        }
        for (int i = 0; i < 90; i++) {
            sleep(500);
            if (!isAppRunning(dataDir)) {
                log.accept("실행 중이던 프로그램을 종료했습니다.");
                return true;
            }
        }
        return false;
    }

    // 앱이 session.lock에 잡고 있는 파일 잠금을 우리가 잡을 수 있는지로 판단 (프로세스가 죽으면 OS가 잠금을 풀어줌)
    static boolean isAppRunning(Path dataDir) {
        Path lockFile = dataDir.resolve("session.lock");
        if (!Files.exists(lockFile)) return false;
        try (FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
            FileLock lock = ch.tryLock();
            if (lock == null) return true;
            lock.release();
            return false;
        } catch (OverlappingFileLockException e) {
            return true;
        } catch (IOException e) {
            return true; // 열 수 없으면 사용 중인 것으로 간주
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- 3. 포트

    // 서버 기본 포트(18080)를 쓸 수 있는지 확인하고, 안 되면(Hyper-V/WSL이 예약했거나 다른 프로그램이 사용 중) 다른 포트를 고른다
    private void choosePort() {
        stage("서버 포트 확인");
        int chosen = -1;
        if (o.port > 0) {
            chosen = canBindTcp(o.port) ? o.port : -1;
            if (chosen < 0) warn("지정한 포트 " + o.port + "를 사용할 수 없습니다. 다른 포트를 찾습니다.");
        }
        if (chosen < 0) {
            for (int candidate : PORT_CANDIDATES) {
                if (canBindTcp(candidate)) { chosen = candidate; break; }
            }
        }
        if (chosen < 0) {
            warn("사용할 수 있는 포트를 찾지 못했습니다. 기본 포트(18080)로 설정합니다. 프로그램을 실행할 때 다른 프로그램을 종료해야 할 수 있습니다.");
            chosen = 18080;
        }
        r.port = chosen;
        if (chosen == 18080) {
            l.log("서버 포트: 18080 (기본값)");
            // 이전 설치에서 다른 포트를 적어 뒀다면, 기본 포트를 쓸 수 있는 지금은 지움
            try {
                Path settings = dataDir().resolve("settings.properties");
                if (Files.exists(settings)) writeSettingsPort(null);
            } catch (IOException e) {
                warn("설정 파일을 갱신하지 못했습니다: " + e.getMessage());
            }
        } else {
            l.log("기본 포트(18080)를 쓸 수 없어서 포트 " + chosen + "로 설정합니다.");
            try {
                writeSettingsPort(chosen);
            } catch (IOException e) {
                warn("포트 설정을 저장하지 못했습니다: " + e.getMessage());
            }
        }
        if (!canBindUdp(DISCOVERY_UDP_PORT)) {
            warn("폰이 PC를 자동으로 찾는 데 쓰는 포트(UDP " + DISCOVERY_UDP_PORT + ")를 쓸 수 없습니다. "
                    + "자동 검색이 안 되면 폰 앱의 [서버 주소 직접 입력]으로 연결하세요.");
        }
    }

    static boolean canBindTcp(int port) {
        try (ServerSocket s = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean canBindUdp(int port) {
        try (DatagramSocket s = new DatagramSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ~/.attendance-desktop/settings.properties 의 server.port (앱이 시작할 때 읽음). null이면 항목을 지움
    static void writeSettingsPort(Integer port) throws IOException {
        Files.createDirectories(dataDir());
        Path file = dataDir().resolve("settings.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (var in = new java.io.InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                p.load(in);
            }
        }
        if (port == null) p.remove("server.port");
        else p.setProperty("server.port", String.valueOf(port));
        try (var out = new java.io.OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            p.store(out, "Attendance Manager settings");
        }
    }

    // ---------------------------------------------------------------- 4. 파일 복사

    private void copyFiles() throws FatalException {
        stage("프로그램 파일 설치");
        Path dest = o.installDir;
        try {
            // 업데이트 시 이전 버전 파일을 먼저 지움 (사용자 데이터는 설치 폴더가 아니라 ~/.attendance-desktop에 있어서 안전)
            for (String name : new String[] { "app", "runtime", "setup" }) {
                Win.deleteRecursively(dest.resolve(name));
            }
            Files.createDirectories(dest);

            // 런타임: 이 설치 프로그램이 실행 중인 번들 런타임(java.home)을 그대로 설치 (앱과 서버가 같은 모듈 구성을 씀)
            Path runtime = Path.of(System.getProperty("java.home"));
            l.log("Java 런타임 복사 중...");
            copyTree(runtime, dest.resolve("runtime"));

            l.log("프로그램 파일 복사 중...");
            copyTree(payloadDir(), dest);

            // 제거 프로그램이 쓸 파일(설치 프로그램 자신)
            Path setupDir = dest.resolve("setup");
            Files.createDirectories(setupDir);
            Path appDir = appHome().resolve("app");
            if (Files.isDirectory(appDir)) {
                try (var stream = Files.list(appDir)) {
                    for (Path f : (Iterable<Path>) stream.filter(p -> p.toString().endsWith(".jar"))::iterator) {
                        Files.copy(f, setupDir.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            r.apkPath = dest.resolve("AttendanceApp.apk");
            l.log("복사 완료: " + dest);
        } catch (IOException e) {
            throw new FatalException("파일을 설치하지 못했습니다: " + e.getMessage());
        }
    }

    static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Path target = dst.resolve(src.relativize(file).toString());
                // jpackage가 만든 실행 파일(AttendanceManager.exe)은 읽기 전용이라서, 업데이트 때 덮어쓰려면 먼저 속성을 풀어야 함
                // (Java의 REPLACE_EXISTING은 읽기 전용 파일을 지우지 못함)
                Win.makeWritable(target);
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    // ---------------------------------------------------------------- 5. 바로가기

    private void createShortcuts() {
        stage("바로가기 만들기");
        Path exe = o.installDir.resolve("AttendanceManager.exe");
        try {
            if (o.desktopShortcut) {
                Path lnk = Win.desktopDir().resolve("출석 관리.lnk");
                Win.createShortcut(lnk, exe, "", o.installDir, exe, "학원 출석 관리 프로그램");
                l.log("바탕화면 바로가기: " + lnk);
            }
            if (o.startMenu) {
                Path folder = Win.programsDir().resolve("출석 관리");
                Win.createShortcut(folder.resolve("출석 관리.lnk"), exe, "", o.installDir, exe, "학원 출석 관리 프로그램");
                Win.createShortcut(folder.resolve("출석 관리 제거.lnk"), uninstallJavaw(), uninstallArguments(), o.installDir, exe, "출석 관리 프로그램 제거");
                l.log("시작 메뉴 바로가기: " + folder);
            }
        } catch (IOException | RuntimeException e) {
            warn("바로가기를 만들지 못했습니다: " + e.getMessage());
        }
    }

    // 제거: 설치 폴더의 런타임(javaw)으로 setup.jar를 --uninstall로 실행
    private Path uninstallJavaw() {
        return o.installDir.resolve("runtime").resolve("bin").resolve("javaw.exe");
    }

    private String uninstallArguments() {
        // 클래스패스의 와일드카드(*)는 Path로 만들 수 없는 문자(Windows)라서 문자열로 직접 이어 붙인다
        String setupDir = o.installDir.resolve("setup").toString();
        return "-cp \"" + setupDir + File.separator + "setup.jar;" + setupDir + File.separator + "*\" setup.SetupMain --uninstall";
    }

    // ---------------------------------------------------------------- 6. 프로그램 추가/제거 등록

    private void registerUninstall() {
        stage("제거 정보 등록");
        try {
            String key = Win.uninstallKey();
            Win.regSetString(key, "DisplayName", "출석 관리 프로그램");
            Win.regSetString(key, "DisplayVersion", VERSION);
            Win.regSetString(key, "Publisher", "Attendance Manager");
            Win.regSetString(key, "InstallLocation", o.installDir.toString());
            Win.regSetString(key, "DisplayIcon", o.installDir.resolve("AttendanceManager.exe").toString());
            Win.regSetString(key, "UninstallString", "\"" + uninstallJavaw() + "\" " + uninstallArguments());
            Win.regSetDword(key, "NoModify", 1);
            Win.regSetDword(key, "NoRepair", 1);
            l.log("Windows 설정 > 앱 목록에 '출석 관리 프로그램'이 등록되었습니다.");
        } catch (IOException | RuntimeException e) {
            warn("제거 정보를 등록하지 못했습니다 (앱 목록에서 제거할 수 없을 수 있습니다): " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 7. 자동 실행

    private void configureAutostart() {
        stage("자동 실행 설정");
        try {
            if (o.autostart) {
                Win.regSetString(Win.runKey(), Win.APP_KEY_NAME,
                        "\"" + o.installDir.resolve("AttendanceManager.exe") + "\" --background");
                l.log("Windows를 시작하면 프로그램이 백그라운드로 자동 실행됩니다.");
            } else {
                Win.regDeleteValue(Win.runKey(), Win.APP_KEY_NAME);
                l.log("자동 실행: 사용 안 함");
            }
        } catch (IOException | RuntimeException e) {
            warn("자동 실행을 설정하지 못했습니다: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 8. 방화벽

    private void configureFirewall() {
        stage("방화벽 설정");
        if (!o.firewall) {
            l.log("방화벽 자동 설정: 건너뜀 (Android 앱이 연결되지 않으면 프로그램 처음 실행 때 다시 안내합니다)");
            return;
        }
        if (FirewallSetup.isConfigured(r.port, DISCOVERY_UDP_PORT)) {
            l.log("방화벽 규칙이 이미 설정되어 있습니다.");
            return;
        }
        l.log("방화벽 허용 규칙을 추가합니다. 곧 나오는 '관리자 권한 요청'에서 [예]를 눌러주세요.");
        if (FirewallSetup.configure(r.port, DISCOVERY_UDP_PORT)) {
            l.log("방화벽 규칙을 추가했습니다. (TCP " + r.port + ", UDP " + DISCOVERY_UDP_PORT + ")");
        } else {
            warn("방화벽 규칙을 추가하지 못했습니다. Android 앱이 PC에 연결되지 않을 수 있습니다. "
                    + "프로그램을 처음 실행할 때 다시 안내합니다.");
        }
    }

    // ---------------------------------------------------------------- 9. Android 앱

    private void installAndroid() {
        stage("Android 앱 설치");
        if (!o.android) {
            l.log("Android 앱 설치: 건너뜀 (설치 폴더의 AttendanceApp.apk를 폰에 설치하면 됩니다)");
            return;
        }
        if (r.apkPath == null || !Files.isRegularFile(r.apkPath)) {
            warn("Android 앱 파일(AttendanceApp.apk)이 설치 파일에 들어 있지 않습니다.");
            return;
        }

        // 1) USB로 연결된 폰이 있으면 자동 설치
        String adb = AndroidInstall.findAdb(appHome());
        if (adb != null) {
            try {
                List<String> devices = AndroidInstall.authorizedDevices(adb);
                if (!devices.isEmpty()) {
                    int ok = 0;
                    for (String serial : devices) {
                        l.log("USB로 연결된 폰(" + serial + ")에 설치하는 중...");
                        String error = AndroidInstall.install(adb, serial, r.apkPath);
                        if (error == null) {
                            ok++;
                            l.log("폰 " + serial + ": 설치 완료");
                        } else {
                            warn("폰 " + serial + " 설치 실패: " + error);
                        }
                    }
                    if (ok > 0) {
                        r.androidMessage = "USB로 연결된 폰 " + ok + "대에 앱을 설치했습니다.";
                        return;
                    }
                } else {
                    l.log("USB로 연결된 폰이 없습니다. (연결하려면 폰의 USB 디버깅을 켜야 합니다)");
                }
            } catch (IOException e) {
                warn("adb로 폰을 확인하지 못했습니다: " + e.getMessage());
            }
        } else {
            l.log("adb(Android SDK)를 찾지 못했습니다. QR 코드 방식으로 안내합니다.");
        }

        // 2) QR/주소로 폰이 내려받게 함 (마법사가 열려 있는 동안 제공). 무인 설치에서는 제공하지 않음
        if (o.silent) {
            r.androidMessage = "무인 설치라 폰 설치는 건너뛰었습니다. 설치 폴더의 AttendanceApp.apk를 폰에 설치하세요.";
            return;
        }
        String host = LanAddress.find();
        if (host == null) {
            warn("같은 Wi-Fi의 PC 주소를 찾지 못해서 QR 코드를 만들 수 없습니다. 설치 폴더의 AttendanceApp.apk를 폰에 옮겨서 설치하세요.");
            return;
        }
        try {
            r.apkServer = new AndroidInstall.ApkServer(r.apkPath, r.port);
            r.androidUrl = r.apkServer.url(host);
            r.androidMessage = "폰의 카메라로 QR 코드를 스캔하거나, 폰 브라우저에 아래 주소를 입력해서 앱을 내려받아 설치하세요. "
                    + "(폰이 이 PC와 같은 Wi-Fi에 연결되어 있어야 합니다)";
            l.log("폰 설치용 주소를 열었습니다: " + r.androidUrl);
        } catch (IOException e) {
            warn("폰 설치용 주소를 열지 못했습니다: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 설치 후 실행

    static void launchApp(Path installDir) throws IOException {
        Path exe = installDir.resolve("AttendanceManager.exe");
        new ProcessBuilder(exe.toString()).directory(installDir.toFile()).start();
    }
}
