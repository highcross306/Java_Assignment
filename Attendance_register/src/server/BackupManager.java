package server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

// 출석 데이터는 이 PC의 H2 파일(data/attendance.mv.db) 하나뿐이라, PC 고장이나 파일 손상이
// 나면 복구할 방법이 없음. 서버가 꺼져 있어서 파일이 안정된 시점(앱 시작 직전, 종료 직후)에
// 하루 단위로 복사본을 남기고 오래된 것은 지운다. 같은 날 여러 번 켜고 꺼도 그날의
// 마지막 상태 하나만 남음. 복원은 앱이 켜지기 전에만 안전하므로, 복원을 예약해두고
// 다음 시작 때 적용한다 (현재 데이터는 복원 직전에 따로 보관)
public class BackupManager {

    private static final int KEEP_DAYS = 14;
    private static final String DB_NAME = "attendance.mv.db";
    private static final String PENDING_RESTORE = "restore-pending.mv.db";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final File dataDir;
    private final File dbFile;
    private final File backupDir;

    public BackupManager(File appDataDir) {
        this.dataDir = new File(appDataDir, "data");
        this.dbFile = new File(dataDir, DB_NAME);
        this.backupDir = new File(appDataDir, "backups");
    }

    public File getBackupDir() {
        backupDir.mkdirs();
        return backupDir;
    }

    // 서버를 띄우기 직전에 호출: 예약된 복원이 있으면 적용하고, 그 다음 오늘 백업이 없으면 만든다
    public void onStartup() {
        applyPendingRestore();
        if (!new File(backupDir, dayFileName(LocalDate.now())).isFile()) {
            backupNow(dayFileName(LocalDate.now()));
        }
    }

    // 서버를 끈 직후에 호출: 오늘 백업을 이 시점의 최종 상태로 덮어씀
    public void onShutdown() {
        backupNow(dayFileName(LocalDate.now()));
    }

    // 복원할 백업 파일을 예약. 실제 적용은 앱을 다시 시작할 때 (실행 중에는 DB 파일이 열려 있음)
    public void scheduleRestore(File backupFile) throws IOException {
        dataDir.mkdirs();
        Files.copy(backupFile.toPath(), new File(dataDir, PENDING_RESTORE).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void applyPendingRestore() {
        File pending = new File(dataDir, PENDING_RESTORE);
        if (!pending.isFile()) return;
        try {
            // 복원 직전의 현재 데이터를 따로 남겨서, 잘못 복원해도 되돌릴 수 있게 함
            if (dbFile.isFile()) {
                backupNow("before-restore-" + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".mv.db");
            }
            Files.move(pending.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("백업 복원 적용 실패: " + e.getMessage());
        }
    }

    private void backupNow(String fileName) {
        if (!dbFile.isFile()) return;
        try {
            backupDir.mkdirs();
            Files.copy(dbFile.toPath(), new File(backupDir, fileName).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            prune();
        } catch (IOException e) {
            // 백업 실패가 앱 실행 자체를 막으면 안 됨
            System.err.println("백업 실패: " + e.getMessage());
        }
    }

    private static String dayFileName(LocalDate day) {
        return "attendance-" + day.format(DAY) + ".mv.db";
    }

    // 일 단위 백업은 최근 KEEP_DAYS개만 남김 (이름의 날짜순 = 시간순)
    private void prune() {
        File[] daily = backupDir.listFiles((d, n) -> n.matches("attendance-\\d{8}\\.mv\\.db"));
        if (daily == null || daily.length <= KEEP_DAYS) return;
        Arrays.sort(daily, Comparator.comparing(File::getName));
        for (int i = 0; i < daily.length - KEEP_DAYS; i++) {
            daily[i].delete();
        }
    }
}
