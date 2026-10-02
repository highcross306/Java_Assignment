package sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// 구글 계정 로그인(OAuth 2.0 "데스크톱 앱" 방식).
//  1) 시스템 브라우저로 구글 로그인 화면을 열고, 이 PC의 임시 주소(127.0.0.1:임의포트)로 인증 코드를 받는다
//  2) PKCE(code_verifier/challenge)로 코드 가로채기를 막는다
//  3) 인증 코드를 토큰으로 바꿔서 refresh_token을 ~/.attendance-desktop/google-token.json에 저장
//
// 요청하는 권한은 drive.appdata(이 앱 전용 숨김 폴더만)와 로그인 계정 표시용 openid/email뿐이다.
// 사용자의 다른 드라이브 파일에는 접근할 수 없다.
//
// OAuth 클라이언트 ID/Secret은 개발자가 구글 클라우드 콘솔에서 "데스크톱 앱" 유형으로 발급해서
// google-client.json에 넣어 배포한다 (데스크톱 앱의 client secret은 비밀로 취급되지 않음 - 구글 문서 기준).
public class GoogleAuth implements DriveClient.TokenProvider {

    public static final String SCOPE = "openid email https://www.googleapis.com/auth/drive.appdata";

    private final File tokenFile;
    // 사용자가 화면에서 입력한 값을 저장하는 곳 (이 PC의 사용자 데이터 폴더)
    private final File userClientConfig;
    private final File[] clientConfigCandidates;
    private final String authEndpoint;
    private final String tokenEndpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // 시험에서는 실제 브라우저 대신 로그인 완료를 흉내내는 동작으로 바꿈
    interface Browser { void open(String url) throws IOException; }
    Browser browser = GoogleAuth::openBrowser;

    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String email = "";
    private String accessToken;
    private long accessExpiresAtMillis;

    // clientConfigCandidates: 위에서부터 먼저 있는 파일을 사용 (앱 폴더, 사용자 데이터 폴더 순)
    public GoogleAuth(File appDataDir, File... clientConfigCandidates) {
        this(appDataDir, "https://accounts.google.com/o/oauth2/v2/auth",
                System.getProperty("attendance.google.tokenEndpoint", "https://oauth2.googleapis.com/token"),
                clientConfigCandidates);
    }

    // 시험용: 엔드포인트를 가짜 서버로 바꿔서 흐름을 검증할 수 있게 함
    GoogleAuth(File appDataDir, String authEndpoint, String tokenEndpoint, File... clientConfigCandidates) {
        this.tokenFile = new File(appDataDir, "google-token.json");
        this.userClientConfig = new File(appDataDir, "google-client.json");
        this.authEndpoint = authEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.clientConfigCandidates = clientConfigCandidates;
        loadClientConfig();
        loadToken();
    }

    // 클라이언트 ID가 있어야 로그인을 시작할 수 있음 (없으면 설정 화면에서 입력받음)
    public boolean isClientConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    // ---------------------------------------------------------------- 클라이언트 설정(google-client.json) 만들기

    // 사용자가 화면에서 입력한 클라이언트 ID/보안 비밀로 google-client.json을 만들어 저장하고 바로 적용한다.
    // (이 두 값은 구글 클라우드 콘솔에서 발급받는 값이라 프로그램이 대신 만들 수는 없고, 입력받아 저장만 한다.)
    // 값이 올바르지 않으면 사용자에게 보여줄 메시지의 IOException
    public void configureClient(String id, String secret) throws IOException {
        String newId = id == null ? "" : id.trim();
        String newSecret = secret == null ? "" : secret.trim();
        String error = validateClientValues(newId, newSecret);
        if (error != null) throw new IOException(error);

        JsonObject json = new JsonObject();
        json.addProperty("client_id", newId);
        json.addProperty("client_secret", newSecret);
        File tmp = new File(userClientConfig.getPath() + ".tmp");
        Files.writeString(tmp.toPath(), json.toString(), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), userClientConfig.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        userClientConfig.setReadable(false, false);
        userClientConfig.setReadable(true, true);

        // 다른 클라이언트로 바꾸면 이전 클라이언트가 발급한 로그인 정보는 쓸 수 없으므로 지움 (다시 로그인 필요)
        boolean changed = clientId != null && !clientId.isEmpty() && !clientId.equals(newId);
        clientId = newId;
        clientSecret = newSecret;
        if (changed && isSignedIn()) signOut();
    }

