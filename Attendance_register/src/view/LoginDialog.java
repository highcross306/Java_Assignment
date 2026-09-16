package view;

import javax.swing.*;
import java.awt.*;
import model.DataManager;

// 프로그램 시작 시 뜨는 로그인/회원가입 창.
// 로그인(또는 회원가입)에 성공해야만 닫히고, 그 이후에 MainFrame이 뜸
public class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final Color BG = new Color(248, 249, 251);
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color GRAY_TXT = new Color(107, 114, 128);

    private boolean success = false;

    private final JTextField serverUrlField = new JTextField(16);
    private final JTextField usernameField = new JTextField(16);
    private final JPasswordField passwordField = new PlaceholderPasswordField(16, "비밀번호는 8자 이상이어야 합니다");
    private final JCheckBox rememberCheckBox = new JCheckBox("내 정보 기억");
    private final JLabel messageLabel = new JLabel(" ");

    public LoginDialog(Frame owner) {
        super(owner, "학원 출석 관리 프로그램 - 로그인", true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setBackground(BG);
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("학원 출석 관리 프로그램");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(20, 0, 8, 0));
        // 학원마다 자기 서버를 따로 운영하므로, 이전에 접속했던 서버 주소가 있으면 미리 채워줌
        if (DataManager.isServerConfigured()) {
            serverUrlField.setText(DataManager.getServerUrl()
                    .replaceFirst("^https?://", "")
                    .replaceFirst("/api$", ""));
        }

        form.add(labeled("학원 서버 주소", serverUrlField));
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        form.add(labeled("아이디", usernameField));
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        form.add(labeled("비밀번호", passwordFieldWithToggle()));

        // 이전에 "내 정보 기억"을 체크하고 로그인/회원가입에 성공한 적이 있으면
        // 아이디/비밀번호를 미리 채워줌 (로그인은 여전히 직접 눌러야 함)
        if (DataManager.isRememberMeEnabled()) {
            rememberCheckBox.setSelected(true);
            String savedUsername = DataManager.getSavedUsername();
            String savedPassword = DataManager.getSavedPassword();
            if (savedUsername != null) {
                usernameField.setText(savedUsername);
            }
            if (savedPassword != null) {
                passwordField.setText(savedPassword);
            }
        }
        rememberCheckBox.setOpaque(false);
        rememberCheckBox.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        rememberCheckBox.setForeground(GRAY_TXT);
        rememberCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        rememberCheckBox.setFocusPainted(false);
        form.add(Box.createRigidArea(new Dimension(0, 6)));
        form.add(rememberCheckBox);

        messageLabel.setForeground(RED);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

        JButton loginBtn = primaryButton("로그인");
        JButton signupBtn = secondaryButton("회원가입");

        loginBtn.addActionListener(e -> attempt(true));
        signupBtn.addActionListener(e -> attempt(false));
        passwordField.addActionListener(e -> attempt(true)); // 비밀번호 입력 후 엔터로 로그인

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        buttons.add(loginBtn);
        buttons.add(signupBtn);

        content.add(title);
        content.add(form);
        content.add(messageLabel);
        content.add(buttons);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    // 비밀번호 입력칸 오른쪽에 "보기"/"숨기기" 토글 버튼을 붙인 한 줄짜리 패널을 만듦.
    // 기본은 별표(echo char)로 가려져 있고, 버튼을 누르면 그 동안만 평문으로 보여줌
    private JPanel passwordFieldWithToggle() {
        char defaultEchoChar = passwordField.getEchoChar();
        JButton toggleBtn = new JButton("보기");
        toggleBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        toggleBtn.setFocusPainted(false);
        toggleBtn.setPreferredSize(new Dimension(52, 28));
        toggleBtn.addActionListener(e -> {
            boolean isCurrentlyHidden = passwordField.getEchoChar() != 0;
            passwordField.setEchoChar(isCurrentlyHidden ? (char) 0 : defaultEchoChar);
            toggleBtn.setText(isCurrentlyHidden ? "숨기기" : "보기");
        });

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(passwordField, BorderLayout.CENTER);
        row.add(toggleBtn, BorderLayout.EAST);
        return row;
    }

    private JPanel labeled(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        lbl.setForeground(GRAY_TXT);
        field.setPreferredSize(new Dimension(240, 32));
        panel.add(lbl, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setBackground(BLUE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 38));
        return btn;
    }

    private JButton secondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        btn.setForeground(BLUE);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(BLUE));
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 38));
        return btn;
    }

    private void attempt(boolean isLogin) {
        String serverUrl = serverUrlField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (serverUrl.isEmpty()) {
            messageLabel.setForeground(RED);
            messageLabel.setText("학원 서버 주소를 입력해주세요");
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setForeground(RED);
            messageLabel.setText("아이디와 비밀번호를 입력해주세요");
            return;
        }
        // 서버(AuthRequest)도 비밀번호 8자 이상을 요구하므로, 여기서 먼저 걸러서
        // 서버가 돌려주는 영문 검증 메시지 대신 친절한 한글 메시지를 바로 보여줌
        if (password.length() < 8) {
            messageLabel.setForeground(RED);
            messageLabel.setText("비밀번호는 8자 이상이어야 합니다");
            return;
        }

        if (isInsecureNonLocalUrl(serverUrl)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "암호화되지 않은 연결(http://)입니다. 로그인 정보와 학생 개인정보가\n" +
                            "전송 구간에서 노출될 수 있습니다. 학원 서버 주소가 https://로\n" +
                            "시작하는지 다시 확인하는 것을 권장합니다.\n\n그래도 계속 진행하시겠습니까?",
                    "보안 경고", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // 서버 주소가 바뀌었을 수도 있으니 매 시도마다 최신 입력값으로 저장함
        DataManager.setServerUrl(serverUrl);

        messageLabel.setForeground(GRAY_TXT);
        messageLabel.setText(isLogin ? "로그인 중..." : "회원가입 중...");

        // 서버 통신 중 화면이 멈추지 않도록 별도 스레드에서 처리
        new Thread(() -> {
            String error = isLogin
                    ? DataManager.login(username, password)
                    : DataManager.signup(username, password);

            SwingUtilities.invokeLater(() -> {
                if (error == null) {
                    DataManager.saveRememberedCredentials(rememberCheckBox.isSelected(), username, password);
                    success = true;
                    dispose();
                } else {
                    messageLabel.setForeground(RED);
                    messageLabel.setText(error);
                }
            });
        }).start();
    }

    public boolean isSuccess() {
        return success;
    }

    // 로컬 개발 서버(localhost/127.0.0.1)는 예외적으로 허용하고, 그 외의
    // http:// 주소만 경고 대상으로 삼음
    private boolean isInsecureNonLocalUrl(String input) {
        String lower = input.trim().toLowerCase();
        if (!lower.startsWith("http://")) {
            return false;
        }
        String host = lower.substring("http://".length());
        return !(host.startsWith("localhost") || host.startsWith("127.0.0.1"));
    }

    // JPasswordField는 HTML input의 placeholder 같은 기능이 없어서, 비어있고
    // 포커스가 없을 때만 안내 문구를 회색으로 겹쳐 그려주는 방식으로 흉내냄
    private static class PlaceholderPasswordField extends JPasswordField {
        private static final long serialVersionUID = 1L;
        private final String placeholder;

        PlaceholderPasswordField(int columns, String placeholder) {
            super(columns);
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getPassword().length != 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.GRAY);
            g2.setFont(getFont());
            Insets insets = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            int x = insets.left + 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, x, y);
            g2.dispose();
        }
    }
}
