package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import model.DataManager;

// 프로그램 시작 시 뜨는 로그인/회원가입 창.
// JDialog가 아니라 JFrame으로 만든 이유: JDialog는 윈도우 작업표시줄에 독립된
// 항목으로 뜨지 않아서, 다른 창들에 가려지면 최소화하지 않는 이상 찾기 어려웠음.
// 로그인 성공 시 onLoginSuccess 콜백을 실행하고 이 창은 닫힘(모달로 막아두는
// 대신 콜백으로 이어감)
public class LoginWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color BG = new Color(248, 249, 251);
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color GRAY_TXT = new Color(107, 114, 128);

    private final Runnable onLoginSuccess;

    private final JTextField usernameField = new JTextField(16);
    private final JPasswordField passwordField = new PlaceholderPasswordField(16, "비밀번호는 8자 이상이어야 합니다");
    private final JCheckBox rememberCheckBox = new JCheckBox("내 정보 기억");
    private final JLabel messageLabel = new JLabel(" ");

    public LoginWindow(Runnable onLoginSuccess) {
        super("학원 출석 관리 프로그램 - 로그인");
        this.onLoginSuccess = onLoginSuccess;
        setResizable(false);
        // 로그인하지 않고 창을 닫으면(작업표시줄 우클릭 닫기 포함) 프로그램 자체를 종료
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

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
        // 서버 주소는 입력받지 않음: 이 PC 안의 내장 서버(Main이 시작 시 자동 설정)에 접속한다
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
        setLocationRelativeTo(null); // 소유 창이 없으니 화면 가운데에 띄움
    }

    // 비밀번호 입력칸 오른쪽에 눈 모양 아이콘 토글 버튼을 붙인 한 줄짜리 패널을 만듦.
    // 기본은 별표(echo char)로 가려져 있고(감은 눈), 누르면 그 동안만 평문으로 보여줌(뜬 눈)
    private JPanel passwordFieldWithToggle() {
        char defaultEchoChar = passwordField.getEchoChar();
        JButton toggleBtn = new JButton(new EyeIcon(false));
        toggleBtn.setToolTipText("비밀번호 보기");
        toggleBtn.setBorderPainted(false);
        toggleBtn.setContentAreaFilled(false);
        toggleBtn.setFocusPainted(false);
        toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleBtn.setPreferredSize(new Dimension(28, 28));
        toggleBtn.addActionListener(e -> {
            boolean willBeVisible = passwordField.getEchoChar() != 0;
            passwordField.setEchoChar(willBeVisible ? (char) 0 : defaultEchoChar);
            toggleBtn.setIcon(new EyeIcon(willBeVisible));
            toggleBtn.setToolTipText(willBeVisible ? "비밀번호 숨기기" : "비밀번호 보기");
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
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

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
                    dispose();
                    onLoginSuccess.run();
                } else {
                    messageLabel.setForeground(RED);
                    messageLabel.setText(error);
                }
            });
        }).start();
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

    // 버튼에 쓸 눈 모양 아이콘을 직접 그려서 씀 (이미지 리소스 없이 처리).
    // visible=true면 눈동자가 있는 "뜬 눈", false면 사선이 그어진 "감은 눈"
    private static class EyeIcon implements Icon {
        private static final int SIZE = 18;
        private final boolean visible;

        EyeIcon(boolean visible) {
            this.visible = visible;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(GRAY_TXT);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 눈 윤곽(아몬드형)
            g2.draw(new Ellipse2D.Double(1, 4, SIZE - 2, SIZE - 8));

            if (visible) {
                int pupil = 5;
                g2.fillOval(SIZE / 2 - pupil / 2, SIZE / 2 - pupil / 2, pupil, pupil);
            } else {
                g2.draw(new Line2D.Double(1, SIZE - 3, SIZE - 1, 3));
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