    // 구글 클라우드 콘솔에서 내려받은 JSON 파일({"installed": {...}})에서 클라이언트 ID/보안 비밀을 읽어옴 -> {id, secret}
    public static String[] readClientFile(File file) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject cfg = root.has("installed") ? root.getAsJsonObject("installed") : root;
            if (!cfg.has("client_id")) {
                if (root.has("web")) {
                    throw new IOException("'웹 애플리케이션' 유형으로 만든 클라이언트입니다. '데스크톱 앱' 유형으로 다시 만들어주세요.");
                }
                throw new IOException("클라이언트 ID가 들어 있지 않은 파일입니다.");
            }
            return new String[] { cfg.get("client_id").getAsString(),
                    cfg.has("client_secret") ? cfg.get("client_secret").getAsString() : "" };
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("구글 클라우드 콘솔에서 내려받은 JSON 파일이 아닙니다.");
        }
    }

    // 입력값 검사: 문제가 있으면 사용자에게 보여줄 메시지, 없으면 null
    public static String validateClientValues(String id, String secret) {
        if (id == null || id.isEmpty()) return "클라이언트 ID를 입력해주세요.";
        if (id.chars().anyMatch(Character::isWhitespace) || !id.endsWith(".apps.googleusercontent.com") || id.length() < 30) {
            return "클라이언트 ID 형식이 올바르지 않습니다. (예: 123456789-abcdef.apps.googleusercontent.com)";
        }
        if (secret == null || secret.isEmpty()) return "클라이언트 보안 비밀(Client secret)을 입력해주세요.";
        if (secret.chars().anyMatch(Character::isWhitespace)) return "클라이언트 보안 비밀에 공백이 들어 있습니다.";
        return null;
    }

    public boolean isSignedIn() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    public String getEmail() {
        return email;
    }

    // ---------------------------------------------------------------- 로그인

    // 브라우저에서 사용자가 로그인/동의할 때까지(최대 3분) 기다림. 실패하면 사용자에게 보여줄 메시지의 IOException
    public void signIn() throws IOException {
        if (!isClientConfigured()) {
            throw new IOException("구글 로그인 설정(google-client.json)이 없습니다. 설치 폴더의 안내 문서를 확인하세요.");
        }

        String verifier = randomUrlSafe(64);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        String state = randomUrlSafe(16);

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String code = param(query, "code");
            String error = param(query, "error");
            String returnedState = param(query, "state");

            String page;
            if (code != null && state.equals(returnedState)) {
                page = "로그인이 완료되었습니다. 이 창을 닫고 프로그램으로 돌아가세요.";
                codeFuture.complete(code);
            } else {
                page = "로그인에 실패했습니다" + (error == null ? "" : " (" + error + ")") + ". 프로그램에서 다시 시도해주세요.";
                codeFuture.completeExceptionally(new IOException(error != null && error.equals("access_denied")
                        ? "구글 로그인이 취소되었습니다" : "구글 로그인에 실패했습니다"));
            }
            byte[] body = ("<html><head><meta charset='utf-8'></head><body style='font-family:sans-serif;padding:40px'>"
                    + page + "</body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
            String url = authEndpoint + "?client_id=" + enc(clientId)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&response_type=code&scope=" + enc(SCOPE)
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256"
                    + "&state=" + state + "&access_type=offline&prompt=consent";
            browser.open(url);

            String code;
            try {
                code = codeFuture.get(3, TimeUnit.MINUTES);
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                throw new IOException("로그인 시간이 초과되었습니다. 다시 시도해주세요.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("로그인이 중단되었습니다");
            }

            JsonObject token = postForm(tokenEndpoint, "code=" + enc(code) + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret == null ? "" : clientSecret)
                    + "&code_verifier=" + enc(verifier) + "&grant_type=authorization_code"
                    + "&redirect_uri=" + enc(redirectUri));

            // 동의 화면에서 드라이브 항목을 체크 해제하면 토큰은 나오지만 권한이 없음
            String granted = token.has("scope") ? token.get("scope").getAsString() : "";
            if (!granted.contains("drive.appdata")) {
                throw new IOException("구글 드라이브 접근 권한이 허용되지 않았습니다. 로그인할 때 드라이브 항목을 체크해주세요.");
            }
            if (!token.has("refresh_token")) {
                throw new IOException("구글에서 로그인 유지 정보를 받지 못했습니다. 구글 계정의 '서드파티 앱 연결'에서 이 앱을 제거한 뒤 다시 시도해주세요.");
            }

            refreshToken = token.get("refresh_token").getAsString();
            email = emailFromIdToken(token);
            accessToken = token.get("access_token").getAsString();
            accessExpiresAtMillis = System.currentTimeMillis() + token.get("expires_in").getAsLong() * 1000;
            saveToken();
        } finally {
            server.stop(0);
        }
    }

    public void signOut() {
        refreshToken = null;
        accessToken = null;
        email = "";
        tokenFile.delete();
    }

    // ---------------------------------------------------------------- 액세스 토큰

    @Override
    public synchronized String accessToken() throws IOException {
        if (!isSignedIn()) throw new IOException("구글 계정에 로그인되어 있지 않습니다");
        if (accessToken == null || System.currentTimeMillis() > accessExpiresAtMillis - 60_000) {
            refresh();
        }
        return accessToken;
    }

    @Override
    public synchronized void invalidate() {
        accessToken = null;
    }

    private void refresh() throws IOException {
        JsonObject token;
        try {
            token = postForm(tokenEndpoint, "client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret == null ? "" : clientSecret)
                    + "&refresh_token=" + enc(refreshToken) + "&grant_type=refresh_token");
        } catch (InvalidGrantException e) {
            // 사용자가 구글 계정에서 앱 연결을 끊었거나 토큰이 만료됨 -> 다시 로그인해야 함
            signOut();
            throw new IOException("구글 로그인이 만료되었습니다. 동기화 설정에서 다시 로그인해주세요.");
        }
        accessToken = token.get("access_token").getAsString();
        accessExpiresAtMillis = System.currentTimeMillis() + token.get("expires_in").getAsLong() * 1000;
    }

    // ---------------------------------------------------------------- 내부

    private static class InvalidGrantException extends IOException {
        InvalidGrantException(String m) { super(m); }
    }

    private JsonObject postForm(String url, String form) throws IOException {
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            JsonObject body;
            try {
                body = JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new IOException("구글 응답을 해석하지 못했습니다 (코드 " + res.statusCode() + ")");
            }
            if (res.statusCode() / 100 != 2) {
                String error = body.has("error") ? body.get("error").getAsString() : "";
                if (error.equals("invalid_grant")) throw new InvalidGrantException(error);
                throw new IOException("구글 인증 서버 오류: " + error + " (코드 " + res.statusCode() + ")");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("요청이 중단되었습니다");
        }
    }

    private static String emailFromIdToken(JsonObject token) {
        try {
            String payload = token.get("id_token").getAsString().split("\\.")[1];
            JsonObject claims = JsonParser.parseString(
                    new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)).getAsJsonObject();
            return claims.has("email") ? claims.get("email").getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void loadClientConfig() {
        for (File file : clientConfigCandidates) {
            if (file == null || !file.isFile()) continue;
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                // 구글 콘솔에서 내려받은 JSON({"installed": {...}})과 단순 형식 모두 허용
                JsonObject cfg = root.has("installed") ? root.getAsJsonObject("installed") : root;
                clientId = cfg.get("client_id").getAsString();
                clientSecret = cfg.has("client_secret") ? cfg.get("client_secret").getAsString() : "";
                return;
            } catch (Exception e) {
                System.err.println("구글 클라이언트 설정을 읽지 못했습니다 (" + file + "): " + e.getMessage());
            }
        }
    }

    private void loadToken() {
        try {
            if (tokenFile.isFile()) {
                JsonObject t = JsonParser.parseString(Files.readString(tokenFile.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                refreshToken = t.get("refresh_token").getAsString();
                email = t.has("email") ? t.get("email").getAsString() : "";
            }
        } catch (Exception e) {
            System.err.println("구글 토큰 파일을 읽지 못했습니다: " + e.getMessage());
        }
    }

    private void saveToken() throws IOException {
        JsonObject t = new JsonObject();
        t.addProperty("refresh_token", refreshToken);
        t.addProperty("email", email);
        Files.writeString(tokenFile.toPath(), t.toString(), StandardCharsets.UTF_8);
        tokenFile.setReadable(false, false);
        tokenFile.setReadable(true, true);
    }

    private static void openBrowser(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
        } else {
            throw new IOException("브라우저를 열 수 없습니다");
        }
    }

    private static String param(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
