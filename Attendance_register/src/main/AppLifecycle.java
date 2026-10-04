package main;

import server.ServerLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;

// 창의 [X]를 눌렀을 때의 동작을 "백그라운드로 숨기기"와 "완전히 종료" 중에서 고르게 한다.
//
// 이 프로그램은 PC 안에서 서버도 함께 돌리고, Android 앱은 그 서버에 연결해서 쓴다. 창을 닫으면 서버도 같이
// 꺼져서 폰이 연결되지 않으므로, 창만 숨기고 서버는 계속 돌리는 선택지를 준다.
//  - 백그라운드 실행: 창을 숨겨서 작업 표시줄에도 안 보이게 하고, 알림 영역(시스템 트레이)에 아이콘을 둔다.
//    아이콘을 더블클릭하거나 [열기]를 누르면 창이 다시 나타나고, [완전히 종료]로 끝낼 수 있다.
//  - 완전히 종료: 서버를 포함해 프로그램 전체를 끝낸다 (종료 훅이 저장/백업/동기화를 수행).
// 선택을 기억해 두면 다음부터 묻지 않고, 설정 메뉴에서 바꿀 수 있다.
// 숨겨진 상태에서 프로그램을 또 실행하면(중복 실행) 새 프로그램이 뜨는 대신 숨겨진 창을 불러온다.
public final class AppLifecycle {

    public enum CloseChoice { HIDE, EXIT, CANCEL }

    private static final String PREF_FILE = "ui-prefs.properties";
    private static final String PREF_KEY = "close.action"; // ask | hide | exit
    private static final String SHOW_SIGNAL_FILE = "show.signal";
    // 설치 프로그램이 업데이트/제거 전에 실행 중인 프로그램을 정상 종료(저장, 백업, 동기화 포함)시키려고 만드는 신호
    private static final String EXIT_SIGNAL_FILE = "exit.signal";

    // 이 프로그램(JVM)이 시작된 시각. 파일 시각의 정밀도(초 단위로 내려가는 파일 시스템)를 감안해 1초 여유를 둠
    private static final long STARTED_AT_MILLIS = ProcessHandle.current().info().startInstant()
            .map(java.time.Instant::toEpochMilli).orElse(System.currentTimeMillis()) - 1000;

    private static JFrame current;
    private static TrayIcon trayIcon;
    private static boolean trayHintShown = false;
    private static Timer signalTimer;

    // 시험용: 대화상자 대신 선택을 정해주거나, 실제 종료 대신 다른 동작을 하게 함
    static Supplier<CloseChoice> chooserOverride;
    static Runnable exitAction = () -> System.exit(0);

    private AppLifecycle() {}

    // ---------------------------------------------------------------- 창 등록

