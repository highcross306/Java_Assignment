package migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Properties;

// 지금 버전의 출석 관리 프로그램이 띄워 둔 내장 서버(REST API)에 데이터를 넣는 클래스.
// DB 파일(H2)에 직접 쓰지 않고 앱과 똑같은 API를 쓰므로, 계정(user_id)·번호 매기기·데모 제한 같은 규칙이 그대로 지켜진다.
public class NewServer {

    public static class ApiException extends IOException {
        public final int status;
        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private String token;

    public NewServer(String baseUrl) {
        String u = baseUrl.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        this.baseUrl = u;
    }

    // 데스크톱 앱과 같은 규칙: ~/.attendance-desktop/settings.properties의 server.port, 없으면 18080
    public static String defaultBaseUrl() {
        int port = 18080;
        File file = new File(System.getProperty("user.home"), ".attendance-desktop" + File.separator + "settings.properties");
        if (file.isFile()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                p.load(in);
                port = Integer.parseInt(p.getProperty("server.port", "18080").trim());
            } catch (IOException | NumberFormatException ignored) {
                // 읽을 수 없으면 기본 포트
            }
        }
        return "http://localhost:" + port;
    }

    public boolean isUp() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return false;
        }
    }

    public void login(String username, String password, boolean createIfMissing) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        try {
            token = send("POST", "/api/auth/login", body, false).getAsJsonObject().get("token").getAsString();
        } catch (ApiException e) {
            // 400은 비밀번호 8자 미만 같은 입력 검사 실패 (그런 계정은 있을 수 없으므로 로그인 실패와 같음)
            boolean badLogin = e.status == 401 || e.status == 400;
            if (e.status == 429) throw new ApiException(429, "로그인을 너무 많이 실패해서 잠시 막혔습니다. 몇 분 뒤 다시 해 주세요.");
            if (!badLogin || !createIfMissing) {
                throw badLogin ? new ApiException(401, "지금 프로그램의 아이디 또는 비밀번호가 올바르지 않습니다.") : e;
            }
            try {
                token = send("POST", "/api/auth/signup", body, false).getAsJsonObject().get("token").getAsString();
            } catch (ApiException e2) {
                if (e2.status == 409) throw new ApiException(409, "이미 있는 아이디인데 비밀번호가 틀렸습니다.");
                if (e2.status == 400) throw new ApiException(400, "새 계정의 비밀번호는 8자 이상이어야 합니다.");
                throw e2;
            }
        }
    }

    public JsonArray getClasses() throws IOException {
        return send("GET", "/api/classes", null, true).getAsJsonArray();
    }

    public JsonArray getStudents() throws IOException {
        return send("GET", "/api/students", null, true).getAsJsonArray();
    }

    public JsonArray getAttendance() throws IOException {
        return send("GET", "/api/attendance", null, true).getAsJsonArray();
    }

    public int createClass(String name) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("className", name);
        return send("POST", "/api/classes", body, true).getAsJsonObject().get("classId").getAsInt();
    }

    public int createStudent(String name, String school, int classId, String studentPhone, String guardianPhone) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("school", school);
        body.addProperty("classId", classId);
        body.addProperty("studentPhone", studentPhone);
        body.addProperty("guardianPhone", guardianPhone);
        return send("POST", "/api/students", body, true).getAsJsonObject().get("studentId").getAsInt();
    }

    public void saveAttendance(int studentId, int classId, LocalDate date, String status, String reason) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("studentId", studentId);
        body.addProperty("classId", classId);
        body.addProperty("date", date.toString());
        body.addProperty("status", status);
        body.addProperty("reason", reason);
        send("POST", "/api/attendance", body, true);
    }

    private JsonElement send(String method, String path, JsonObject body, boolean auth) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
        if (auth) b.header("Authorization", "Bearer " + token);
        if (body != null) {
            b.header("Content-Type", "application/json; charset=UTF-8");
            b.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> r;
        try {
            r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단됨", e);
        }
        if (r.statusCode() / 100 != 2) throw new ApiException(r.statusCode(), errorMessage(r));
        String text = r.body();
        return text == null || text.isBlank() ? new JsonObject() : JsonParser.parseString(text);
    }

    private static String errorMessage(HttpResponse<String> r) {
        String detail = r.body();
        try {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("message") && !o.get("message").getAsString().isBlank()) detail = o.get("message").getAsString();
            else if (o.has("error")) detail = o.get("error").getAsString();
        } catch (RuntimeException ignored) {
            // JSON이 아니면 본문 그대로
        }
        if (detail != null && detail.length() > 200) detail = detail.substring(0, 200);
        return "서버 응답 " + r.statusCode() + (detail == null || detail.isBlank() ? "" : ": " + detail);
    }
}
