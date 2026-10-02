package server;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

// 이 PC에서 프로그램이 "지금 실행 중인지"와 "지난번에 정상 종료했는지"를 기록한다.
//
//  1) 실행 중 표시: 프로그램이 켜져 있는 동안 session.lock 파일에 OS 수준의 파일 잠금을 잡고 있다.
//     프로그램이 어떻게 죽든(강제 종료, 전원 차단 포함) OS가 잠금을 풀어주므로, PID를 적어두는 방식과 달리
//     "이미 죽은 프로그램의 흔적" 때문에 오판하지 않는다. 같은 PC에서 두 번째로 실행하면 잠금을 잡지 못해서
//     중복 실행을 알 수 있다 (두 번째 실행이 첫 번째 실행의 서버를 "남은 서버"로 알고 종료시키는 것을 막음).
//
//  2) 정상 종료 표시: session.state 파일에 시작할 때 "running", 정상 종료 절차(백업, 드라이브 업로드)를 마치면
//     "clean"을 적는다. 다음 실행 때 잠금은 잡히는데 "running"이 남아 있으면 지난번이 정상 종료가 아니었다는 뜻이다
//     (작업 관리자로 종료, 정전, Windows 종료 중 강제 종료 등). 이때는 건너뛴 종료 절차를 이번 시작 때 대신 한다.
public final class SessionGuard {

    public enum Result {
        OK,
        ALREADY_RUNNING,
        // 지난 실행이 정상 종료되지 않음 -> 종료 때 했어야 할 백업/업로드를 지금 해야 함
        PREVIOUS_EXIT_UNCLEAN
    }

    private static final String RUNNING = "running";
    private static final String CLEAN = "clean";

    private final File lockFile;
    private final File stateFile;
    private FileChannel channel;
    private FileLock lock;

    public SessionGuard(File appDataDir) {
        this.lockFile = new File(appDataDir, "session.lock");
        this.stateFile = new File(appDataDir, "session.state");
    }

    // 잠금을 잡고 지난 실행의 종료 상태를 알려준다. ALREADY_RUNNING이면 잠금은 잡히지 않은 상태
    public Result acquire() throws IOException {
        channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null; // 같은 JVM 안에서 이미 잡혀 있음
        }
        if (lock == null) {
            close();
            return Result.ALREADY_RUNNING;
        }
        boolean unclean = stateFile.isFile() && RUNNING.equals(read());
        return unclean ? Result.PREVIOUS_EXIT_UNCLEAN : Result.OK;
    }

    // 서버를 띄우기 직전에 호출 (이 시점부터 DB 파일이 바뀔 수 있음)
    public void markRunning() throws IOException {
        write(RUNNING);
    }

    // 종료 절차(서버 종료, 백업, 업로드)를 모두 마친 뒤에 호출
    public void markClean() {
        try {
            write(CLEAN);
        } catch (IOException e) {
            System.err.println("종료 상태를 기록하지 못했습니다: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (lock != null) lock.release();
        } catch (IOException ignored) {
            // 프로세스가 끝나면 어차피 OS가 풀어줌
        }
        try {
            if (channel != null) channel.close();
        } catch (IOException ignored) {
            // 위와 같음
        }
        lock = null;
        channel = null;
    }

    private String read() {
        try {
            return Files.readString(stateFile.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void write(String value) throws IOException {
        Files.writeString(stateFile.toPath(), value, StandardCharsets.UTF_8);
    }
}
