package setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Windows 설정 작업(바로가기, 레지스트리, 폴더 위치)을 PowerShell로 수행하는 보조 기능.
// 스크립트는 -EncodedCommand(Base64 UTF-16LE)로 넘겨서, 한글/공백/따옴표가 들어간 경로에서도 인코딩과 인용 문제가 없게 한다.
final class Win {

    private Win() {}

    // 시험에서는 실제 "프로그램 추가/제거" 위치 대신 임시 키를 쓰도록 바꿀 수 있음 (-Dsetup.regRoot=HKCU\Software\SomeTestKey)
    static final String REG_ROOT = System.getProperty("setup.regRoot", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion");

    static final String APP_KEY_NAME = "AttendanceManager";

    // PowerShell 스크립트 실행. 성공하면 표준 출력(UTF-8), 실패하면 오류 메시지의 IOException
    static String ps(String script) throws IOException {
        String full = "$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " + script;
        String encoded = Base64.getEncoder().encodeToString(full.getBytes(StandardCharsets.UTF_16LE));
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("PowerShell 작업이 시간 안에 끝나지 않았습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("작업이 중단되었습니다");
        }
        if (p.exitValue() != 0) throw new IOException(out.trim().isEmpty() ? "PowerShell 오류 (코드 " + p.exitValue() + ")" : out.trim());
        return out.trim();
    }

    // 관리자 권한(UAC)으로 PowerShell 스크립트를 실행하고 끝날 때까지 기다림. 사용자가 거부하면 false
    static boolean runElevated(String script) {
        String full = "$ErrorActionPreference='Continue'; " + script;
        String encoded = Base64.getEncoder().encodeToString(full.getBytes(StandardCharsets.UTF_16LE));
        try {
            ps("Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden -ArgumentList '-NoProfile','-EncodedCommand','" + encoded + "'");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static String q(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    // 바탕화면/시작 메뉴 프로그램 폴더. OneDrive로 옮겨진 바탕화면도 올바르게 찾도록 Windows에 물어봄
    static Path desktopDir() throws IOException {
        String override = System.getProperty("setup.desktopDir");
        return Path.of(override != null ? override : ps("[Environment]::GetFolderPath('Desktop')"));
    }

    static Path programsDir() throws IOException {
        String override = System.getProperty("setup.programsDir");
        return Path.of(override != null ? override : ps("[Environment]::GetFolderPath('Programs')"));
    }

    static void createShortcut(Path lnk, Path target, String arguments, Path workDir, Path icon, String description) throws IOException {
        java.nio.file.Files.createDirectories(lnk.getParent());
        ps("$s=(New-Object -ComObject WScript.Shell).CreateShortcut(" + q(lnk.toString()) + "); "
                + "$s.TargetPath=" + q(target.toString()) + "; "
                + "$s.Arguments=" + q(arguments == null ? "" : arguments) + "; "
                + "$s.WorkingDirectory=" + q(workDir.toString()) + "; "
                + "$s.IconLocation=" + q(icon.toString()) + "; "
                + "$s.Description=" + q(description) + "; $s.Save()");
    }

    private static String psPath(String regKey) {
        // "HKCU\Software\X" -> "HKCU:\Software\X"
        int i = regKey.indexOf('\\');
        return regKey.substring(0, i) + ":" + regKey.substring(i);
    }

    // 주의: New-Item -Force는 이미 있는 키를 다시 만들면서 그 안의 값을 모두 지우므로, 키가 없을 때만 만든다
    static void regSetString(String key, String name, String value) throws IOException {
        ps("if (-not (Test-Path " + q(psPath(key)) + ")) { New-Item -Path " + q(psPath(key)) + " -Force | Out-Null }; "
                + "Set-ItemProperty -Path " + q(psPath(key)) + " -Name " + q(name) + " -Value " + q(value) + " -Type String");
    }

    static void regSetDword(String key, String name, int value) throws IOException {
        ps("if (-not (Test-Path " + q(psPath(key)) + ")) { New-Item -Path " + q(psPath(key)) + " -Force | Out-Null }; "
                + "Set-ItemProperty -Path " + q(psPath(key)) + " -Name " + q(name) + " -Value " + value + " -Type DWord");
    }

    static String regGet(String key, String name) throws IOException {
        return ps("$v=(Get-ItemProperty -Path " + q(psPath(key)) + " -Name " + q(name) + " -ErrorAction SilentlyContinue)." + "'" + name + "'; if ($v -ne $null) { $v }");
    }

    static void regDeleteKey(String key) throws IOException {
        ps("if (Test-Path " + q(psPath(key)) + ") { Remove-Item -Path " + q(psPath(key)) + " -Recurse -Force }");
    }

    static void regDeleteValue(String key, String name) throws IOException {
        ps("if (Test-Path " + q(psPath(key)) + ") { Remove-ItemProperty -Path " + q(psPath(key)) + " -Name " + q(name) + " -ErrorAction SilentlyContinue }");
    }

    static String uninstallKey() { return REG_ROOT + "\\Uninstall\\" + APP_KEY_NAME; }

    static String runKey() { return REG_ROOT + "\\Run"; }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    // 읽기 전용 속성을 풀어서 덮어쓰기/삭제가 가능하게 함 (파일이 없으면 아무것도 안 함)
    static void makeWritable(Path p) {
        try {
            if (java.nio.file.Files.exists(p)) java.nio.file.Files.setAttribute(p, "dos:readonly", false);
        } catch (IOException | UnsupportedOperationException ignored) {
            // 풀지 못해도 이후 복사/삭제에서 오류로 드러남
        }
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!java.nio.file.Files.exists(dir)) return;
        // 방금 종료한 프로그램의 파일 핸들이 잠깐 남아 있을 수 있어서 몇 번 재시도
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try (var walk = java.nio.file.Files.walk(dir)) {
                List<Path> all = walk.sorted(java.util.Comparator.reverseOrder()).toList();
                for (Path p : all) {
                    makeWritable(p);
                    java.nio.file.Files.deleteIfExists(p);
                }
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw new IOException("삭제하지 못했습니다 (사용 중인 파일이 있을 수 있습니다): " + dir + " - " + (last == null ? "" : last.getMessage()));
    }
}