    // 로그인 창과 메인 창이 만들어질 때 호출. [X]를 눌러도 바로 닫히지 않고 선택을 거치게 한다
    public static void register(JFrame frame) {
        current = frame;
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose(frame);
            }
        });
    }

    static void requestClose(JFrame frame) {
        CloseChoice choice = switch (getClosePreference()) {
            case "hide" -> CloseChoice.HIDE;
            case "exit" -> CloseChoice.EXIT;
            default -> chooserOverride != null ? chooserOverride.get() : askUser(frame);
        };
        switch (choice) {
            case HIDE -> hide(frame);
            case EXIT -> exit();
            case CANCEL -> { /* 아무것도 하지 않음 */ }
        }
    }

    // ---------------------------------------------------------------- 선택 창

    private static CloseChoice askUser(Component parent) {
        boolean trayOk = SystemTray.isSupported();
        String nl = System.lineSeparator();
        JCheckBox remember = new JCheckBox("선택을 기억하고 다음부터 묻지 않기 (설정 메뉴에서 바꿀 수 있습니다)");
        remember.setFocusPainted(false);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("<html><div style='width:430px'>창을 닫으면 어떻게 할까요?<br><br>"
                + "<b>" + (trayOk ? "백그라운드로 실행" : "최소화") + "</b> — "
                + (trayOk
                    ? "창을 숨기고(작업 표시줄에서도 안 보입니다) <b>서버는 계속 실행</b>합니다. "
                      + "Android 앱이 계속 연결됩니다. 화면 오른쪽 아래 알림 영역의 아이콘으로 다시 열 수 있습니다."
                    : "알림 영역을 쓸 수 없어서 창만 작업 표시줄로 내립니다. 서버는 계속 실행됩니다.")
                + "<br><br><b>완전히 종료</b> — 서버를 포함해 프로그램을 끝냅니다. "
                + "Android 앱이 연결되지 않게 됩니다.</div></html>"), BorderLayout.CENTER);
        panel.add(remember, BorderLayout.SOUTH);

        String[] options = { trayOk ? "백그라운드로 실행" : "최소화", "완전히 종료", "취소" };
        int result = JOptionPane.showOptionDialog(parent, panel, "프로그램 닫기",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        CloseChoice choice = switch (result) {
            case 0 -> CloseChoice.HIDE;
            case 1 -> CloseChoice.EXIT;
            default -> CloseChoice.CANCEL; // 취소 버튼, 창 닫기(X)
        };
        if (remember.isSelected() && choice != CloseChoice.CANCEL) {
            setClosePreference(choice == CloseChoice.HIDE ? "hide" : "exit");
        }
        return choice;
    }

    // 설정 메뉴에서 "닫기 버튼 동작"을 바꿀 때 사용
    public static void showClosePreferenceDialog(Component parent) {
        String current = getClosePreference();
        String[] options = { "매번 묻기", "항상 백그라운드로 실행", "항상 완전히 종료", "취소" };
        int initial = switch (current) { case "hide" -> 1; case "exit" -> 2; default -> 0; };
        int result = JOptionPane.showOptionDialog(parent,
                "창의 [X]를 눌렀을 때의 동작을 정합니다." + System.lineSeparator()
                        + "현재: " + options[initial],
                "닫기 버튼 동작", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[initial]);
        switch (result) {
            case 0 -> setClosePreference("ask");
            case 1 -> setClosePreference("hide");
            case 2 -> setClosePreference("exit");
            default -> { /* 취소 */ }
        }
    }

    // ---------------------------------------------------------------- 숨기기 / 다시 열기 / 종료

    static void hide(JFrame frame) {
        if (SystemTray.isSupported() && installTray()) {
            frame.setVisible(false);
            if (!trayHintShown && trayIcon != null) {
                trayHintShown = true;
                trayIcon.displayMessage("학원 출석 관리", "백그라운드에서 실행 중입니다. Android 앱은 계속 연결할 수 있습니다."
                        + " 아이콘을 더블클릭하면 창이 열립니다.", TrayIcon.MessageType.INFO);
            }
        } else {
            frame.setExtendedState(Frame.ICONIFIED); // 알림 영역을 못 쓰면 작업 표시줄로 내림 (서버는 계속 실행)
        }
    }

    // 숨겨진 창을 다시 보여줌 (알림 영역 아이콘, 또는 프로그램을 또 실행했을 때)
    public static void showWindow() {
        SwingUtilities.invokeLater(() -> {
            if (current == null) return;
            removeTray();
            current.setVisible(true);
            current.setExtendedState(Frame.NORMAL);
            // Windows는 백그라운드 프로그램의 창을 앞으로 가져오는 것을 막는 경우가 있어서 잠깐 "항상 위"로 올렸다 내림
            current.setAlwaysOnTop(true);
            current.toFront();
            current.requestFocus();
            current.setAlwaysOnTop(false);
        });
    }

    // 자동 실행(--background)에서 자동 로그인에 성공했을 때 사용: 한 번도 보여주지 않은 메인 창을 숨긴 상태로 두고 알림 영역 아이콘을 띄움
    public static void hideCurrent() {
        SwingUtilities.invokeLater(() -> {
            if (current != null) hide(current);
        });
    }

    static void exit() {
        removeTray();
        exitAction.run();
    }

    // ---------------------------------------------------------------- 알림 영역(트레이)

    private static boolean installTray() {
        if (trayIcon != null) return true;
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("열기");
            open.addActionListener(e -> showWindow());
            MenuItem quit = new MenuItem("완전히 종료");
            quit.addActionListener(e -> exit());
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);

            TrayIcon icon = new TrayIcon(drawIcon(), "학원 출석 관리 (백그라운드 실행 중)", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> showWindow()); // 더블클릭
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
            return true;
        } catch (AWTException | RuntimeException e) {
            System.err.println("알림 영역 아이콘을 만들지 못했습니다: " + e.getMessage());
            return false;
        }
    }

    private static void removeTray() {
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (RuntimeException ignored) {
                // 이미 없어졌어도 상관없음
            }
            trayIcon = null;
        }
    }

    static boolean isTrayInstalled() {
        return trayIcon != null;
    }

    // 파란 바탕에 흰 체크 표시 (별도 이미지 파일 없이 코드로 그림)
    private static Image drawIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(37, 99, 235));
        g.fillRoundRect(2, 2, 60, 60, 18, 18);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[] { 16, 28, 48 }, new int[] { 34, 46, 20 }, 3);
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- 중복 실행 -> 숨겨진 창 불러오기

    // 이미 실행 중인 프로그램에게 "창을 보여달라"고 알림 (파일을 하나 만들어 두면 실행 중인 쪽이 확인함).
    // 별도 포트를 열지 않아도 되는 가장 단순한 방법
    public static void requestShow() {
        try {
            new File(ServerLauncher.getDataDir(), SHOW_SIGNAL_FILE).createNewFile();
        } catch (IOException e) {
            System.err.println("실행 중인 창에 알리지 못했습니다: " + e.getMessage());
        }
    }

    // 실행 중인 쪽에서 주기적으로 알림 파일을 확인
    public static void startSignalWatcher() {
        if (signalTimer != null) return;
        File signal = new File(ServerLauncher.getDataDir(), SHOW_SIGNAL_FILE);
        File exitSignal = new File(ServerLauncher.getDataDir(), EXIT_SIGNAL_FILE);
        // 이전 실행에서 남은 알림은 무시한다. 단, 이 프로그램이 시작된 "이후"에 만들어진 것은 방금 온 요청이므로 지우면 안 된다
        // (설치 프로그램이 앱이 막 켜지는 순간에 종료를 요청하면 그 신호를 지워서 요청이 사라지는 경쟁 상태가 있었음)
        deleteIfOlderThanStart(signal);
        deleteIfOlderThanStart(exitSignal);
        signalTimer = new Timer(700, e -> {
            if (exitSignal.exists() && exitSignal.delete()) exit(); // 종료 훅이 저장/백업/동기화를 수행
            else if (signal.exists() && signal.delete()) showWindow();
        });
        signalTimer.setRepeats(true);
        signalTimer.start();
    }

    // 프로그램이 시작되기 전에 만들어진 신호 파일만 삭제
    private static void deleteIfOlderThanStart(File file) {
        if (file.exists() && file.lastModified() < STARTED_AT_MILLIS) file.delete();
    }

    // ---------------------------------------------------------------- 선택 저장

    public static String getClosePreference() {
        Properties p = loadPrefs();
        String v = p.getProperty(PREF_KEY, "ask");
        return v.equals("hide") || v.equals("exit") ? v : "ask";
    }

    static void setClosePreference(String value) {
        setPref(PREF_KEY, value);
    }

    // ui-prefs.properties의 다른 항목(예: AutoStart의 autostart)도 같은 파일에 함께 저장
    static String getPref(String key, String def) {
        return loadPrefs().getProperty(key, def);
    }

    static synchronized void setPref(String key, String value) {
        Properties p = loadPrefs();
        p.setProperty(key, value);
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(prefsFile()), StandardCharsets.UTF_8)) {
            p.store(out, "UI preferences");
        } catch (IOException e) {
            System.err.println("설정을 저장하지 못했습니다: " + e.getMessage());
        }
    }

    private static Properties loadPrefs() {
        Properties p = new Properties();
        File f = prefsFile();
        if (f.isFile()) {
            try (InputStreamReader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException ignored) {
                // 못 읽으면 기본값("ask")
            }
        }
        return p;
    }

    private static File prefsFile() {
        return new File(ServerLauncher.getDataDir(), PREF_FILE);
    }
}
