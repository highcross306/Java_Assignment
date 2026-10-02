package sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

// 이 PC의 동기화 상태 (~/.attendance-desktop/sync-state.json).
// 암호화 키(keyBase64)가 들어 있으므로 이 폴더의 다른 데이터(DB 파일)와 같은 수준으로 보호해야 함.
// 키를 PC에 저장하는 이유는 종료할 때 비밀번호를 묻지 않고 자동으로 올리기 위함
public class SyncState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = false;
    public String deviceId = UUID.randomUUID().toString();
    public String accountEmail = "";

    public String saltBase64 = "";
    public int iterations = CryptoBox.DEFAULT_ITERATIONS;
    public String keyBase64 = "";

    // 마지막으로 드라이브와 맞춘 시점의 원격 버전, 그때의 로컬 DB 파일 해시
    public long lastSyncedVersion = 0;
    public String lastSyncedHash = "";

    // 다음 시작 때 로컬을 무시하고 드라이브 데이터를 내려받도록 예약 (다른 PC에서 처음 연결할 때)
    public boolean pendingPull = false;

    public String lastSyncTime = "";
    public String lastMessage = "";

    public static SyncState load(File file) {
        try {
            if (file.isFile()) {
                SyncState state = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), SyncState.class);
                if (state != null) return state;
            }
        } catch (Exception e) {
            System.err.println("동기화 상태 파일을 읽지 못했습니다: " + e.getMessage());
        }
        return new SyncState();
    }

    // 쓰는 도중 꺼져도 파일이 깨지지 않도록 임시 파일에 쓴 뒤 교체
    public void save(File file) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        Files.writeString(tmp.toPath(), GSON.toJson(this), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        file.setReadable(false, false);
        file.setReadable(true, true);
    }

    public boolean hasKey() {
        return !keyBase64.isEmpty() && !saltBase64.isEmpty();
    }
}
