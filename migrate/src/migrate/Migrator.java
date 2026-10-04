package migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 옛 데이터를 반 → 학생 → 출석 순서로 새 서버에 넣는다. 번호(id)는 새 서버가 새로 매기므로 옛 번호 → 새 번호 대응표를 만들어 씀.
// 여러 번 실행해도 두 번 들어가지 않게:
//  - 같은 이름의 반이 이미 있으면 그 반을 씀
//  - 같은 반에 같은 이름의 학생이 이미 있으면 그 학생을 씀 (동명이인은 개수만큼 짝지음)
//  - 같은 학생·같은 날짜의 출석이 이미 있으면 건너뜀 (새 프로그램에서 입력·수정한 기록을 덮어쓰지 않음)
public class Migrator {

    public interface Listener {
        void log(String line);
        void progress(int done, int total);
    }

    public static class Result {
        public int classesCreated, classesReused;
        public int studentsCreated, studentsReused, studentsSkipped;
        public int attendanceSaved, attendanceExisting, attendanceSkipped;

        public String summary() {
            return "반: 새로 " + classesCreated + "개, 이미 있음 " + classesReused + "개\n"
                    + "학생: 새로 " + studentsCreated + "명, 이미 있음 " + studentsReused + "명"
                    + (studentsSkipped > 0 ? ", 건너뜀 " + studentsSkipped + "명" : "") + "\n"
                    + "출석: 새로 " + attendanceSaved + "건, 이미 있음 " + attendanceExisting + "건"
                    + (attendanceSkipped > 0 ? ", 건너뜀 " + attendanceSkipped + "건" : "");
        }
    }

    private static final Set<String> STATUSES = Set.of("PRESENT", "LATE", "ABSENT", "EARLY_LEAVE", "EXCUSED");

    public static Result run(OldDatabase.Data old, NewServer server, Listener listener) throws IOException {
        Result result = new Result();
        int total = old.classes.size() + old.students.size() + old.attendance.size();
        int done = 0;

        // 1) 반
        Map<String, Deque<Integer>> existingClasses = new HashMap<>();
        for (JsonElement e : server.getClasses()) {
            JsonObject o = e.getAsJsonObject();
            existingClasses.computeIfAbsent(text(o, "className"), k -> new ArrayDeque<>()).add(o.get("classId").getAsInt());
        }
        Map<Integer, Integer> classMap = new HashMap<>();
        for (OldDatabase.OldClass c : old.classes) {
            Deque<Integer> same = existingClasses.get(c.name());
            if (same != null && !same.isEmpty()) {
                classMap.put(c.id(), same.poll());
                result.classesReused++;
            } else {
                classMap.put(c.id(), server.createClass(c.name()));
                result.classesCreated++;
                listener.log("반 추가: " + c.name());
            }
            listener.progress(++done, total);
        }

        // 2) 학생
        Map<String, Deque<Integer>> existingStudents = new HashMap<>();
        for (JsonElement e : server.getStudents()) {
            JsonObject o = e.getAsJsonObject();
            existingStudents.computeIfAbsent(studentKey(o.get("classId").getAsInt(), text(o, "name")), k -> new ArrayDeque<>())
                    .add(o.get("studentId").getAsInt());
        }
        Map<Integer, Integer> studentMap = new HashMap<>();
        Map<Integer, Integer> studentClass = new HashMap<>();
        for (OldDatabase.OldStudent s : old.students) {
            Integer newClassId = classMap.get(s.classId());
            if (newClassId == null) {
                result.studentsSkipped++;
                listener.log("건너뜀(소속 반이 없음): 학생 " + s.name() + " (옛 반 번호 " + s.classId() + ")");
            } else {
                Deque<Integer> same = existingStudents.get(studentKey(newClassId, s.name()));
                int newId;
                if (same != null && !same.isEmpty()) {
                    newId = same.poll();
                    result.studentsReused++;
                } else {
                    newId = server.createStudent(s.name(), s.school(), newClassId, s.studentPhone(), s.guardianPhone());
                    result.studentsCreated++;
                }
                studentMap.put(s.id(), newId);
                studentClass.put(newId, newClassId);
            }
            listener.progress(++done, total);
        }
        listener.log("학생 " + (result.studentsCreated + result.studentsReused) + "명 준비됨 (새로 " + result.studentsCreated + "명)");

        // 3) 출석
        Set<String> existingAttendance = new HashSet<>();
        for (JsonElement e : server.getAttendance()) {
            JsonObject o = e.getAsJsonObject();
            existingAttendance.add(o.get("studentId").getAsInt() + "|" + text(o, "date"));
        }
        for (OldDatabase.OldAttendance a : old.attendance) {
            Integer newStudentId = studentMap.get(a.studentId());
            String status = a.status() == null ? null : a.status().trim().toUpperCase();
            if (newStudentId == null) {
                result.attendanceSkipped++;
                listener.log("건너뜀(학생 없음): " + a.date() + " 옛 학생 번호 " + a.studentId());
            } else if (!STATUSES.contains(status)) {
                result.attendanceSkipped++;
                listener.log("건너뜀(알 수 없는 상태 '" + a.status() + "'): " + a.date() + " 옛 학생 번호 " + a.studentId());
            } else if (!existingAttendance.add(newStudentId + "|" + a.date())) {
                result.attendanceExisting++;
            } else {
                server.saveAttendance(newStudentId, studentClass.get(newStudentId), a.date(), status, a.reason());
                result.attendanceSaved++;
            }
            listener.progress(++done, total);
        }
        return result;
    }

    private static String studentKey(int classId, String name) {
        return classId + "|" + name;
    }

    private static String text(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
