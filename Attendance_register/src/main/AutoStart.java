package main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Windows를 켤 때 프로그램이 백그라운드(--background)로 자동 실행되도록 등록한다.
//
// 등록 위치는 설치 프로그램과 같은 "현재 사용자" 시작 프로그램 목록(HKCU\...\Run, 값 이름 AttendanceManager)이라
// 관리자 권한이 필요 없고, 설치 프로그램/제거 프로그램이 등록하거나 지운 것과 서로 맞물린다.
// 켜짐/꺼짐 선택은 ui-prefs.properties의 autostart(on|off)에 기억하고, 설정 메뉴에서 바꿀 수 있다.
//  - 처음 실행(선택 기록 없음): 설치 프로그램으로 설치했으면 설치할 때 고른 대로 따르고,
//    설치 프로그램 없이 실행 파일을 바로 실행했으면 기본으로 켠다.
//  - 켜짐이면 실행할 때마다 다시 등록해서, 프로그램 폴더를 옮겨도 경로가 맞게 유지된다.
// 개발 중(javac로 직접 실행)에는 실행 파일 경로가 없으므로 아무것도 하지 않는다.
public final class AutoStart {

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String UNINSTALL_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\AttendanceManager";
    private static final String VALUE_NAME = "AttendanceManager";
    private static final String PREF_KEY = "autostart"; // on | off

    private AutoStart() {}

    // 실행 파일(jpackage가 만든 AttendanceManager.exe)로 실행됐을 때만 자동 실행을 등록할 수 있음
    public static boolean isAvailable() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") && appExe() != null;
    }

    public static boolean isEnabled() {
        return "on".equals(AppLifecycle.getPref(PREF_KEY, null));
    }

    // 프로그램 시작 시 백그라운드에서 호출 (reg.exe 실행에 시간이 조금 걸림)
    public static void applyOnStartup() {
        if (!isAvailable()) return;
        String pref = AppLifecycle.getPref(PREF_KEY, null);
        if (pref == null) {
            boolean installedBySetup = regExists(UNINSTALL_KEY, null);
            pref = !installedBySetup || regExists(RUN_KEY, VALUE_NAME) ? "on" : "off";
            AppLifecycle.setPref(PREF_KEY, pref);
        }
        if (pref.equals("on")) register();
    }

    // 설정 메뉴에서 켜고 끌 때 사용. 성공하면 true
    public static boolean setEnabled(boolean enabled) {
        if (!isAvailable()) return false;
        boolean ok = enabled ? register() : unregister();
        if (ok) AppLifecycle.setPref(PREF_KEY, enabled ? "on" : "off");
        return ok;
    }

    private static boolean register() {
        // 값 안의 큰따옴표는 reg.exe에 \"로 넘긴다 (경로에 공백이 있어도 실행 파일 경로가 하나로 읽히도록)
        String command = "\\\"" + appExe() + "\\\" --background";
        return reg("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", command, "/f") == 0;
    }

    private static boolean unregister() {
        if (!regExists(RUN_KEY, VALUE_NAME)) return true;
        return reg("delete", RUN_KEY, "/v", VALUE_NAME, "/f") == 0;
    }

    private static boolean regExists(String key, String valueName) {
        return valueName == null ? reg("query", key) == 0 : reg("query", key, "/v", valueName) == 0;
    }

    private static String appExe() {
        String path = System.getProperty("jpackage.app-path");
        return path != null && new File(path).isFile() ? path : null;
    }

    private static int reg(String... args) {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(new File(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32\\reg.exe").getPath());
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(new ByteArrayOutputStream()); // 출력을 비워 줘야 멈추지 않음
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (IOException e) {
            System.err.println("자동 실행 설정을 바꾸지 못했습니다: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
