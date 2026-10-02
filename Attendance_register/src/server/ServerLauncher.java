package server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Duration;
import java.util.Enumeration;
import sync.DbFingerprint;
import sync.DriveClient;
import sync.DriveSync;
import sync.GoogleAuth;
import sync.SwingSyncUi;

// 예전에는 각 학원이 Postgres+Spring Boot 백엔드를 Render/Northflank 같은 클라우드에
// 직접 배포해서 운영해야 했는데, 이 과정(DB 계정 생성, 환경변수 설정, 슬립 문제 대응 등)이
// 비전문가에게는 너무 복잡했음. 대신 이 데스크탑 앱이 백엔드(attendance-server) jar를
// 실행할 때마다 자기 자신의 자식 프로세스로 같이 띄우고, DB도 클라우드 대신 이 PC 안에
// 파일로 저장되는 H2를 쓰도록(attendance-server의 "local" 프로필) 바꿔서, 데스크탑 앱만
// 켜면 별도 가입/설정 없이 서버가 함께 뜨게 함. Android 앱은 같은 와이파이 안에서
// 이 PC의 LAN 주소로 접속한다 (getLanAddressHint 참고).
public class ServerLauncher {

    // 8080은 이 개발 PC에서 Hyper-V/WSL의 동적 포트 예약 범위(8064~8163)에 걸려서
    // 아무 프로세스도 안 쓰는데도 bind가 거부되는 문제가 있었음 -> 18080으로 변경
    private static final int PORT = 18080;
    private static Process process;
    private static BackupManager backups;
    private static boolean stopped = false;
    private static SessionGuard sessionGuard; // 이 실행이 세션을 소유한 경우에만 non-null (중복 실행이면 null)
    private static boolean sessionRunning = false; // markRunning까지 마쳤는지 (정상 종료 표시의 전제)
    private static GoogleAuth googleAuth;
    private static DriveSync driveSync;
    private static final String NL = System.lineSeparator();

    public static int getPort() {
        return PORT;
    }

    public static int getDiscoveryPort() {
        return DISCOVERY_PORT;
    }

    public static String getBaseUrl() {
        return "http://localhost:" + PORT;
    }

    public static boolean isRunning() {
        return process != null && process.isAlive();
    }

