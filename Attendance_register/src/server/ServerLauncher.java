package server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final int PORT = 8080;
    private static Process process;

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

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", jar.getAbsolutePath(),
                    "--spring.profiles.active=local");
            pb.directory(dataDir);
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
        return null;
    }

    public static void stop() {
        if (process != null) {
            process.destroy();
        }
    }

    // jar 위치를 찾는 순서: 실행 시 직접 지정한 경로 -> 앱과 같은 폴더의 server/ 하위
    // (나중에 실제 배포판을 만들 때 이 자리에 jar를 함께 넣어두면 됨) -> 지금 이 개발
    // 환경에서 백엔드 저장소가 있는 고정 경로(개발 편의용)
    private static File findServerJar() {
        String override = System.getProperty("attendance.server.jar");
        if (override != null) {
            File f = new File(override);
            if (f.isFile()) return f;
        }

        File bundled = new File("server/attendance-server.jar");
        if (bundled.isFile()) return bundled;

        File devJar = new File("C:/Attendance_Server/attendance-server/target/attendance-server-1.0.0.jar");
        if (devJar.isFile()) return devJar;

        return null;
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

    // Android 앱 로그인 화면에 입력해줄, 같은 와이파이 안에서 이 PC를 가리키는 주소
    public static String getLanAddressHint() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress() + ":" + PORT;
                    }
                }
            }
        } catch (Exception ignored) {
            // 아래 안내 문구로 대체
        }
        return "이 PC의 IP 주소:" + PORT + " (Wi-Fi 설정에서 IP 확인 필요)";
    }
}
