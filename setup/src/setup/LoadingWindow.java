package setup;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// 설치 마법사가 뜨기 전(Java 시작 + 화면 구성, 몇 초)에 보여주는 "준비 중" 창.
// 데스크톱 앱이 시작할 때 띄우는 "내장 서버 시작 중..." 창과 같은 모양.
//
// 단일 파일 설치본(AttendanceSetup.exe, setup\bootstrap\Bootstrap.cs)은 Java가 뜨기 전에 압축을 푸는 동안
// 같은 모양의 준비 창을 보여주고 있음. 이 창이 화면에 뜨면 환경 변수 ATTENDANCE_SETUP_READY가
// 가리키는 파일을 만들어서 그 창을 닫게 함 -> 두 창이 이어서 보임
final class LoadingWindow {

    static final String MESSAGE = "설치 프로그램 준비 중...";

    private final JWindow window = new JWindow();

    private LoadingWindow() {
        JLabel label = new JLabel(MESSAGE, SwingConstants.CENTER);
        label.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(220, 6));

        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219)),
                BorderFactory.createEmptyBorder(20, 30, 20, 30)));
        panel.add(label, BorderLayout.CENTER);
        panel.add(bar, BorderLayout.SOUTH);
        window.add(panel);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
    }

    // EDT에서 호출
    static LoadingWindow show() {
        LoadingWindow w = new LoadingWindow();
        w.window.setVisible(true);
        // 바로 이어서 마법사를 만드느라 EDT가 몇 초 바쁘므로, 그 전에 지금 바로 그려 둠
        JRootPane root = w.window.getRootPane();
        root.paintImmediately(0, 0, root.getWidth(), root.getHeight());
        signalReady();
        return w;
    }

    void close() {
        window.dispose();
    }

    // 압축 푸는 동안 떠 있던 PowerShell 준비 창에 "이제 닫아도 된다"고 알림
    static void signalReady() {
        String path = System.getenv("ATTENDANCE_SETUP_READY");
        if (path == null || path.isBlank()) return;
        try {
            Files.writeString(Path.of(path), "ready");
        } catch (IOException | RuntimeException ignored) {
            // 못 만들어도 PowerShell 창은 설치 프로그램이 끝나거나 시간이 지나면 스스로 닫힘
        }
    }
}
