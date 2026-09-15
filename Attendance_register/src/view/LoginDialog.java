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

    private final JTextField usernameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
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
        form.add(labeled("아이디", usernameField));
        form.add(Box.createRigidArea(new Dimension(0, 10)));
        form.add(labeled("비밀번호", passwordField));

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

        messageLabel.setForeground(GRAY_TXT);
        messageLabel.setText(isLogin ? "로그인 중..." : "회원가입 중...");

        // 서버 통신 중 화면이 멈추지 않도록 별도 스레드에서 처리
        new Thread(() -> {
            String error = isLogin
                    ? DataManager.login(username, password)
                    : DataManager.signup(username, password);

            SwingUtilities.invokeLater(() -> {
                if (error == null) {
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
}
