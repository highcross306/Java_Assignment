package migrate;

import javax.swing.UIManager;
import java.util.HashMap;
import java.util.Map;

// 예전 버전(MariaDB) 데이터 → 지금 버전 옮기기 도구의 시작점.
// 보통은 창으로 실행. 시험·자동화용으로 창 없이도 실행 가능:
//   AttendanceMigrate.exe --cli dbHost=localhost dbPort=3306 db=attendance_db dbUser=attendance_user dbPassword=...
//                         server=http://localhost:18080 user=아이디 password=비밀번호 [create=true] [oldUser=옛계정]
public class MigrateMain {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--cli")) {
            System.exit(runCli(args));
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 기본 모양 사용
        }
        MigrateWindow.open();
    }

    private static int runCli(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) o.put(args[i].substring(0, eq), args[i].substring(eq + 1));
        }
        try {
            OldDatabase db = new OldDatabase(o.getOrDefault("dbHost", "localhost"),
                    Integer.parseInt(o.getOrDefault("dbPort", "3306")), o.getOrDefault("db", "attendance_db"),
                    o.getOrDefault("dbUser", "attendance_user"), o.getOrDefault("dbPassword", ""));
            Integer oldUserId = null;
            if (o.containsKey("oldUser")) {
                for (OldDatabase.OldUser u : db.listUsers()) {
                    if (u.username().equals(o.get("oldUser"))) oldUserId = u.id();
                }
                if (oldUserId == null) throw new IllegalArgumentException("옛 계정을 찾을 수 없음: " + o.get("oldUser"));
            }
            OldDatabase.Data data = db.load(oldUserId);
            System.out.println("예전 데이터 " + data.summary());

            NewServer server = new NewServer(o.getOrDefault("server", NewServer.defaultBaseUrl()));
            if (!server.isUp()) throw new IllegalStateException("지금 프로그램 서버에 연결할 수 없음");
            server.login(o.get("user"), o.get("password"), Boolean.parseBoolean(o.getOrDefault("create", "false")));
            Migrator.Result r = Migrator.run(data, server, new Migrator.Listener() {
                @Override public void log(String line) { System.out.println(line); }
                @Override public void progress(int done, int total) { }
            });
            System.out.println(r.summary());
            return 0;
        } catch (Exception e) {
            System.err.println("실패: " + e.getMessage());
            return 1;
        }
    }
}