    // 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지를 반환
    public static String start() {
        File jar = findServerJar();
        if (jar == null) {
            return "내장 서버(attendance-server jar)를 찾을 수 없습니다.\n"
                    + "attendance-server 프로젝트를 먼저 빌드한 뒤(mvn package),\n"
                    + "그 jar 경로를 -Dattendance.server.jar=경로 로 지정해주세요.";
        }

        File dataDir = new File(System.getProperty("user.home"), ".attendance-desktop");
        dataDir.mkdirs();

        // 이미 실행 중인 프로그램이 있으면 여기서 멈춘다. 이 확인이 없으면 아래의 "남은 서버 정리"가 실행 중인
        // 프로그램의 서버를 종료시켜서, 먼저 켜 둔 창이 서버 없이 남게 된다 (실제로 확인함)
        SessionGuard guard = new SessionGuard(dataDir);
        SessionGuard.Result session;
        try {
            session = guard.acquire();
        } catch (IOException e) {
            return "실행 상태를 확인하지 못했습니다: " + e.getMessage();
        }
        if (session == SessionGuard.Result.ALREADY_RUNNING) {
            return "출석 관리 프로그램이 이미 실행 중입니다." + NL
                    + "열려 있는 창을 사용하거나, 그 창을 닫은 뒤 다시 실행해주세요.";
        }
        sessionGuard = guard;

        // 이전 실행에서 남은 서버가 포트를 잡고 있으면 새 서버가 뜨지 못하는데, 그 잔존 서버가
        // /api/health에는 응답하므로 "성공"으로 오판하게 됨 -> 먼저 정리하고, 못 하면 원인을 알려줌
        String portError = ensurePortAvailable();
        if (portError != null) return portError;

        // 서버가 DB 파일을 열기 전에: (동기화를 켠 경우) 드라이브의 최신 데이터 받기 -> 예약된 복원 적용 + 오늘 백업.
        // 동기화 실패(오프라인 등)는 앱 실행을 막지 않고 상태 메시지로만 남는다
        backups = new BackupManager(dataDir);
        File appDir = getAppDir();
        googleAuth = new GoogleAuth(dataDir,
                new File(appDir, "google-client.json"),
                new File(appDir.getParentFile() == null ? appDir : appDir.getParentFile(), "google-client.json"),
                new File(dataDir, "google-client.json"));
        driveSync = new DriveSync(dataDir, () -> new DriveClient(googleAuth),
                new SwingSyncUi(), name -> backups.snapshot(name),
                file -> DbFingerprint.compute(file, findH2Jar(), findJavaExecutable()));

        // 지난 실행이 강제 종료/정전 등으로 끝났다면 종료 때 했어야 할 일(마지막 상태 백업, 변경 내용 드라이브 업로드)을
        // 건너뛴 것이므로 지금 대신 한다. 서버는 위에서 정리됐고 아직 새로 띄우기 전이라 DB 파일이 닫혀 있다.
        // 업로드는 드라이브가 그 사이 바뀌지 않았을 때만 되고, 바뀌었다면 아래 시작 시 동기화에서 충돌로 처리됨
        if (session == SessionGuard.Result.PREVIOUS_EXIT_UNCLEAN) {
            System.err.println("지난 실행이 정상 종료되지 않아 종료 절차(백업, 동기화)를 지금 수행합니다");
            backups.onShutdown();
            driveSync.afterServerStop();
        }
        driveSync.beforeServerStart();
        backups.onStartup();

        try {
            sessionGuard.markRunning(); // 이 시점부터 DB가 바뀔 수 있음. 정상 종료 절차를 마치면 stop()이 지움
            sessionRunning = true;
        } catch (IOException e) {
            return "실행 상태를 기록하지 못했습니다: " + e.getMessage();
        }

        String jwtSecret;
        try {
            jwtSecret = loadOrCreateJwtSecret(dataDir);
        } catch (IOException e) {
            return "인증 키 파일을 만들지 못했습니다: " + e.getMessage();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    findJavaExecutable(), "-jar", jar.getAbsolutePath(),
                    "--spring.profiles.active=local");
            pb.directory(dataDir);
            // 서버의 JWT 서명 키(application-local.properties의 jwt.secret)를 PC마다 다른
            // 무작위 값으로 덮어씀. 고정 기본값이면 같은 Wi-Fi의 누구나 토큰을 위조할 수 있음
            pb.environment().put("JWT_SECRET", jwtSecret);
            // 이 앱이 강제 종료돼도 서버가 포트를 붙잡은 채 남지 않도록, 서버가 이 PID를 감시하다가
            // 사라지면 스스로 종료함 (서버 쪽 ParentProcessWatcher)
            pb.environment().put("ATTENDANCE_PARENT_PID", String.valueOf(ProcessHandle.current().pid()));
            pb.redirectOutput(new File(dataDir, "server.log"));
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            return "내장 서버 실행 실패: " + e.getMessage();
        }

        // 종료 시 stop()은 Main의 종료 훅이 "데이터 저장 -> 서버 종료" 순서로 직접 호출함
        // (여기서 따로 훅을 등록하면 저장보다 서버가 먼저 꺼질 수 있음)

