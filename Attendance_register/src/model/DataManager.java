package model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;

public class DataManager {

    // ====== 서버 주소 ======
    // 같은 PC에서 서버까지 실행 중이면 localhost로 충분함.
    // 만약 다른 컴퓨터(서버용 PC)에서 서버를 돌린다면, 이 부분을 그 PC의 IP로 바꿔야 함
    // 예: "http://192.168.0.15:8080/api"
    private static final String SERVER_URL = "http://localhost:9090/api";

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
    // 프로그램 시작 시 호출, 서버에서 전체 데이터 불러오기
    // =====================================================
    public static void loadAll() {
        loadClasses();
        loadStudents();
        loadAttendance();
    }

    // 저장 버튼 등에서 호출, 로컬에 있는 변경사항을 서버로 올림
    public static void saveAll() {
        saveClasses();
        saveStudents();
        // 출석 기록(records)은 addRecord/updateRecord 호출 시점에
        // 이미 곧바로 서버에 반영되기 때문에 여기서 따로 보낼 게 없음
    }

    // =====================================================
    // 공통 HTTP 헬퍼 메서드
    // (GET/POST/PUT/DELETE를 매번 똑같이 반복 작성하지 않도록 정리한 것)
    // =====================================================
    private static String httpGet(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + path))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return res.body();
    }

    private static String httpPost(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return res.body();
    }

    private static void httpPut(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void httpDelete(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_URL + path))
                .DELETE()
                .build();
        client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Class_Info cls = new Class_Info(obj.getInt("classId"), obj.getString("className"));
                classes.add(cls);
                classMap.put(cls.getClass_Id(), cls);
                knownClassIds.add(cls.getClass_Id());
            }
        } catch (Exception e) {
            System.err.println("반 데이터 불러오기 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    private static void saveClasses() {
        // 1) 로컬에 있는 항목들을 서버로 반영 (새 항목은 등록, 기존 항목은 수정)
        for (Class_Info cls : classes) {
            try {
                JSONObject json = new JSONObject();
                json.put("className", cls.getClass_Name());

                if (knownClassIds.contains(cls.getClass_Id())) {
                    // 이미 서버에 있는 반 -> 수정(PUT)
                    httpPut("/classes/" + cls.getClass_Id(), json.toString());
                } else {
                    // 서버에 아직 없는 반 -> 등록(POST)
                    String resBody = httpPost("/classes", json.toString());
                    JSONObject created = new JSONObject(resBody);
                    int serverId = created.getInt("classId");

                    // 서버가 실제로 발급한 id로, 로컬 객체의 id를 맞춰줌
                    // (로컬에서 임시로 매겼던 id와 서버 id가 다를 수 있기 때문)
                    classMap.remove(cls.getClass_Id());
                    cls.setClass_Id(serverId);
                    classMap.put(serverId, cls);
                    knownClassIds.add(serverId);
                }
            } catch (Exception e) {
                System.err.println("반 저장 실패: " + e.getMessage());
            }
        }

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
                System.err.println("반 삭제 실패: " + e.getMessage());
            }
        }
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
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Student s = new Student(
                        obj.getString("name"),
                        obj.optString("school", ""),
                        obj.getInt("classId")
                );
                s.setStudent_Id(obj.getInt("studentId"));
                s.setStudent_Phone(obj.optString("studentPhone", ""));
                s.setGuardian_Phone(obj.optString("guardianPhone", ""));
                students.add(s);
                studentMap.put(s.getStudent_id(), s);
                knownStudentIds.add(s.getStudent_id());
            }
        } catch (Exception e) {
            System.err.println("학생 데이터 불러오기 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }

    private static void saveStudents() {
        for (Student s : students) {
            try {
                JSONObject json = new JSONObject();
                json.put("name", s.getName());
                json.put("school", s.getSchool());
                json.put("classId", s.getClass_Id());
                json.put("studentPhone", s.getStudent_Phone());
                json.put("guardianPhone", s.getGuardian_Phone());

                if (knownStudentIds.contains(s.getStudent_id())) {
                    httpPut("/students/" + s.getStudent_id(), json.toString());
                } else {
                    String resBody = httpPost("/students", json.toString());
                    JSONObject created = new JSONObject(resBody);
                    int serverId = created.getInt("studentId");

                    studentMap.remove(s.getStudent_id());
                    s.setStudent_Id(serverId);
                    studentMap.put(serverId, s);
                    knownStudentIds.add(serverId);
                }
            } catch (Exception e) {
                System.err.println("학생 저장 실패: " + e.getMessage());
            }
        }

        Set<Integer> currentIds = new HashSet<>();
        for (Student s : students) currentIds.add(s.getStudent_id());

        Set<Integer> toDelete = new HashSet<>(knownStudentIds);
        toDelete.removeAll(currentIds);
        for (Integer deletedId : toDelete) {
            try {
                httpDelete("/students/" + deletedId);
                knownStudentIds.remove(deletedId);
            } catch (Exception e) {
                System.err.println("학생 삭제 실패: " + e.getMessage());
            }
        }
    }

    // =====================================================
    // 출석 기록 불러오기
    // (저장은 addRecord/updateRecord에서 즉시 처리하므로 별도 saveAttendance는 없음)
    // =====================================================
    private static void loadAttendance() {
        records.clear();
        try {
            String body = httpGet("/attendance");
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                records.add(new AttendanceRecord(
                        obj.getInt("studentId"),
                        obj.getInt("classId"),
                        LocalDate.parse(obj.getString("date")),
                        AttendanceStatus.valueOf(obj.getString("status")),
                        obj.optString("reason", "")
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
    public static void addRecord(AttendanceRecord record) {
        records.add(record);
        sendAttendanceToServer(record);
    }

    public static void updateRecord(int student_id, LocalDate date, AttendanceStatus status, String reason) {
        for (AttendanceRecord r : records) {
            if (r.getStudent_Id() == student_id && r.getDate().equals(date)) {
                r.setStatus(status);
                r.setReason(reason);
                sendAttendanceToServer(r);
                return;
            }
        }

        Student s = findStudentById(student_id);
        if (s != null) {
            AttendanceRecord newRecord = new AttendanceRecord(student_id, s.getClass_Id(), date, status, reason);
            addRecord(newRecord);
        }
    }

    // 특정 날짜 + 특정 반의 출석 기록 전체 삭제
    // AttendanceInputPanel의 "초기화" 버튼에서 호출됨
    public static void deleteRecordsByClassAndDate(int classId, LocalDate date) {
        // 1) 서버에서 해당 조건의 기록 id 목록을 조회해서 삭제 요청
        try {
            String body = httpGet("/attendance?classId=" + classId + "&date=" + date.toString());
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                int id = obj.getInt("id");
                httpDelete("/attendance/" + id);
            }
        } catch (Exception e) {
            System.err.println("출석 기록 삭제 실패: " + e.getMessage());
        }

        // 2) 로컬 리스트에서도 제거
        records.removeIf(r ->
            r.getClass_Id() == classId && r.getDate().equals(date)
        );
    }

    private static void sendAttendanceToServer(AttendanceRecord r) {
        try {
            JSONObject json = new JSONObject();
            json.put("studentId", r.getStudent_Id());
            json.put("classId", r.getClass_Id());
            json.put("date", r.getDate().toString()); // "2026-06-21" 형식
            json.put("status", r.getStatus().name());
            json.put("reason", r.getReason() == null ? "" : r.getReason());

            httpPost("/attendance", json.toString());
        } catch (Exception e) {
            System.err.println("출석 기록 저장 실패 (서버 연결 확인 필요): " + e.getMessage());
        }
    }
}