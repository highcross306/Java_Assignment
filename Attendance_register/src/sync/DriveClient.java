package sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 구글 드라이브 REST API(v3)로 "앱 전용 숨김 폴더(appDataFolder)"의 파일 하나(attendance-data.enc)를
// 읽고 쓰는 최소 클라이언트. 사용자의 일반 드라이브 파일은 건드리지도, 보지도 못한다.
// 버전 번호와 올린 기기는 파일의 appProperties에 기록해서 PC 간 비교에 사용한다.
public class DriveClient implements RemoteStore {

    public interface TokenProvider {
        String accessToken() throws IOException;
        // 서버가 401을 돌려주면 캐시된 토큰을 버리고 새로 받도록 알림
        void invalidate();
    }

    private static final String FILE_NAME = "attendance-data.enc";
    private static final int MAX_ATTEMPTS = 3;

    private final TokenProvider tokens;
    private final String apiBase;
    private final String uploadBase;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String fileId;

    public DriveClient(TokenProvider tokens) {
        // attendance.google.apiBase: 개발/시험용으로만 쓰는 주소 재지정 (기본은 실제 구글)
        this(tokens, System.getProperty("attendance.google.apiBase", "https://www.googleapis.com"));
    }

    // 시험용: 가짜 드라이브 서버 주소를 넣을 수 있음
    DriveClient(TokenProvider tokens, String base) {
        this.tokens = tokens;
        this.apiBase = base + "/drive/v3/files";
        this.uploadBase = base + "/upload/drive/v3/files";
    }

    @Override
    public Meta head() throws IOException {
        String query = "name='" + FILE_NAME + "' and trashed=false";
        String url = apiBase + "?spaces=appDataFolder&orderBy=modifiedTime%20desc&pageSize=1"
                + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&fields=" + URLEncoder.encode("files(id,modifiedTime,appProperties)", StandardCharsets.UTF_8);
        JsonObject body = JsonParser.parseString(new String(send(builder(url).GET(), "목록 조회"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray files = body.getAsJsonArray("files");
        if (files == null || files.isEmpty()) {
            fileId = null;
            return null;
        }
        JsonObject f = files.get(0).getAsJsonObject();
        fileId = f.get("id").getAsString();
        JsonObject props = f.has("appProperties") ? f.getAsJsonObject("appProperties") : new JsonObject();
        long version = props.has("version") ? Long.parseLong(props.get("version").getAsString()) : 0;
        String device = props.has("device") ? props.get("device").getAsString() : "";
        String modified = f.has("modifiedTime") ? f.get("modifiedTime").getAsString() : "";
        return new Meta(version, device, modified);
    }

    @Override
    public byte[] download() throws IOException {
        if (fileId == null && head() == null) throw new IOException("드라이브에 동기화 데이터가 없습니다");
        return send(builder(apiBase + "/" + fileId + "?alt=media").GET(), "다운로드");
    }

    @Override
    public void upload(byte[] data, long version, String deviceId) throws IOException {
        if (fileId == null) head(); // 이미 있는 파일이면 새로 만들지 않고 갱신 (중복 파일 방지)

        JsonObject metadata = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("version", String.valueOf(version));
        props.addProperty("device", deviceId);
        metadata.add("appProperties", props);
        if (fileId == null) {
            metadata.addProperty("name", FILE_NAME);
            JsonArray parents = new JsonArray();
            parents.add("appDataFolder");
            metadata.add("parents", parents);
        }

        String boundary = "attsync" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                + metadata + "\r\n--" + boundary + "\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8));

        String url = (fileId == null ? uploadBase : uploadBase + "/" + fileId) + "?uploadType=multipart&fields=id";
        HttpRequest.Builder req = builder(url)
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .method(fileId == null ? "POST" : "PATCH", HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        byte[] response = send(req, "업로드");
        if (fileId == null) {
            fileId = JsonParser.parseString(new String(response, StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();
        }
    }

    // ---------------------------------------------------------------- 내부

    private HttpRequest.Builder builder(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
    }

    // 401이면 토큰을 새로 받아 한 번 재시도하고, 429/5xx는 잠깐 쉬었다가 재시도 (드라이브 API 권장 방식)
    private byte[] send(HttpRequest.Builder request, String what) throws IOException {
        boolean refreshedAuth = false;
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<byte[]> res = http.send(
                        request.setHeader("Authorization", "Bearer " + tokens.accessToken()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                int code = res.statusCode();
                if (code / 100 == 2) return res.body();

                if (code == 401 && !refreshedAuth) {
                    refreshedAuth = true;
                    tokens.invalidate();
                    continue;
                }
                if ((code == 429 || code >= 500) && attempt < MAX_ATTEMPTS) {
                    Thread.sleep(1000L << attempt);
                    continue;
                }
                if (code == 403 && new String(res.body(), StandardCharsets.UTF_8).contains("storageQuotaExceeded")) {
                    throw new IOException("구글 드라이브 저장 공간이 부족합니다");
                }
                throw new IOException("드라이브 " + what + " 실패 (코드 " + code + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("요청이 중단되었습니다");
            }
        }
    }
}