        File log = new File(dataDir, "server.log");
        if (!waitUntilHealthy(process, Duration.ofSeconds(30))) {
            if (!process.isAlive()) {
                return "내장 서버가 시작 직후 종료되었습니다. 로그를 확인해주세요:" + NL + log.getAbsolutePath();
            }
            return "내장 서버가 30초 안에 응답하지 않습니다. 로그를 확인해주세요:" + NL + log.getAbsolutePath();
        }
        startDiscoveryResponder();
        return null;
    }

    public static synchronized void stop() {
        if (stopped) return;
        stopped = true;
        stopDiscoveryResponder();
        if (process != null) {
            process.destroy();
            try {
                // DB 파일이 닫힌 뒤에 백업해야 일관된 복사본이 됨
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        boolean serverStopped = process == null || !process.isAlive();
        // 서버가 아직 DB를 잡고 있으면 파일이 일관되지 않을 수 있으므로 백업/업로드를 하지 않고,
        // "정상 종료" 표시도 하지 않는다 -> 다음 실행 때 건너뛴 종료 절차를 대신 수행함
        if (serverStopped) {
            if (backups != null) {
                backups.onShutdown();
            }
            if (driveSync != null) {
                driveSync.afterServerStop();
            }
            // 복구 절차를 거쳐 markRunning까지 간 실행만 "정상 종료"로 기록한다. 그 전에 시작이 실패했다면
            // (포트 충돌 등) 지난번의 비정상 종료 흔적을 지우면 안 되므로 그대로 둔다
            if (sessionGuard != null && sessionRunning) {
                sessionGuard.markClean();
            }
        }
    }

    public static GoogleAuth getGoogleAuth() {
        return googleAuth;
    }

    public static DriveSync getDriveSync() {
        return driveSync;
    }

    // 18080을 쓸 수 있는 상태로 만든다. 이전 실행에서 남은 attendance-server가 잡고 있으면 종료시키고,
    // 다른 프로그램이 잡고 있거나 Windows가 예약한 포트면 사용자에게 원인을 알린다. 성공하면 null
    private static String ensurePortAvailable() {
        if (isPortFree()) return null;

        long pid = findListeningPid();
        if (pid > 0) {
            ProcessHandle owner = ProcessHandle.of(pid).orElse(null);
            String command = owner == null ? "" : commandLineOf(pid);
            if (owner != null && command.contains("attendance-server")) {
                owner.destroy();
                if (!waitForPortFree(5000)) {
                    owner.destroyForcibly();
                    waitForPortFree(5000);
                }
            }
        }
        if (isPortFree()) return null;

        if (pid > 0) {
            return "포트 " + PORT + "을(를) 다른 프로그램(PID " + pid + ")이 사용 중입니다." + NL
                    + "그 프로그램을 종료한 뒤 다시 실행해주세요.";
        }
        return "포트 " + PORT + "을(를) 사용할 수 없습니다." + NL
                + "(Windows가 이 포트를 예약했을 수 있습니다. 관리자에게 문의해주세요.)";
    }

    // Windows에서는 Java의 ProcessHandle.info().commandLine()이 다른 프로세스에 대해 비어 있는 경우가 많아서
    // (실제로 확인함) PowerShell의 CIM 조회로 명령줄을 가져옴. 실패하면 빈 문자열
    private static String commandLineOf(long pid) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "(Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid + "').CommandLine")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            return out.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isPortFree() {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(PORT)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean waitForPortFree(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (isPortFree()) return true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isPortFree();
    }

    // netstat 출력에서 PORT를 LISTENING 중인 프로세스의 PID를 찾음 (못 찾으면 -1)
    private static long findListeningPid() {
        try {
            Process p = new ProcessBuilder("netstat", "-ano", "-p", "tcp").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            for (String line : out.split(NL)) {
                String[] cols = line.trim().split("\\s+");
                if (cols.length >= 5 && cols[3].equalsIgnoreCase("LISTENING") && cols[1].endsWith(":" + PORT)) {
                    return Long.parseLong(cols[4]);
                }
            }
        } catch (Exception ignored) {
            // 찾지 못하면 -1 -> 호출한 쪽에서 일반 메시지로 대체
        }
        return -1;
    }

    public static BackupManager getBackupManager() {
        return backups;
    }

    // 처음 실행할 때 무작위 키를 만들어 dataDir/jwt.secret에 저장하고, 이후에는 그 값을 재사용
    // (재사용하지 않으면 앱을 켤 때마다 발급된 토큰이 전부 무효가 되어 매번 재로그인해야 함)
    private static String loadOrCreateJwtSecret(File dataDir) throws IOException {
        File file = new File(dataDir, "jwt.secret");
        if (file.isFile()) {
            String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            if (existing.length() >= 32) return existing;
        }
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(file.toPath(), secret, StandardCharsets.UTF_8);
        // 다른 사용자 계정이 읽지 못하도록 최대한 제한 (Windows에서는 지원이 제한적이라 실패해도 무시)
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        return secret;
    }

    // Android 앱이 서버 주소를 직접 입력받지 않고 같은 와이파이 안에서 자동으로 찾을 수
    // 있도록, UDP 브로드캐스트로 "ATTENDANCE_DISCOVER_V1" 질의가 오면 이 PC의 서버 포트를
    // 알려주는 응답기. 멀티캐스트 대신 브로드캐스트를 쓴 건 Android에서 별도 권한
    // (CHANGE_WIFI_MULTICAST_STATE)이 필요 없기 때문. 이 포트/문자열은 Android의
    // network/ServerDiscovery.kt와 반드시 일치해야 함
    private static final int DISCOVERY_PORT = 18081;
    private static final String DISCOVERY_QUERY = "ATTENDANCE_DISCOVER_V1";
    private static final String DISCOVERY_REPLY_PREFIX = "ATTENDANCE_SERVER_V1:";
    private static DatagramSocket discoverySocket;
    private static Thread discoveryThread;

    private static void startDiscoveryResponder() {
        try {
            discoverySocket = new DatagramSocket(DISCOVERY_PORT);
            discoverySocket.setBroadcast(true);
        } catch (IOException e) {
            // 발견 응답은 편의 기능일 뿐이라 실패해도 서버 자체는 계속 정상 동작해야 함
            // (예: 이 포트가 이미 쓰이는 중이면 Android 쪽은 그냥 예전 방식대로 동작 못하게 됨)
            System.err.println("자동 검색 응답 소켓을 열지 못했습니다: " + e.getMessage());
            return;
        }

        discoveryThread = new Thread(() -> {
            byte[] buffer = new byte[256];
            while (!discoverySocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    discoverySocket.receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    if (DISCOVERY_QUERY.equals(received)) {
                        byte[] replyBytes = (DISCOVERY_REPLY_PREFIX + PORT).getBytes(StandardCharsets.UTF_8);
                        discoverySocket.send(new DatagramPacket(
                                replyBytes, replyBytes.length, packet.getAddress(), packet.getPort()));
                    }
                } catch (IOException ignored) {
                    // stop()에서 소켓을 닫으면 receive()가 예외를 던지며 루프가 자연히 끝남
                }
            }
        }, "attendance-discovery-responder");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private static void stopDiscoveryResponder() {
        if (discoverySocket != null) {
            discoverySocket.close();
        }
    }

    // 서버 jar 위치를 찾는 순서: 실행 시 직접 지정한 경로(개발용) -> 앱 설치 폴더 기준
    // server/attendance-server.jar (배포판에 같이 들어있음). 작업 디렉터리(cwd)는 바로가기로
    // 실행하면 달라질 수 있어서, 앱 자신의 위치(jar/클래스 경로)를 기준으로 찾는다
    private static File findServerJar() {
        String override = System.getProperty("attendance.server.jar");
        if (override != null) {
            File f = new File(override);
            if (f.isFile()) return f;
        }

        File appDir = getAppDir();
        File[] candidates = {
                new File(appDir, "server/attendance-server.jar"),
                new File(appDir.getParentFile() == null ? appDir : appDir.getParentFile(),
                        "server/attendance-server.jar"),
                new File("server/attendance-server.jar")
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) return candidate;
        }
        return null;
    }

    // 동기화가 "DB 내용이 바뀌었는지" 비교할 때 쓰는 H2 도구 jar (서버 jar 안에 들어 있는 것과 같은 버전을
    // 배포본의 server/h2.jar로 함께 넣어둠). 개발 중에는 -Dattendance.h2.jar=경로 로 지정
    private static File findH2Jar() {
        String override = System.getProperty("attendance.h2.jar");
        if (override != null) return new File(override);
        File serverJar = findServerJar();
        return serverJar == null ? null : new File(serverJar.getParentFile(), "h2.jar");
    }

    // 이 앱이 실행 중인 폴더. jar로 실행되면 그 jar가 있는 폴더, Eclipse처럼 클래스 폴더로
    // 실행되면 그 클래스 폴더(bin)
    public static File getAppDir() {
        try {
            File location = new File(ServerLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return location.isFile() ? location.getParentFile() : location;
        } catch (Exception e) {
            return new File(".").getAbsoluteFile();
        }
    }

    // PATH의 "java"에 의존하면 Java가 따로 설치돼 있지 않은 PC(런타임을 포함한 설치본
    // 사용 시)에서 실패하므로, 지금 이 앱을 실행 중인 JVM의 java를 그대로 사용한다
    private static String findJavaExecutable() {
        String exe = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        File f = new File(System.getProperty("java.home"), "bin" + File.separator + exe);
        return f.isFile() ? f.getAbsolutePath() : "java";
    }

    private static boolean waitUntilHealthy(Process child, Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            // 우리가 띄운 자식이 이미 죽었다면(포트 충돌 등) 다른 프로세스가 응답하더라도 성공이 아님
            if (!child.isAlive()) return false;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + PORT + "/api/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET().build();
                HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() == 200) return true;
            } catch (Exception ignored) {
                // 서버가 아직 뜨는 중이면 연결이 거부될 수 있음 - 재시도
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // VirtualBox/Hyper-V/VPN 소프트웨어가 깔린 PC에서는 이런 이름의 가상 어댑터도
    // 사설 IP(192.168.x.x, 172.x.x.x)를 들고 있는 경우가 많음. Java의 isVirtual()은
    // 이런 어댑터들을 걸러주지 못해서(Windows에서 전부 false로 나옴, 실제로 이 PC에서
    // 확인함), 이름으로 직접 걸러야 진짜 와이파이/이더넷 주소를 안정적으로 찾을 수 있음
    private static final String[] IGNORED_ADAPTER_KEYWORDS = {
            "virtualbox", "vmware", "hyper-v", "docker", "wsl",
            "zerotier", "hamachi", "tailscale", "loopback", "vpn", "teredo"
    };

    // Android 앱 로그인 화면에 입력해줄, 같은 와이파이 안에서 이 PC를 가리키는 주소
    public static String getLanAddressHint() {
        try {
            String fallback = null;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                String name = iface.getDisplayName() == null ? "" : iface.getDisplayName().toLowerCase();
                boolean looksVirtual = false;
                for (String keyword : IGNORED_ADAPTER_KEYWORDS) {
                    if (name.contains(keyword)) { looksVirtual = true; break; }
                }

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        if (!looksVirtual) return addr.getHostAddress() + ":" + PORT;
                        if (fallback == null) fallback = addr.getHostAddress() + ":" + PORT;
                    }
                }
            }
            // 진짜 와이파이/이더넷으로 보이는 게 하나도 없으면(드묾), 그래도 가상 어댑터
            // 주소라도 안내하는 게 아무것도 안 보여주는 것보다 나음
            if (fallback != null) return fallback;
        } catch (Exception ignored) {
            // 아래 안내 문구로 대체
        }
        return "이 PC의 IP 주소:" + PORT + " (Wi-Fi 설정에서 IP 확인 필요)";
    }
}
