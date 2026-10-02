package sync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.function.Supplier;

// 여러 PC에서 같은 출석 데이터를 쓰기 위한 동기화 로직.
//
//  - 서버를 띄우기 직전(beforeServerStart): 드라이브에 더 새로운 버전이 있으면 받아와서 로컬 DB를 교체
//  - 서버를 끈 직후(afterServerStop): 로컬 DB가 바뀌었으면 암호화해서 드라이브에 올림
//
// DB 파일은 서버가 꺼져 있는 이 두 시점에만 만지므로 파일이 일관된 상태다.
// 같은 시점에 두 PC가 각자 바꾼 "충돌"은 조용히 덮어쓰지 않는다:
//   - 시작할 때 발견하면 사용자에게 어느 쪽을 쓸지 묻고(Ui.askConflict),
//   - 종료할 때 발견하면 업로드를 건너뛰어서(다른 PC의 데이터를 덮어쓰지 않음) 다음 시작 때 묻는다.
// 로컬 DB를 교체하기 전에는 항상 백업 폴더에 먼저 보관한다.
//
// 이 클래스의 어떤 실패(오프라인, 비밀번호 불일치 등)도 앱 실행을 막지 않고 lastMessage에만 기록한다.
public class DriveSync {

    public enum Conflict { USE_DRIVE, KEEP_LOCAL, LATER }

    public interface Ui {
        // remoteTime: 드라이브에 마지막으로 올라온 시각 (표시용)
        Conflict askConflict(String remoteTime);
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File stateFile;
    private final File dbFile;
    private final Supplier<RemoteStore> remoteFactory;
    private final Ui ui;
    private final Consumer<String> localSnapshot;
    private final SyncState state;
    // 이번 실행 동안 업로드를 하지 않음 (충돌을 "나중에"로 미뤘거나 복호화에 실패한 경우)
    private boolean skipUpload = false;

    // DB 파일의 "내용"을 대표하는 값 (같은 내용이면 항상 같은 값). DbFingerprint 참고
    public interface Fingerprinter {
        String of(File dbFile) throws IOException;
    }

    private final Fingerprinter fingerprint;

    // 시험용 기본값: 파일 바이트 해시 (실제 H2 파일에는 쓰지 말 것 - 열고 닫기만 해도 값이 바뀜)
    public DriveSync(File appDataDir, Supplier<RemoteStore> remoteFactory, Ui ui, Consumer<String> localSnapshot) {
        this(appDataDir, remoteFactory, ui, localSnapshot, DriveSync::sha256);
    }

    // localSnapshot: 로컬 DB를 교체하기 전에 지정한 이름으로 백업해 두는 동작 (BackupManager 연결)
    public DriveSync(File appDataDir, Supplier<RemoteStore> remoteFactory, Ui ui, Consumer<String> localSnapshot,
                     Fingerprinter fingerprint) {
        this.fingerprint = fingerprint;
        this.stateFile = new File(appDataDir, "sync-state.json");
        this.dbFile = new File(new File(appDataDir, "data"), "attendance.mv.db");
        this.remoteFactory = remoteFactory;
        this.ui = ui;
        this.localSnapshot = localSnapshot;
        this.state = SyncState.load(stateFile);
    }

    public synchronized boolean isEnabled() { return state.enabled && state.hasKey(); }
    public synchronized String getAccountEmail() { return state.accountEmail; }
    public synchronized String getLastMessage() { return state.lastMessage; }
    public synchronized String getLastSyncTime() { return state.lastSyncTime; }
    public synchronized String getConsentAt() { return state.consentAt; }

    public static final int CONSENT_VERSION = 1;
    private String pendingConsentAt = "";

