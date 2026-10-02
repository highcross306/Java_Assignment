package setup;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

// 설치/제거 마법사 화면. 실제 작업은 Installer/Uninstaller가 하고, 여기서는 옵션을 받고 진행 상황을 보여준다.
final class SetupWizard {

    private SetupWizard() {}

    private static final Font BASE = new Font("맑은 고딕", Font.PLAIN, 13);
    private static final Color BLUE = new Color(37, 99, 235);

    // ================================================================ 설치

    static void runInstall(Options o) {
        SwingUtilities.invokeLater(() -> new InstallFrame(o).setVisible(true));
    }

    private static final class InstallFrame extends JFrame {
        private final Options o;
        private final CardLayout cards = new CardLayout();
        private final JPanel cardPanel = new JPanel(cards);
        private final JButton nextButton = new JButton("다음");
        private final JButton cancelButton = new JButton("취소");
        private String page = "welcome";
        private boolean installing = false;
        private Installer.Result result;

        // 옵션 화면
        private final JTextField dirField = new JTextField(46);
        private final JCheckBox desktopBox = new JCheckBox("바탕화면에 바로가기 만들기");
        private final JCheckBox startMenuBox = new JCheckBox("시작 메뉴에 등록");
        private final JCheckBox autostartBox = new JCheckBox("Windows를 켤 때 자동으로 실행 (백그라운드)");
        private final JCheckBox firewallBox = new JCheckBox("방화벽 자동 설정 (권장 - 관리자 권한 요청이 한 번 나옵니다)");
        private final JCheckBox androidBox = new JCheckBox("Android 앱 설치 (USB로 연결된 폰에 설치하거나, QR 코드로 안내)");

        // 진행 화면
        private final JLabel stageLabel = new JLabel("준비 중...");
        private final JProgressBar bar = new JProgressBar(0, 100);
        private final JTextArea logArea = new JTextArea();

        // 완료 화면
        private final JPanel donePanel = new JPanel(new BorderLayout(0, 10));
        private final JCheckBox launchBox = new JCheckBox("설치가 끝나면 프로그램 실행");

