package migrate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

// 이 PC의 MariaDB(또는 MySQL) Windows 서비스가 꺼져 있을 때 찾아서 켜 주는 도우미.
// 켜는 것은 관리자 권한이 필요해서 UAC 확인 창이 한 번 뜬다.
public final class ServiceHelper {

    private ServiceHelper() {}

    // 꺼져 있는 MariaDB/MySQL 서비스 이름, 없으면 null.
    // sc query 출력의 SERVICE_NAME/STATE 항목은 한국어 Windows에서도 영어 그대로이고, 상태는 숫자(1=중지, 4=실행 중)로 판단한다
    public static String findStoppedDatabaseService() {
        try {
            Process p = new ProcessBuilder("sc", "query", "type=", "service", "state=", "all").redirectErrorStream(true).start();
            String name = null;
            String found = null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.startsWith("SERVICE_NAME:")) {
                        name = t.substring("SERVICE_NAME:".length()).trim();
                    } else if (t.startsWith("STATE") && name != null && found == null) {
                        String lower = name.toLowerCase();
                        if ((lower.contains("mariadb") || lower.contains("mysql")) && t.matches("STATE\\s*:\\s*1\\b.*")) {
                            found = name;
                        }
                    }
                }
            }
            p.waitFor();
            return found;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // UAC 확인 후 서비스 시작. 사용자가 거절하거나 실패하면 false
    public static boolean startWithElevation(String serviceName) {
        if (!serviceName.matches("[A-Za-z0-9_.\\-]+")) return false;
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Start-Process sc.exe -ArgumentList 'start','" + serviceName + "' -Verb RunAs -WindowStyle Hidden -Wait")
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