    // 설정 화면에서 동의 항목을 모두 체크하고 [허용]을 눌렀을 때 호출. 실제로 동기화가 켜지는 시점(applyKey)에
    // 함께 저장되므로, 로그인이나 비밀번호 입력을 중간에 취소하면 동의도 기록되지 않는다
    public synchronized void markConsentGiven() {
        pendingConsentAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ---------------------------------------------------------------- 시작/종료 시 자동 동기화

    public synchronized void beforeServerStart() {
        skipUpload = false;
        if (!isEnabled()) return;
        try {
            RemoteStore.Meta meta = remoteFactory.get().head();
            if (meta == null) {
                state.pendingPull = false; // 받아올 게 없음. 종료할 때 이 PC 데이터가 올라감
                note("드라이브에 아직 데이터가 없습니다");
                return;
            }

            boolean localExists = dbFile.isFile();
            boolean localChanged = localExists && !fingerprint.of(dbFile).equals(state.lastSyncedHash);
            boolean remoteNewer = meta.version() > state.lastSyncedVersion;

            if (state.pendingPull || (remoteNewer && !localChanged)) {
                pull(meta, localExists);
            } else if (remoteNewer) {
                switch (ui.askConflict(meta.modifiedTime())) {
                    case USE_DRIVE -> pull(meta, localExists);
                    case KEEP_LOCAL -> {
                        // 드라이브 버전을 "내가 봤다"고 인정 -> 종료할 때 이 PC 데이터가 드라이브를 덮어씀
                        state.lastSyncedVersion = meta.version();
                        note("이 PC의 데이터를 유지합니다 (종료할 때 드라이브에 올라갑니다)");
                    }
                    case LATER -> {
                        skipUpload = true;
                        note("충돌을 나중에 해결하기로 했습니다 (이번 실행에서는 동기화하지 않음)");
                    }
                }
            } else {
                note("최신 상태입니다");
            }
        } catch (Exception e) {
            note("시작 시 동기화 실패: " + describe(e));
        }
    }

    public synchronized void afterServerStop() {
        if (!isEnabled() || skipUpload || !dbFile.isFile()) return;
        try {
            String hash = fingerprint.of(dbFile);
            if (hash.equals(state.lastSyncedHash)) {
                note("변경된 내용이 없어 올리지 않았습니다");
                return;
            }

            RemoteStore remote = remoteFactory.get();
            RemoteStore.Meta meta = remote.head();
            long remoteVersion = meta == null ? 0 : meta.version();
            if (remoteVersion != state.lastSyncedVersion) {
                // 그 사이 다른 PC가 올렸음 -> 덮어쓰면 그쪽 변경이 사라지므로 올리지 않고 다음 시작 때 선택하게 함
                note("다른 PC에서 먼저 올린 변경이 있어 업로드하지 않았습니다. 다음 실행 때 어느 쪽을 쓸지 선택하세요");
                return;
            }

            byte[] encrypted = CryptoBox.encrypt(Base64.getDecoder().decode(state.keyBase64),
                    Base64.getDecoder().decode(state.saltBase64), state.iterations, Files.readAllBytes(dbFile.toPath()));
            remote.upload(encrypted, remoteVersion + 1, state.deviceId);

            state.lastSyncedVersion = remoteVersion + 1;
            state.lastSyncedHash = hash;
            note("드라이브에 업로드했습니다 (버전 " + state.lastSyncedVersion + ")");
        } catch (Exception e) {
            note("종료 시 동기화 실패: " + describe(e));
        }
    }

    private void pull(RemoteStore.Meta meta, boolean localExists) throws IOException, GeneralSecurityException {
        byte[] plain;
        try {
            plain = CryptoBox.decrypt(Base64.getDecoder().decode(state.keyBase64), remoteFactory.get().download());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            skipUpload = true; // 키가 안 맞는 상태로 올리면 드라이브 데이터를 망가뜨릴 수 있음
            throw e;
        }

        // 받은 데이터를 임시 위치에 먼저 풀어서 지문을 계산한다. 이 단계가 실패하면(손상된 DB 등)
        // 로컬 DB는 건드리지 않은 채 중단됨
        File stagingDir = new File(dbFile.getParentFile(), "sync-tmp");
        stagingDir.mkdirs();
        File staged = new File(stagingDir, dbFile.getName());
        String stagedHash;
        try {
            Files.write(staged.toPath(), plain);
            stagedHash = fingerprint.of(staged);

            if (localExists) {
                localSnapshot.accept("before-sync-" + LocalDateTime.now().format(STAMP) + ".mv.db");
            }
            Files.move(staged.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            staged.delete();
            stagingDir.delete();
        }

        state.lastSyncedVersion = meta.version();
        state.lastSyncedHash = stagedHash;
        state.pendingPull = false;
        note("드라이브의 최신 데이터를 받아왔습니다 (버전 " + meta.version() + ")");
    }

    // ---------------------------------------------------------------- 설정 화면용

    // 설정 화면에서 드라이브에 이미 데이터가 있는지 확인 (없으면 null)
    public synchronized RemoteStore.Meta peekRemote() throws IOException {
        return remoteFactory.get().head();
    }

    // 이 구글 계정으로 처음 동기화를 켜는 PC: 새 동기화 비밀번호를 정함
    public synchronized void enableWithNewPassphrase(char[] passphrase, String email) throws IOException, GeneralSecurityException {
        byte[] salt = CryptoBox.newSalt();
        byte[] key = CryptoBox.deriveKey(passphrase, salt, CryptoBox.DEFAULT_ITERATIONS);
        applyKey(salt, CryptoBox.DEFAULT_ITERATIONS, key, email);
        state.lastSyncedVersion = 0;
        state.lastSyncedHash = "";
        state.pendingPull = false;
        note("동기화를 켰습니다. 프로그램을 종료할 때 데이터가 드라이브에 올라갑니다");
    }

    // 드라이브에 이미 데이터가 있을 때: 비밀번호가 맞는지 실제로 복호화해서 확인. 틀리면 예외(상태는 바뀌지 않음)
    public synchronized RemoteStore.Meta verifyExisting(char[] passphrase) throws IOException, GeneralSecurityException {
        RemoteStore remote = remoteFactory.get();
        RemoteStore.Meta meta = remote.head();
        if (meta == null) throw new IOException("드라이브에 동기화 데이터가 없습니다");
        byte[] file = remote.download();
        CryptoBox.Header header = CryptoBox.readHeader(file);
        byte[] key = CryptoBox.deriveKey(passphrase, header.salt(), header.iterations());
        CryptoBox.decrypt(key, file); // 틀린 비밀번호면 여기서 AEADBadTagException
        pendingKey = key;
        pendingHeader = header;
        return meta;
    }

    private byte[] pendingKey;
    private CryptoBox.Header pendingHeader;

    // verifyExisting 성공 후: 드라이브 데이터를 쓸지, 이 PC 데이터를 쓸지 정하고 동기화를 켬
    public synchronized void enableWithExisting(RemoteStore.Meta meta, boolean useDriveData, String email) throws IOException {
        if (pendingKey == null || pendingHeader == null) throw new IllegalStateException("비밀번호 확인이 먼저 필요합니다");
        applyKey(pendingHeader.salt(), pendingHeader.iterations(), pendingKey, email);
        pendingKey = null;
        pendingHeader = null;
        if (useDriveData) {
            state.pendingPull = true; // 서버가 꺼져 있는 다음 시작 때 적용 (지금은 DB 파일이 열려 있음)
            state.lastSyncedVersion = 0;
            state.lastSyncedHash = "";
            note("동기화를 켰습니다. 프로그램을 다시 시작하면 드라이브의 데이터를 받아옵니다");
        } else {
            state.pendingPull = false;
            state.lastSyncedVersion = meta.version(); // 종료할 때 이 PC 데이터가 드라이브를 덮어씀
            state.lastSyncedHash = "";
            note("동기화를 켰습니다. 종료할 때 이 PC의 데이터가 드라이브에 올라갑니다");
        }
    }

    // 동기화 해제: 이 PC의 키와 상태를 지움. 드라이브의 파일은 그대로 둠
    public synchronized void disable() {
        state.enabled = false;
        state.keyBase64 = "";
        state.saltBase64 = "";
        state.pendingPull = false;
        state.accountEmail = "";
        state.consentAt = "";
        state.consentVersion = 0;
        note("동기화를 해제했습니다 (드라이브의 데이터는 그대로 남아 있습니다)");
    }

    private void applyKey(byte[] salt, int iterations, byte[] key, String email) {
        state.saltBase64 = Base64.getEncoder().encodeToString(salt);
        state.iterations = iterations;
        state.keyBase64 = Base64.getEncoder().encodeToString(key);
        state.accountEmail = email == null ? "" : email;
        state.enabled = true;
        if (!pendingConsentAt.isEmpty()) {
            state.consentAt = pendingConsentAt;
            state.consentVersion = CONSENT_VERSION;
            pendingConsentAt = "";
        }
    }

    // ---------------------------------------------------------------- 공통

    private void note(String message) {
        state.lastMessage = message;
        state.lastSyncTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            state.save(stateFile);
        } catch (IOException e) {
            System.err.println("동기화 상태 저장 실패: " + e.getMessage());
        }
    }

    private static String describe(Exception e) {
        if (e instanceof GeneralSecurityException) return "동기화 비밀번호가 다르거나 데이터가 손상되었습니다";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    static String sha256(File file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
