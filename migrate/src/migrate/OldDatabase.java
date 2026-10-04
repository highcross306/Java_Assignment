package migrate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 예전 버전(MariaDB에 직접 저장하던 서버)의 데이터를 읽기만 하는 클래스. 옛 DB에는 아무것도 쓰지 않는다.
// 테이블 구조: classes(class_id, class_name), students(student_id, name, school, class_id, student_phone, guardian_phone),
//             attendance(id, student_id, class_id, date, status, reason)
// 회원가입 기능이 들어간 뒤에 MariaDB를 썼던 경우에는 각 표에 user_id와 users 표가 있을 수 있어서, 그때는 계정별로 골라 읽는다.
public class OldDatabase {

    public record OldClass(int id, String name) {}
    public record OldStudent(int id, String name, String school, int classId, String studentPhone, String guardianPhone) {}
    public record OldAttendance(int studentId, int classId, LocalDate date, String status, String reason) {}
    public record OldUser(int id, String username) {
        @Override public String toString() { return username; }
    }

    public static class Data {
        public final List<OldClass> classes = new ArrayList<>();
        public final List<OldStudent> students = new ArrayList<>();
        public final List<OldAttendance> attendance = new ArrayList<>();

        public String summary() {
            LocalDate first = null, last = null;
            for (OldAttendance a : attendance) {
                if (first == null || a.date().isBefore(first)) first = a.date();
                if (last == null || a.date().isAfter(last)) last = a.date();
            }
            String range = first == null ? "" : " (" + first + " ~ " + last + ")";
            return "반 " + classes.size() + "개, 학생 " + students.size() + "명, 출석 기록 " + attendance.size() + "건" + range;
        }
    }

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public OldDatabase(String host, int port, String database, String user, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    private Connection connect(String db) throws SQLException {
        String url = "jdbc:mariadb://" + host + ":" + port + "/" + (db == null ? "" : db) + "?connectTimeout=5000";
        return DriverManager.getConnection(url, user, password);
    }

    // DB 이름을 잘못 적었을 때 안내용: 이 계정으로 볼 수 있는 DB 목록 (시스템 DB 제외)
    public List<String> listDatabases() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection c = connect(null); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String n = rs.getString(1);
                if (!Set.of("information_schema", "mysql", "performance_schema", "sys").contains(n.toLowerCase())) names.add(n);
            }
        }
        return names;
    }

    // users 표와 user_id 열이 있으면 옛 계정 목록, 없으면(회원가입 기능 이전 버전) 빈 목록
    public List<OldUser> listUsers() throws SQLException {
        List<OldUser> users = new ArrayList<>();
        try (Connection c = connect(database)) {
            if (!tableExists(c, "users") || !columns(c, "classes").contains("user_id")) return users;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT user_id, username FROM users ORDER BY user_id")) {
                while (rs.next()) users.add(new OldUser(rs.getInt(1), rs.getString(2)));
            }
        }
        return users;
    }

    // oldUserId가 null이면 전체를 읽음
    public Data load(Integer oldUserId) throws SQLException {
        Data data = new Data();
        try (Connection c = connect(database)) {
            for (String t : List.of("classes", "students", "attendance")) {
                if (!tableExists(c, t)) {
                    throw new SQLException("'" + database + "' DB에 " + t + " 표가 없습니다. 출석 관리 프로그램의 DB가 맞는지 확인해 주세요.");
                }
            }
            for (Map<String, Object> r : rows(c, "classes", oldUserId, "class_id")) {
                data.classes.add(new OldClass(intOf(r.get("class_id")), str(r.get("class_name"))));
            }
            for (Map<String, Object> r : rows(c, "students", oldUserId, "student_id")) {
                data.students.add(new OldStudent(intOf(r.get("student_id")), str(r.get("name")), str(r.get("school")),
                        intOf(r.get("class_id")), str(r.get("student_phone")), str(r.get("guardian_phone"))));
            }
            for (Map<String, Object> r : rows(c, "attendance", oldUserId, "date")) {
                data.attendance.add(new OldAttendance(intOf(r.get("student_id")), intOf(r.get("class_id")),
                        dateOf(r.get("date")), str(r.get("status")), str(r.get("reason"))));
            }
        }
        return data;
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getTables(c.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static Set<String> columns(Connection c, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (ResultSet rs = c.getMetaData().getColumns(c.getCatalog(), null, table, null)) {
            while (rs.next()) cols.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        return cols;
    }

    // 버전마다 열이 조금씩 다를 수 있어서 SELECT *로 읽고 열 이름(소문자)으로 꺼냄. 없는 열은 null
    private static List<Map<String, Object>> rows(Connection c, String table, Integer oldUserId, String orderBy) throws SQLException {
        String sql = "SELECT * FROM " + table;
        if (oldUserId != null && columns(c, table).contains("user_id")) sql += " WHERE user_id = " + oldUserId;
        sql += " ORDER BY " + orderBy;
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                out.add(row);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int intOf(Object o) {
        return o == null ? -1 : ((Number) o).intValue();
    }

    private static LocalDate dateOf(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.time.LocalDateTime t) return t.toLocalDate();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        return LocalDate.parse(o.toString().substring(0, 10));
    }
}
