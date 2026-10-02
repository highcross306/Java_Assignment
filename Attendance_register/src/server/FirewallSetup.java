package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Android 앱이 이 PC의 서버(TCP)와 자동 검색 응답(UDP)에 닿으려면 Windows 방화벽의
// 인바운드 규칙이 필요함. 규칙이 없으면 첫 실행 때 뜨는 "허용" 팝업을 사용자가 취소하는 순간
// 앱이 조용히 연결 불가가 되므로, 앱이 직접 규칙의 유무를 확인하고 없으면 관리자 권한(UAC)으로
// 한 번만 등록한다. Windows가 아니면 아무것도 하지 않음
public class FirewallSetup {

    private static final String TCP_RULE = "Attendance Server (TCP)";
    private static final String UDP_RULE = "Attendance Discovery (UDP)";

    public static boolean isSupported() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isConfigured() {
        if (!isSupported()) return true;
        return ruleExists(TCP_RULE) && ruleExists(UDP_RULE);
    }

    // 관리자 권한 요청(UAC)을 띄워서 규칙을 등록. 사용자가 거부하면 false
    public static boolean configure(int tcpPort, int udpPort) {
        if (!isSupported()) return true;
        // 따옴표 중첩 문제를 피하려고, 관리자 권한으로 실행할 명령은 Base64(UTF-16LE)로
        // 인코딩해서 -EncodedCommand로 넘긴다
        String adminScript =
                "netsh advfirewall firewall add rule name='" + TCP_RULE
                + "' dir=in action=allow protocol=TCP localport=" + tcpPort + " profile=any; "
                + "netsh advfirewall firewall add rule name='" + UDP_RULE
                + "' dir=in action=allow protocol=UDP localport=" + udpPort + " profile=any";
        String encoded = Base64.getEncoder().encodeToString(adminScript.getBytes(StandardCharsets.UTF_16LE));
        String ps = "Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden "
                + "-ArgumentList '-NoProfile','-EncodedCommand','" + encoded + "'";
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            p.waitFor();
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return isConfigured();
    }

    private static boolean ruleExists(String name) {
        try {
            Process p = new ProcessBuilder("netsh", "advfirewall", "firewall", "show", "rule",
                    "name=" + name).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