        InstallFrame(Options o) {
            super("출석 관리 프로그램 설치");
            this.o = o;
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    onCancel();
                }
            });
            setSize(680, 540);
            setLocationRelativeTo(null);
            setResizable(false);

            cardPanel.add(welcomePage(), "welcome");
            cardPanel.add(optionsPage(), "options");
            cardPanel.add(progressPage(), "progress");
            cardPanel.add(donePanel, "done");

            nextButton.addActionListener(e -> onNext());
            cancelButton.addActionListener(e -> onCancel());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            buttons.add(nextButton);
            buttons.add(cancelButton);

            add(cardPanel, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            applyFonts(getContentPane());
        }

        private JPanel welcomePage() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createEmptyBorder(28, 32, 12, 32));
            JLabel title = new JLabel("출석 관리 프로그램 설치");
            title.setFont(new Font("맑은 고딕", Font.BOLD, 22));
            title.setForeground(BLUE);
            JLabel body = new JLabel("<html><div style='width:560px;line-height:1.5'>"
                    + "이 프로그램은 학원의 출석을 PC와 Android 폰에서 관리합니다.<br><br>"
                    + "<b>설치되는 것</b><br>"
                    + "· 데스크톱 프로그램과 내장 서버 (Java가 따로 필요 없습니다)<br>"
                    + "· 바탕화면/시작 메뉴 바로가기, 제거 프로그램<br>"
                    + "· 폰이 PC에 접속할 수 있도록 Windows 방화벽 설정<br>"
                    + "· Android 앱 (USB로 연결된 폰에 자동 설치, 또는 QR 코드로 안내)<br><br>"
                    + "<b>알아두세요</b><br>"
                    + "· 폰은 이 PC와 <b>같은 Wi-Fi</b>에 연결되어 있어야 합니다.<br>"
                    + "· 방화벽 설정을 위해 <b>관리자 권한 요청</b>이 한 번 나옵니다. [예]를 눌러주세요.<br>"
                    + "· 이미 설치되어 있다면 출석 데이터는 그대로 두고 프로그램만 새 버전으로 바뀝니다.<br><br>"
                    + "[다음]을 눌러 계속하세요."
                    + "</div></html>");
            p.add(title, BorderLayout.NORTH);
            p.add(body, BorderLayout.CENTER);
            return p;
        }

        private JPanel optionsPage() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(BorderFactory.createEmptyBorder(24, 32, 12, 32));

            JLabel title = new JLabel("설치 옵션");
            title.setFont(new Font("맑은 고딕", Font.BOLD, 18));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(title);
            p.add(Box.createVerticalStrut(14));

            JLabel dirLabel = new JLabel("설치 위치");
            dirLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            dirField.setText(o.installDir.toString());
            JButton browse = new JButton("찾아보기");
            browse.addActionListener(e -> {
                JFileChooser fc = new JFileChooser(o.installDir.getParent() == null ? null : o.installDir.getParent().toFile());
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    dirField.setText(fc.getSelectedFile().toPath().resolve("AttendanceManager").toString());
                }
            });
            JPanel dirRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            dirRow.add(dirField);
            dirRow.add(browse);
            dirRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            // BoxLayout이 이 줄을 세로로 늘리지 않도록 높이를 고정 (안 하면 아래 항목들과 사이에 큰 빈 공간이 생김)
            dirRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, dirRow.getPreferredSize().height));
            p.add(dirLabel);
            p.add(dirRow);
            p.add(Box.createVerticalStrut(16));

            for (JCheckBox b : new JCheckBox[] { desktopBox, startMenuBox, autostartBox, firewallBox, androidBox }) {
                b.setAlignmentX(Component.LEFT_ALIGNMENT);
                b.setFocusPainted(false);
                p.add(b);
                p.add(Box.createVerticalStrut(6));
            }
            desktopBox.setSelected(o.desktopShortcut);
            startMenuBox.setSelected(o.startMenu);
            autostartBox.setSelected(o.autostart);
            firewallBox.setSelected(o.firewall);
            androidBox.setSelected(o.android);

            JLabel hint = new JLabel("<html><div style='width:560px;color:#6B7280'>"
                    + "자동 실행을 켜면 Windows를 켤 때 창 없이 알림 영역에서 실행되어, 폰이 언제든 연결할 수 있습니다."
                    + "</div></html>");
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(Box.createVerticalStrut(8));
            p.add(hint);
            return p;
        }

        private JPanel progressPage() {
            JPanel p = new JPanel(new BorderLayout(0, 10));
            p.setBorder(BorderFactory.createEmptyBorder(24, 32, 12, 32));
            JLabel title = new JLabel("설치하는 중...");
            title.setFont(new Font("맑은 고딕", Font.BOLD, 18));
            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.add(title, BorderLayout.NORTH);
            top.add(stageLabel, BorderLayout.CENTER);
            top.add(bar, BorderLayout.SOUTH);
            logArea.setEditable(false);
            logArea.setLineWrap(true);
            logArea.setWrapStyleWord(true);
            logArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            p.add(top, BorderLayout.NORTH);
            p.add(new JScrollPane(logArea), BorderLayout.CENTER);
            return p;
        }

        // ---------------------------------------------------------------- 이동

        private void onNext() {
            switch (page) {
                case "welcome" -> show("options", "설치");
                case "options" -> startInstall();
                case "done" -> finish();
                default -> { }
            }
        }

        private void show(String name, String nextText) {
            page = name;
            cards.show(cardPanel, name);
            nextButton.setText(nextText);
        }

        private void onCancel() {
            if (installing) {
                JOptionPane.showMessageDialog(this, "설치하는 중에는 취소할 수 없습니다. 잠시만 기다려주세요.", "출석 관리 프로그램 설치", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (page.equals("done")) {
                finish();
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "설치를 취소할까요?", "출석 관리 프로그램 설치",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        }

        private void startInstall() {
            String dir = dirField.getText().trim();
            if (dir.isEmpty()) {
                JOptionPane.showMessageDialog(this, "설치 위치를 입력해주세요.", "설치 옵션", JOptionPane.WARNING_MESSAGE);
                return;
            }
            o.installDir = Path.of(dir);
            o.desktopShortcut = desktopBox.isSelected();
            o.startMenu = startMenuBox.isSelected();
            o.autostart = autostartBox.isSelected();
            o.firewall = firewallBox.isSelected();
            o.android = androidBox.isSelected();

            installing = true;
            nextButton.setEnabled(false);
            cancelButton.setEnabled(false);
            show("progress", "다음");
            nextButton.setEnabled(false);

            new SwingWorker<Installer.Result, Object[]>() {
                @Override
                protected Installer.Result doInBackground() {
                    return new Installer(o, new Installer.Listener() {
                        @Override
                        public void stage(int index, int total, String title) {
                            publish(new Object[] { "stage", index, total, title });
                        }

                        @Override
                        public void log(String message) {
                            publish(new Object[] { "log", message });
                        }
                    }).run();
                }

                @Override
                protected void process(java.util.List<Object[]> chunks) {
                    for (Object[] c : chunks) {
                        if (c[0].equals("stage")) {
                            int index = (int) c[1];
                            int total = (int) c[2];
                            stageLabel.setText("(" + index + "/" + total + ") " + c[3]);
                            bar.setValue(Math.max(1, (index - 1) * 100 / total));
                        } else {
                            logArea.append(c[1] + System.lineSeparator());
                            logArea.setCaretPosition(logArea.getDocument().getLength());
                        }
                    }
                }

                @Override
                protected void done() {
                    installing = false;
                    try {
                        result = get();
                    } catch (Exception e) {
                        result = new Installer.Result();
                        result.success = false;
                        result.failure = "예상하지 못한 오류: " + e;
                    }
                    bar.setValue(100);
                    buildDonePage();
                    cancelButton.setVisible(false);
                    nextButton.setEnabled(true);
                    show("done", "마침");
                }
            }.execute();
        }

        // ---------------------------------------------------------------- 완료 화면

        private void buildDonePage() {
            donePanel.removeAll();
            donePanel.setBorder(BorderFactory.createEmptyBorder(24, 32, 12, 32));
            JLabel title = new JLabel(result.success ? "설치가 완료되었습니다" : "설치하지 못했습니다");
            title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
            title.setForeground(result.success ? new Color(21, 128, 61) : new Color(185, 28, 28));

            StringBuilder html = new StringBuilder("<html><div style='width:580px'>");
            if (result.success) {
                html.append("설치 위치: ").append(escape(result.installDir.toString())).append("<br>");
                html.append("서버 포트: ").append(result.port).append("<br>");
                if (!result.warnings.isEmpty()) {
                    html.append("<br><b style='color:#B45309'>확인이 필요한 항목</b><br>");
                    for (String w : result.warnings) html.append("· ").append(escape(w)).append("<br>");
                }
            } else {
                html.append(escape(result.failure == null ? "알 수 없는 오류" : result.failure));
            }
            html.append("</div></html>");

            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.add(title, BorderLayout.NORTH);
            top.add(new JLabel(html.toString()), BorderLayout.CENTER);
            donePanel.add(top, BorderLayout.NORTH);

            if (result.success) {
                JPanel center = new JPanel(new BorderLayout(0, 6));
                if (result.androidUrl != null) {
                    center.add(androidPanel(), BorderLayout.CENTER);
                } else if (!result.androidMessage.isEmpty()) {
                    center.add(new JLabel("<html><div style='width:580px'><b>Android 앱</b><br>" + escape(result.androidMessage) + "</div></html>"), BorderLayout.NORTH);
                }
                donePanel.add(center, BorderLayout.CENTER);
                launchBox.setSelected(o.launchAfter);
                donePanel.add(launchBox, BorderLayout.SOUTH);
            } else {
                JTextArea details = new JTextArea(logArea.getText());
                details.setEditable(false);
                details.setLineWrap(true);
                donePanel.add(new JScrollPane(details), BorderLayout.CENTER);
            }
            applyFonts(donePanel);
            donePanel.revalidate();
            donePanel.repaint();
        }

        // 폰에서 내려받아 설치하도록 QR 코드와 주소를 보여줌
        private JPanel androidPanel() {
            JPanel p = new JPanel(new BorderLayout(14, 0));
            p.setBorder(BorderFactory.createTitledBorder("Android 앱 설치"));
            try {
                p.add(new JLabel(new ImageIcon(AndroidInstall.qr(result.androidUrl, 170))), BorderLayout.WEST);
            } catch (java.io.IOException e) {
                p.add(new JLabel("(QR 코드를 만들지 못했습니다)"), BorderLayout.WEST);
            }
            JTextField url = new JTextField(result.androidUrl);
            url.setEditable(false);
            JPanel text = new JPanel(new BorderLayout(0, 6));
            text.add(new JLabel("<html><div style='width:290px'>" + escape(result.androidMessage)
                    + "<br><br>설치 파일은 <b>이 창이 열려 있는 동안</b>만 내려받을 수 있습니다. "
                    + "내려받은 뒤 '출처를 알 수 없는 앱 설치'를 허용해야 할 수 있습니다.</div></html>"), BorderLayout.CENTER);
            text.add(url, BorderLayout.SOUTH);
            p.add(text, BorderLayout.CENTER);
            return p;
        }

        private void finish() {
            if (result != null && result.apkServer != null) result.apkServer.close(); // 프로그램이 같은 포트를 쓰므로 먼저 닫음
            if (result != null && result.success && launchBox.isSelected()) {
                try {
                    Installer.launchApp(result.installDir);
                } catch (java.io.IOException e) {
                    JOptionPane.showMessageDialog(this, "프로그램을 실행하지 못했습니다: " + e.getMessage(), "출석 관리 프로그램 설치", JOptionPane.WARNING_MESSAGE);
                }
            }
            System.exit(0);
        }
    }

    // ================================================================ 제거

    static void runUninstall(Options o) {
        SwingUtilities.invokeLater(() -> {
            Path dir = Uninstaller.locateInstallDir(o);
            o.installDir = dir;

            JCheckBox deleteData = new JCheckBox("출석 데이터(DB, 백업, 구글 로그인 정보)도 함께 삭제");
            deleteData.setFont(BASE);
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.add(new JLabel("<html><div style='width:420px'>출석 관리 프로그램을 제거할까요?<br><br>"
                    + "프로그램 파일, 바로가기, 방화벽 규칙이 삭제됩니다.<br>"
                    + "<b>출석 데이터는 기본적으로 남겨 둡니다</b> (나중에 다시 설치하면 이어서 쓸 수 있습니다).</div></html>"), BorderLayout.CENTER);
            panel.add(deleteData, BorderLayout.SOUTH);

            if (JOptionPane.showConfirmDialog(null, panel, "출석 관리 프로그램 제거", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                System.exit(0);
                return;
            }
            if (deleteData.isSelected()
                    && JOptionPane.showConfirmDialog(null, "출석 데이터까지 삭제하면 되돌릴 수 없습니다. 정말 삭제할까요?",
                    "데이터 삭제 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                deleteData.setSelected(false);
            }
            o.deleteUserData = deleteData.isSelected();

            StringBuilder log = new StringBuilder();
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return new Uninstaller(o, new Installer.Listener() {
                        @Override
                        public void stage(int index, int total, String title) {
                            log.append("(").append(index).append("/").append(total).append(") ").append(title).append(System.lineSeparator());
                        }

                        @Override
                        public void log(String message) {
                            log.append("    ").append(message).append(System.lineSeparator());
                        }
                    }).run(dir);
                }

                @Override
                protected void done() {
                    boolean ok;
                    try {
                        ok = get();
                    } catch (Exception e) {
                        ok = false;
                        log.append(e).append(System.lineSeparator());
                    }
                    JTextArea area = new JTextArea(log.toString(), 14, 56);
                    area.setEditable(false);
                    area.setLineWrap(true);
                    JOptionPane.showMessageDialog(null, new JScrollPane(area),
                            ok ? "제거가 완료되었습니다" : "제거하지 못했습니다",
                            ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                    System.exit(ok ? 0 : 1);
                }
            }.execute();
        }

        );
    }

    // ================================================================ 공통

    private static void applyFonts(Component c) {
        if (c.getFont() == null || !c.getFont().getFamily().equals(BASE.getFamily())) {
            if (!(c instanceof JTextArea)) c.setFont(BASE);
        }
        if (c instanceof Container cc) {
            for (Component k : cc.getComponents()) {
                if (k instanceof JLabel lbl && lbl.getFont() != null && lbl.getFont().isBold()) continue;
                applyFonts(k);
            }
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
