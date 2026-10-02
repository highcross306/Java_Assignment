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

        // 서버가 DB 파일을 열기 전에: 예약된 복원 적용 + 오늘 백업
        backups = new BackupManager(dataDir);
        backups.onStartup();

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
            pb.redirectOutput(new File(dataDir, "server.log"));
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            return "내장 서버 실행 실패: " + e.getMessage();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(ServerLauncher::stop));

        if (!waitUntilHealthy(Duration.ofSeconds(20))) {
            return "내장 서버가 20초 안에 응답하지 않습니다. 로그를 확인해주세요: "
                    + new File(dataDir, "server.log").getAbsolutePath();
        }
        startDiscoveryResponder();
        return null;
    }

    public static void stop() {
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
        if (backups != null) {
            backups.onShutdown();
        }
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

    // 이 앱이 실행 중인 폴더. jar로 실행되면 그 jar가 있는 폴더, Eclipse처럼 클래스 폴더로
    // 실행되면 그 클래스 폴더(bin)
    private static File getAppDir() {
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

    private static boolean waitUntilHealthy(Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
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
