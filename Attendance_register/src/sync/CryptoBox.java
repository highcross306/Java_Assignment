package sync;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

// 드라이브에 올리는 데이터를 암호화/복호화. 구글에는 암호문만 올라가고 키는 이 PC(와 사용자가
// 아는 "동기화 비밀번호")에만 있으므로, 구글 계정이 털려도 비밀번호 없이는 내용을 볼 수 없다.
//
// 파일 형식: "ATTSYNC1"(8) | salt(16) | PBKDF2 반복 횟수(4) | IV(12) | AES-256-GCM 암호문+태그(16)
// 앞쪽 헤더 전체를 GCM의 AAD로 묶어서, 헤더를 바꿔치기해도 복호화가 실패하게 함.
// 같은 비밀번호 + 같은 salt면 같은 키가 나오므로, 여러 PC가 드라이브 파일의 salt를 읽어서
// 자기 키를 만들 수 있다 (매 업로드마다 IV만 새로 만듦)
public final class CryptoBox {

    private static final byte[] MAGIC = "ATTSYNC1".getBytes(StandardCharsets.US_ASCII);
    public static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final int HEADER_LEN = MAGIC.length + SALT_LEN + 4 + IV_LEN;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CryptoBox() {}

    public record Header(byte[] salt, int iterations) {}

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_LEN];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] deriveKey(char[] passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static byte[] encrypt(byte[] key, byte[] salt, int iterations, byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LEN];
        RANDOM.nextBytes(iv);
        byte[] aad = aad(salt, iterations);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        byte[] ciphertext = cipher.doFinal(plain);

        return ByteBuffer.allocate(aad.length + IV_LEN + ciphertext.length)
                .put(aad).put(iv).put(ciphertext).array();
    }

    // 형식이 이 프로그램의 것이 아니면 IllegalArgumentException
    public static Header readHeader(byte[] file) {
        if (file == null || file.length < HEADER_LEN + TAG_BITS / 8
                || !Arrays.equals(Arrays.copyOfRange(file, 0, MAGIC.length), MAGIC)) {
            throw new IllegalArgumentException("동기화 파일 형식이 올바르지 않습니다");
        }
        ByteBuffer buf = ByteBuffer.wrap(file, MAGIC.length, SALT_LEN + 4);
        byte[] salt = new byte[SALT_LEN];
        buf.get(salt);
        return new Header(salt, buf.getInt());
    }

    // 키가 틀렸거나 파일이 변조됐으면 AEADBadTagException
    public static byte[] decrypt(byte[] key, byte[] file) throws GeneralSecurityException {
        Header header = readHeader(file);
        byte[] aad = Arrays.copyOfRange(file, 0, MAGIC.length + SALT_LEN + 4);
        byte[] iv = Arrays.copyOfRange(file, aad.length, aad.length + IV_LEN);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        try {
            return cipher.doFinal(file, HEADER_LEN, file.length - HEADER_LEN);
        } catch (AEADBadTagException e) {
            throw new AEADBadTagException("비밀번호가 다르거나 파일이 손상되었습니다");
        }
    }

    private static byte[] aad(byte[] salt, int iterations) {
        return ByteBuffer.allocate(MAGIC.length + SALT_LEN + 4)
                .put(MAGIC).put(salt).putInt(iterations).array();
    }
}
