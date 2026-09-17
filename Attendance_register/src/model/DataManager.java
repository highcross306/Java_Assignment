package model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.prefs.Preferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class DataManager {

    // ====== 서버 주소 ======
    // 학원마다 각자 자기 서버를 운영하므로 주소를 코드에 고정하지 않고, 로그인 화면에서
    // 입력받아 이 PC의 사용자 설정(레지스트리/Preferences)에 저장해두고 재사용함
    private static final Preferences prefs = Preferences.userNodeForPackage(DataManager.class);
    private static String serverUrl = prefs.get("server_url", null);

    public static boolean isServerConfigured() { return serverUrl != null; }
    public static String getServerUrl() { return serverUrl; }

    // 사용자가 "myacademy.onrender.com" 처럼 호스트 주소만 입력해도
    // 뒤에 서버의 공통 API 경로(/api)를 자동으로 붙여서 저장함
    public static void setServerUrl(String rawInput) {
        String trimmed = rawInput.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String withScheme = (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                ? trimmed
                : "https://" + trimmed;
        serverUrl = withScheme + "/api";
        prefs.put("server_url", serverUrl);
    }

    // ====== 아이디/비밀번호 기억하기 ======
    // "내 정보 기억" 체크 시에만 저장함. 자동으로 로그인까지 하지는 않고
    // 화면에 미리 채워주기만 해서, 로그인 버튼은 항상 사용자가 직접 눌러야 함.
    // 비밀번호는 이 PC에서만 쓰는 키로 암호화해서 저장하므로(아래 encrypt/decrypt),
    // 레지스트리를 직접 열어봐도 평문 그대로 보이지 않음. 다만 그 키도 같은 PC의
    // 사용자 설정에 함께 보관되므로, 이 PC 자체에 대한 접근 권한이 있는 사람에게는
    // 여전히 복호화가 가능한 수준의 보호임 — 여러 사람이 함께 쓰는 PC에서는 체크하지
    // 않는 것을 권장함.
    public static boolean isRememberMeEnabled() {
        return prefs.getBoolean("remember_me", false);
    }

    public static String getSavedUsername() {
        return prefs.get("saved_username", null);
    }

    public static String getSavedPassword() {
        String encrypted = prefs.get("saved_password", null);
        return encrypted == null ? null : decrypt(encrypted);
    }

    public static void saveRememberedCredentials(boolean remember, String username, String password) {
        prefs.putBoolean("remember_me", remember);
        if (remember) {
            prefs.put("saved_username", username);
            prefs.put("saved_password", encrypt(password));
        } else {
            prefs.remove("saved_username");
            prefs.remove("saved_password");
        }
    }

    // ---- 저장된 비밀번호를 평문으로 남기지 않기 위한 최소한의 암복호화 ----
    private static final SecureRandom secureRandom = new SecureRandom();

    private static SecretKey getOrCreateLocalKey() {
        String base64Key = prefs.get("cred_key", null);
        if (base64Key == null) {
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(128);
                base64Key = Base64.getEncoder().encodeToString(keyGen.generateKey().getEncoded());
                prefs.put("cred_key", base64Key);
            } catch (Exception e) {
                throw new RuntimeException("암호화 키 생성 실패", e);
            }
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    private static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateLocalKey(), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 암호화 실패", e);
        }
    }

    private static String decrypt(String base64Combined) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Combined);
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateLocalKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 키가 바뀌었거나 값이 손상된 경우 등: 그냥 저장된 적 없는 것처럼 처리
            return null;
        }
    }

    // HttpClient는 한 번만 만들어서 계속 재사용하는 게 정석 (매번 새로 만들면 비효율적)
    private static final HttpClient client = HttpClient.newHttpClient();

    private static ArrayList<Student> students = new ArrayList<>();
    private static ArrayList<Class_Info> classes = new ArrayList<>();
    private static ArrayList<AttendanceRecord> records = new ArrayList<>();

    // HashMap으로 빠른 검색 (기존과 동일한 역할)
    private static HashMap<Integer, Student> studentMap = new HashMap<>();
    private static HashMap<Integer, Class_Info> classMap = new HashMap<>();

    // 서버에 "이미 존재한다고 확인된" id들을 기억해둠.
    // 새로 추가된 항목(아직 서버에 없는 것)인지, 이미 있는 항목(수정해야 하는 것)인지
    // 구분하는 용도로 씀.
    private static Set<Integer> knownClassIds = new HashSet<>();
    private static Set<Integer> knownStudentIds = new HashSet<>();

    // =====================================================
    // 로그인 관련
    // 서버가 회원가입/로그인 시스템을 갖추면서, 이제 모든 API 호출에는
    // 로그인 시 발급받은 토큰을 Authorization 헤더로 실어 보내야 함
    // =====================================================
    private static String authToken = null;
    private static String currentUsername = null;

    public static boolean isLoggedIn() { return authToken != null; }
    public static String getCurrentUsername() { return currentUsername; }

    // =====================================================
    // 구독(결제) 상태 - 데모 상태면 반/학생 수, 캘린더 이동 범위가 제한됨
    // (실제 제한 판단은 서버가 하고, 여기서는 그 결과를 화면에 반영하기 위해 캐싱만 함)
    // =====================================================
    private static String planStatus = "ACTIVE";
    private static Integer maxClasses = null;
    private static Integer maxStudents = null;
    private static java.time.YearMonth allowedMonth = null;

    public static boolean isDemo() { return "DEMO".equals(planStatus); }
    public static Integer getMaxClasses() { return maxClasses; }
    public static Integer getMaxStudents() { return maxStudents; }
    public static java.time.YearMonth getAllowedMonth() { return allowedMonth; }

    private static void loadPlan() {
        planStatus = "ACTIVE";
        maxClasses = null;
        maxStudents = null;
        allowedMonth = null;
        try {
            String body = httpGet("/me/plan");
            applyPlanJson(JsonParser.parseString(body).getAsJsonObject());
        } catch (Exception e) {
            System.err.println("구독 상태 조회 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    private static void applyPlanJson(JsonObject obj) {
        planStatus = obj.get("status").getAsString();
        maxClasses = (obj.has("maxClasses") && !obj.get("maxClasses").isJsonNull())
                ? obj.get("maxClasses").getAsInt() : null;
        maxStudents = (obj.has("maxStudents") && !obj.get("maxStudents").isJsonNull())
                ? obj.get("maxStudents").getAsInt() : null;
        allowedMonth = (obj.has("allowedMonth") && !obj.get("allowedMonth").isJsonNull())
                ? java.time.YearMonth.parse(obj.get("allowedMonth").getAsString()) : null;
    }

    // 테스트용: 비밀번호(1234)를 맞게 입력하면 서버에서 DEMO <-> ACTIVE를 전환해줌.
    // 실제 결제(PG) 연동 전까지 "결제 완료"를 사람이 직접 흉내내기 위한 임시 버튼용 메소드.
    // 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지를 반환
    public static String togglePlanMock(String password) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("password", password);
            String body = httpPost("/me/plan/mock-pay", json.toString());
            applyPlanJson(JsonParser.parseString(body).getAsJsonObject());
            return null;
        } catch (Exception e) {
            return friendlyMessage(e);
        }
    }

    // 회원가입: 성공하면 바로 로그인된 상태가 됨 (서버가 가입과 동시에 토큰을 내려줌)
    public static String signup(String username, String password) {
        return authenticate("/auth/signup", username, password);
    }

    public static String login(String username, String password) {
        return authenticate("/auth/login", username, password);
    }

    // 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지를 반환
    private static String authenticate(String path, String username, String password) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("username", username);
            json.addProperty("password", password);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (res.statusCode() == 200 || res.statusCode() == 201) {
                JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
                authToken = body.get("token").getAsString();
                currentUsername = body.get("username").getAsString();
                return null;
            }

            return errorMessageFrom(res);
        } catch (Exception e) {
            return "서버에 연결할 수 없습니다: " + e.getMessage();
        }
    }

    // org.json의 optString(key, default)과 동일하게 동작: 키가 없거나 null이면 기본값 반환
    private static String optString(JsonObject obj, String key, String def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : def;
    }

    // org.json의 JSONObject.put(key, value)와 동일하게 동작: value가 null이면 키 자체를 넣지 않음
    private static void putNullable(JsonObject json, String key, String value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }

    // 로그아웃: 토큰과 함께, 화면에 남아있던 이전 사용자의 데이터도 전부 비움
    // (비우지 않으면 다음 사용자가 로그인했을 때 로딩 전까지 이전 사용자 데이터가 잠깐 보일 수 있음)
    public static void logout() {
        authToken = null;
        currentUsername = null;
        students.clear();
        classes.clear();
        records.clear();
        studentMap.clear();
        classMap.clear();
        knownClassIds.clear();
        knownStudentIds.clear();
        planStatus = "ACTIVE";
        maxClasses = null;
        maxStudents = null;
        allowedMonth = null;
    }

    // =====================================================
    // 프로그램 시작 시 호출, 서버에서 전체 데이터 불러오기
    // =====================================================
    public static void loadAll() {
        loadPlan();
        loadClasses();
        loadStudents();
        loadAttendance();
    }

    // 저장 버튼 등에서 호출, 로컬에 있는 변경사항을 서버로 올림
    // 반환값: 실패한 항목이 있으면 화면에 그대로 보여줄 에러 메시지 목록 (비어있으면 전부 성공)
    public static List<String> saveAll() {
        List<String> errors = new ArrayList<>();
        errors.addAll(saveClasses());
        errors.addAll(saveStudents());
        // 출석 기록(records)은 addRecord/updateRecord 호출 시점에
        // 이미 곧바로 서버에 반영되기 때문에 여기서 따로 보낼 게 없음
        return errors;
    }

    // =====================================================
    // 공통 HTTP 헬퍼 메서드
    // (GET/POST/PUT/DELETE를 매번 똑같이 반복 작성하지 않도록 정리한 것)
    // =====================================================
    // 로그인 토큰이 있으면 Authorization 헤더를 자동으로 붙여주는 공통 요청 빌더
    private static HttpRequest.Builder authorizedBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(serverUrl + path));
        if (authToken != null) {
            builder.header("Authorization", "Bearer " + authToken);
        }
        return builder;
    }

    // 서버가 4xx/5xx로 응답했을 때, 그 이유를 화면에 그대로 보여줄 수 있도록 던지는 예외
    // (기존에는 각 메서드가 이 상황을 조용히 System.err로만 남기고 넘어가서, 예를 들어
    // 데모 상태 제한에 걸려 저장이 거부돼도 사용자는 아무 반응도 못 보는 문제가 있었음)
    public static class ApiException extends RuntimeException {
        public ApiException(String message) { super(message); }
    }

    // 서버 응답 바디의 "message" 필드를 꺼내거나, 없으면 상태 코드로 기본 메시지를 만듦
    private static String errorMessageFrom(HttpResponse<String> res) {
        try {
            JsonObject err = JsonParser.parseString(res.body()).getAsJsonObject();
            return optString(err, "message", "요청이 실패했습니다 (코드 " + res.statusCode() + ")");
        } catch (Exception parseError) {
            return "요청이 실패했습니다 (코드 " + res.statusCode() + ")";
        }
    }

    // ApiException(서버가 이유를 알려준 경우)이면 그 메시지 그대로, 아니면(네트워크 오류 등) 안내 문구로 변환
    private static String friendlyMessage(Exception e) {
        return (e instanceof ApiException) ? e.getMessage() : "서버에 연결할 수 없습니다: " + e.getMessage();
    }

    private static String httpGet(String path) throws IOException, InterruptedException {
        HttpRequest req = authorizedBuilder(path).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) throw new ApiException(errorMessageFrom(res));
        return res.body();
    }

    private static String httpPost(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = authorizedBuilder(path)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) throw new ApiException(errorMessageFrom(res));
        return res.body();
    }

    private static void httpPut(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = authorizedBuilder(path)
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) throw new ApiException(errorMessageFrom(res));
    }

    private static void httpDelete(String path) throws IOException, InterruptedException {
        HttpRequest req = authorizedBuilder(path).DELETE().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 400) throw new ApiException(errorMessageFrom(res));
    }

    // =====================================================
    // 반(Class) 불러오기/저장하기
    // =====================================================
    private static void loadClasses() {
        classes.clear();
        classMap.clear();
        knownClassIds.clear();
        try {
            String body = httpGet("/classes");
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                Class_Info cls = new Class_Info(obj.get("classId").getAsInt(), obj.get("className").getAsString());
                classes.add(cls);
                classMap.put(cls.getClass_Id(), cls);
                knownClassIds.add(cls.getClass_Id());
            }
        } catch (Exception e) {
            System.err.println("반 데이터 불러오기 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    private static List<String> saveClasses() {
        List<String> errors = new ArrayList<>();
        List<Class_Info> rejected = new ArrayList<>(); // 서버에 한 번도 저장된 적 없다가 거절된 항목 (무한 재시도 방지용)

        // 1) 로컬에 있는 항목들을 서버로 반영 (새 항목은 등록, 기존 항목은 수정)
        for (Class_Info cls : classes) {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("className", cls.getClass_Name());

                if (knownClassIds.contains(cls.getClass_Id())) {
                    // 이미 서버에 있는 반 -> 수정(PUT)
                    httpPut("/classes/" + cls.getClass_Id(), json.toString());
                } else {
                    // 서버에 아직 없는 반 -> 등록(POST)
                    String resBody = httpPost("/classes", json.toString());
                    JsonObject created = JsonParser.parseString(resBody).getAsJsonObject();
                    int serverId = created.get("classId").getAsInt();

                    // 서버가 실제로 발급한 id로, 로컬 객체의 id를 맞춰줌
                    // (로컬에서 임시로 매겼던 id와 서버 id가 다를 수 있기 때문)
                    classMap.remove(cls.getClass_Id());
                    cls.setClass_Id(serverId);
                    classMap.put(serverId, cls);
                    knownClassIds.add(serverId);
                }
            } catch (Exception e) {
                errors.add("반 '" + cls.getClass_Name() + "' 저장 실패: " + friendlyMessage(e));
                if (!knownClassIds.contains(cls.getClass_Id())) {
                    rejected.add(cls); // 서버가 거절한 신규 항목은 로컬에도 남겨두지 않음
                }
            }
        }
        classes.removeAll(rejected);
        for (Class_Info cls : rejected) classMap.remove(cls.getClass_Id());

        // 2) 로컬에서는 삭제됐는데 서버에는 아직 남아있는 항목 -> 서버에서도 삭제
        Set<Integer> currentIds = new HashSet<>();
        for (Class_Info cls : classes) currentIds.add(cls.getClass_Id());

        Set<Integer> toDelete = new HashSet<>(knownClassIds);
        toDelete.removeAll(currentIds);
        for (Integer deletedId : toDelete) {
            try {
                httpDelete("/classes/" + deletedId);
                knownClassIds.remove(deletedId);
            } catch (Exception e) {
                errors.add("반 삭제 실패(ID " + deletedId + "): " + friendlyMessage(e));
            }
        }
        return errors;
    }

    // =====================================================
    // 학생 불러오기/저장하기
    // =====================================================
    private static void loadStudents() {
        students.clear();
        studentMap.clear();
        knownStudentIds.clear();
        try {
            String body = httpGet("/students");
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                Student s = new Student(
                        obj.get("name").getAsString(),
                        optString(obj, "school", ""),
                        obj.get("classId").getAsInt()
                );
                s.setStudent_Id(obj.get("studentId").getAsInt());
                s.setStudent_Phone(optString(obj, "studentPhone", ""));
                s.setGuardian_Phone(optString(obj, "guardianPhone", ""));
                students.add(s);
                studentMap.put(s.getStudent_id(), s);
                knownStudentIds.add(s.getStudent_id());
            }
        } catch (Exception e) {
            System.err.println("학생 데이터 불러오기 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    private static List<String> saveStudents() {
        List<String> errors = new ArrayList<>();
        List<Student> rejected = new ArrayList<>(); // 서버에 한 번도 저장된 적 없다가 거절된 항목 (무한 재시도 방지용)

        for (Student s : students) {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("name", s.getName());
                putNullable(json, "school", s.getSchool());
                json.addProperty("classId", s.getClass_Id());
                putNullable(json, "studentPhone", s.getStudent_Phone());
                putNullable(json, "guardianPhone", s.getGuardian_Phone());

                if (knownStudentIds.contains(s.getStudent_id())) {
                    httpPut("/students/" + s.getStudent_id(), json.toString());
                } else {
                    String resBody = httpPost("/students", json.toString());
                    JsonObject created = JsonParser.parseString(resBody).getAsJsonObject();
                    int serverId = created.get("studentId").getAsInt();

                    studentMap.remove(s.getStudent_id());
                    s.setStudent_Id(serverId);
                    studentMap.put(serverId, s);
                    knownStudentIds.add(serverId);
                }
            } catch (Exception e) {
                errors.add("학생 '" + s.getName() + "' 저장 실패: " + friendlyMessage(e));
                if (!knownStudentIds.contains(s.getStudent_id())) {
                    rejected.add(s);
                }
            }
        }
        students.removeAll(rejected);
        for (Student s : rejected) studentMap.remove(s.getStudent_id());

        Set<Integer> currentIds = new HashSet<>();
        for (Student s : students) currentIds.add(s.getStudent_id());

        Set<Integer> toDelete = new HashSet<>(knownStudentIds);
        toDelete.removeAll(currentIds);
        for (Integer deletedId : toDelete) {
            try {
                httpDelete("/students/" + deletedId);
                knownStudentIds.remove(deletedId);
            } catch (Exception e) {
                errors.add("학생 삭제 실패(ID " + deletedId + "): " + friendlyMessage(e));
            }
        }
        return errors;
    }

    // =====================================================
    // 출석 기록 불러오기
    // (저장은 addRecord/updateRecord에서 즉시 처리하므로 별도 saveAttendance는 없음)
    // =====================================================
    private static void loadAttendance() {
        records.clear();
        try {
            String body = httpGet("/attendance");
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                records.add(new AttendanceRecord(
                        obj.get("studentId").getAsInt(),
                        obj.get("classId").getAsInt(),
                        LocalDate.parse(obj.get("date").getAsString()),
                        AttendanceStatus.valueOf(obj.get("status").getAsString()),
                        optString(obj, "reason", "")
                ));
            }
        } catch (Exception e) {
            System.err.println("출석 데이터 불러오기 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    // =====================================================
    // get 메소드 (기존과 동일)
    // =====================================================
    public static ArrayList<Student> getStudent() { return students; }
    public static ArrayList<Class_Info> getClasses() { return classes; }
    public static ArrayList<AttendanceRecord> getRecords() { return records; }

    public static ArrayList<AttendanceRecord> getTodayRecords() {
        LocalDate today = LocalDate.now();
        ArrayList<AttendanceRecord> result = new ArrayList<>();
        for (AttendanceRecord r : records) {
            if (r.getDate().equals(today)) {
                result.add(r);
            }
        }
        return result;
    }

    public static int getNextStudentId() {
        int max = 0;
        for (Student s : students) {
            if (s.getStudent_id() > max) max = s.getStudent_id();
        }
        return max + 1;
    }

    public static int countTodayByStatus(AttendanceStatus status) {
        int count = 0;
        for (AttendanceRecord r : getTodayRecords()) {
            if (r.getStatus() == status) count++;
        }
        return count;
    }

    public static Student findStudentById(int id) {
        return studentMap.get(id);
    }

    public static Class_Info findClassById(int id) {
        return classMap.get(id);
    }

    // =====================================================
    // 출석 기록 추가/수정 -> 호출 즉시 서버로 전송
    // 서버의 AttendanceController가 "있으면 수정, 없으면 등록"을 알아서 처리해주기 때문에
    // 여기서는 항상 같은 방식(POST)으로 보내면 됨
    // =====================================================
    // 반환값: 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지 (실패 시 로컬에 추가했던 기록도 되돌림)
    public static String addRecord(AttendanceRecord record) {
        records.add(record);
        String error = sendAttendanceToServer(record);
        if (error != null) {
            records.remove(record);
        }
        return error;
    }

    // 반환값: 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지 (실패 시 변경 전 상태로 되돌림)
    public static String updateRecord(int student_id, LocalDate date, AttendanceStatus status, String reason) {
        for (AttendanceRecord r : records) {
            if (r.getStudent_Id() == student_id && r.getDate().equals(date)) {
                AttendanceStatus oldStatus = r.getStatus();
                String oldReason = r.getReason();
                r.setStatus(status);
                r.setReason(reason);
                String error = sendAttendanceToServer(r);
                if (error != null) {
                    r.setStatus(oldStatus);
                    r.setReason(oldReason);
                }
                return error;
            }
        }

        Student s = findStudentById(student_id);
        if (s != null) {
            AttendanceRecord newRecord = new AttendanceRecord(student_id, s.getClass_Id(), date, status, reason);
            return addRecord(newRecord);
        }
        return null;
    }

    // 특정 날짜 + 특정 반의 출석 기록 전체 삭제
    // AttendanceInputPanel의 "초기화" 버튼에서 호출됨
    // 반환값: 실패한 항목이 있으면 화면에 그대로 보여줄 에러 메시지 목록 (비어있으면 전부 성공)
    public static List<String> deleteRecordsByClassAndDate(int classId, LocalDate date) {
        List<String> errors = new ArrayList<>();

        // 1) 서버에서 해당 조건의 기록 id 목록을 조회해서 삭제 요청
        try {
            String body = httpGet("/attendance?classId=" + classId + "&date=" + date.toString());
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                int id = obj.get("id").getAsInt();
                try {
                    httpDelete("/attendance/" + id);
                } catch (Exception e) {
                    errors.add(friendlyMessage(e));
                }
            }
        } catch (Exception e) {
            errors.add(friendlyMessage(e));
        }

        // 2) 로컬 리스트에서도 제거
        records.removeIf(r ->
            r.getClass_Id() == classId && r.getDate().equals(date)
        );
        return errors;
    }

    // 반환값: 성공하면 null, 실패하면 화면에 그대로 보여줄 에러 메시지
    private static String sendAttendanceToServer(AttendanceRecord r) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("studentId", r.getStudent_Id());
            json.addProperty("classId", r.getClass_Id());
            json.addProperty("date", r.getDate().toString()); // "2026-06-21" 형식
            json.addProperty("status", r.getStatus().name());
            json.addProperty("reason", r.getReason() == null ? "" : r.getReason());

            httpPost("/attendance", json.toString());
            return null;
        } catch (Exception e) {
            String msg = friendlyMessage(e);
            System.err.println("출석 기록 저장 실패: " + msg);
            return msg;
        }
    }
}