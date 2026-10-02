package setup;

// 출석 관리 프로그램 설치 프로그램.
//   (옵션 없음)            설치 마법사
//   --uninstall            제거 (설치된 폴더의 runtime으로 실행되며 앱 목록의 [제거]가 이 명령을 부름)
//   --silent               화면 없이 설치/제거 (시험, 여러 PC 일괄 설치용)
//   --install-dir <경로>  --no-desktop-shortcut  --no-start-menu  --autostart  --no-firewall  --no-android  --no-launch  --port <번호>
//   --delete-data          (제거 시) 출석 데이터도 삭제
public final class SetupMain {

    private SetupMain() {}

    public static void main(String[] args) {
        Options o;
        try {
            o = Options.parse(args);
        } catch (RuntimeException e) {
            System.err.println("옵션 오류: " + e.getMessage());
            System.exit(2);
            return;
        }

        if (o.silent) {
            System.exit(runSilent(o) ? 0 : 1);
        } else if (o.uninstall) {
            SetupWizard.runUninstall(o);
        } else {
            SetupWizard.runInstall(o);
        }
    }

    private static boolean runSilent(Options o) {
        Installer.Listener console = new Installer.Listener() {
            @Override
            public void stage(int index, int total, String title) {
                System.out.println("[" + index + "/" + total + "] " + title);
            }

            @Override
            public void log(String message) {
                System.out.println("    " + message);
            }
        };

        if (o.uninstall) {
            o.installDir = Uninstaller.locateInstallDir(o);
            Uninstaller u = new Uninstaller(o, console);
            boolean ok = u.run(o.installDir);
            System.out.println(ok ? "제거가 완료되었습니다." : "제거에 실패했습니다.");
            return ok;
        }

        Installer.Result r = new Installer(o, console).run();
        if (r.success) {
            System.out.println("설치 완료: " + r.installDir + " (포트 " + r.port + ")");
            for (String w : r.warnings) System.out.println("주의: " + w);
            if (!r.androidMessage.isEmpty()) System.out.println("Android: " + r.androidMessage);
            if (o.launchAfter) {
                try {
                    Installer.launchApp(r.installDir);
                } catch (java.io.IOException e) {
                    System.out.println("프로그램을 실행하지 못했습니다: " + e.getMessage());
                }
            }
        } else {
            System.out.println("설치 실패: " + r.failure);
        }
        return r.success;
    }
}
