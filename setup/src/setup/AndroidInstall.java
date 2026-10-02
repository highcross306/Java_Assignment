package setup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sun.net.httpserver.HttpServer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Android 앱(APK) 설치. 두 가지 방법:
//  1) USB로 연결된 폰이 있고 adb(Android SDK platform-tools)가 이 PC에 있으면 자동으로 설치 (USB 디버깅이 켜져 있어야 함)
//  2) 그렇지 않으면 이 PC가 같은 Wi-Fi에 APK를 잠시 제공하고, 폰이 QR 코드(또는 주소)로 내려받아 설치
//     (설치 프로그램이 열려 있는 동안만 제공. 포트는 앱 서버와 같은 포트를 써서 방화벽 규칙이 그대로 적용됨)
final class AndroidInstall {

    private AndroidInstall() {}

    // ---------------------------------------------------------------- adb

    static String findAdb(Path... extraDirs) {
        List<String> candidates = new ArrayList<>();
        String env = System.getenv("ADB");
        if (env != null) candidates.add(env);
        for (String var : new String[] { "ANDROID_SDK_ROOT", "ANDROID_HOME" }) {
            String v = System.getenv(var);
            if (v != null) candidates.add(v + File.separator + "platform-tools" + File.separator + "adb.exe");
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) candidates.add(local + File.separator + "Android" + File.separator + "Sdk" + File.separator + "platform-tools" + File.separator + "adb.exe");
        for (Path d : extraDirs) candidates.add(d.resolve("platform-tools").resolve("adb.exe").toString());
        for (String c : candidates) {
            if (new File(c).isFile()) return c;
        }
        // PATH에서 찾기
        try {
            Process p = new ProcessBuilder("where", "adb").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && !out.isEmpty()) {
                String first = out.lines().findFirst().orElse("");
                if (new File(first).isFile()) return first;
            }
        } catch (Exception ignored) {
            // 못 찾으면 null
        }
        return null;
    }

    // 연결되어 있고 USB 디버깅이 허용된 기기의 일련번호 목록
    static List<String> authorizedDevices(String adb) throws IOException {
        String out = run(List.of(adb, "devices"), 25);
        List<String> serials = new ArrayList<>();
        for (String line : out.split("\\R")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length == 2 && cols[1].equals("device")) serials.add(cols[0]);
        }
        return serials;
    }

    // 설치에 성공하면 null, 실패하면 이유
    static String install(String adb, String serial, Path apk) throws IOException {
        String out = run(List.of(adb, "-s", serial, "install", "-r", apk.toString()), 180);
        if (out.contains("Success")) return null;
        return out.lines().filter(l -> !l.isBlank()).reduce((a, b) -> b).orElse("알 수 없는 오류");
    }

    private static String run(List<String> cmd, int timeoutSeconds) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("시간 안에 끝나지 않았습니다: " + String.join(" ", cmd));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단되었습니다");
        }
        return out;
    }

    // ---------------------------------------------------------------- APK 제공(HTTP) + QR

    // APK를 서비스하는 임시 웹 서버. close()로 끝냄
    static final class ApkServer implements AutoCloseable {
        private final HttpServer server;
        final int port;

        ApkServer(Path apk, int port) throws IOException {
            this.port = port;
            byte[] bytes = Files.readAllBytes(apk);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/AttendanceApp.apk", ex -> {
                ex.getResponseHeaders().add("Content-Type", "application/vnd.android.package-archive");
                ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"AttendanceApp.apk\"");
                ex.sendResponseHeaders(200, bytes.length);
                try (var out = ex.getResponseBody()) {
                    out.write(bytes);
                }
            });
            // 주소만 입력해서 들어와도 내려받기 링크가 보이도록
            server.createContext("/", ex -> {
                byte[] page = ("<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                        + "<body style='font-family:sans-serif;padding:32px;text-align:center'><h2>출석 관리 앱</h2>"
                        + "<p><a href='/AttendanceApp.apk' style='font-size:22px'>앱 내려받기 (APK)</a></p>"
                        + "<p>내려받은 파일을 열어 설치하세요.<br>'출처를 알 수 없는 앱 설치'를 허용해야 할 수 있습니다.</p></body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, page.length);
                try (var out = ex.getResponseBody()) {
                    out.write(page);
                }
            });
            server.start();
        }

        String url(String host) {
            return "http://" + host + ":" + port + "/AttendanceApp.apk";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    static BufferedImage qr(String text, int size) throws IOException {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return img;
        } catch (com.google.zxing.WriterException e) {
            throw new IOException("QR 코드를 만들지 못했습니다: " + e.getMessage());
        }
    }
}
