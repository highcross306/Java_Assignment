package sync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

// DB의 "내용"을 대표하는 지문. H2 파일(.mv.db)은 서버를 켰다 끄기만 해도 바이트가 매번 바뀌고
// 커지기 때문에(실측), 파일 해시로 "데이터가 바뀌었는지"를 판단하면 항상 바뀐 것으로 보여서
// 매번 업로드되고 다른 PC와 가짜 충돌이 생긴다. 대신 H2의 SCRIPT(모든 테이블/행을 SQL로 덤프)
// 결과를 해시하면 내용이 같을 때 항상 같은 값이 나온다 (같은 DB를 3번 열고 닫아서 확인함).
//
// 서버가 꺼져 있는(DB 파일이 잠기지 않은) 때에만 호출해야 한다.
public final class DbFingerprint {

    private DbFingerprint() {}

    public static String compute(File dbFile, File h2Jar, String javaExecutable) throws IOException {
        if (h2Jar == null || !h2Jar.isFile()) {
            throw new IOException("DB 내용을 비교하는 데 필요한 h2.jar를 찾을 수 없습니다");
        }
        String path = dbFile.getAbsolutePath().replace('\\', '/');
        if (path.endsWith(".mv.db")) path = path.substring(0, path.length() - ".mv.db".length());

        File script = File.createTempFile("attendance-fingerprint", ".sql");
        try {
            // IFEXISTS: 파일이 없을 때 빈 DB를 새로 만들어 버리지 않도록
            Process p = new ProcessBuilder(javaExecutable, "-cp", h2Jar.getAbsolutePath(), "org.h2.tools.Script",
                    "-url", "jdbc:h2:file:" + path + ";IFEXISTS=TRUE", "-user", "sa", "-password", "",
                    "-script", script.getAbsolutePath(), "-options", "NOPASSWORDS", "NOSETTINGS")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("DB 내용 확인이 시간 안에 끝나지 않았습니다");
            }
            if (p.exitValue() != 0) {
                throw new IOException("DB 내용을 확인하지 못했습니다: " + output.trim());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(script.toPath())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("DB 내용 확인이 중단되었습니다");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            script.delete();
        }
    }
}
