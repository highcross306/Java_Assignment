package migrate;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// 창 하나짜리 화면: ① 옛 데이터 확인 → ② 지금 프로그램 계정 입력 → ③ 옮기기
public class MigrateWindow extends JFrame {

    private static final String ALL_USERS = "(전체)";

    private final JTextField oldHost = new JTextField("localhost", 12);
    private final JTextField oldPort = new JTextField("3306", 5);
    private final JTextField oldDb = new JTextField("attendance_db", 12);
    private final JTextField oldUser = new JTextField("attendance_user", 12);
    private final JPasswordField oldPassword = new JPasswordField(12);
    private final JComboBox<Object> oldAccount = new JComboBox<>();
    private final JLabel oldAccountLabel = new JLabel("옛 계정");
    private final JLabel oldStatus = new JLabel(" ");
    private final JButton checkButton = new JButton("옛 데이터 확인");

    private final JTextField serverUrl = new JTextField(NewServer.defaultBaseUrl(), 20);
    private final JTextField appUser = new JTextField(12);
    private final JPasswordField appPassword = new JPasswordField(12);
    private final JCheckBox createAccount = new JCheckBox("이 아이디가 없으면 새로 만들기");

    private final JButton migrateButton = new JButton("옮기기 시작");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea logArea = new JTextArea(10, 50);
    private final StringBuilder fullLog = new StringBuilder();

    private OldDatabase.Data loaded;
    private boolean updatingAccounts;

    public MigrateWindow() {
        super("출석 관리 - 예전 데이터 옮기기");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel intro = new JLabel("<html>예전 버전(MariaDB)에 저장된 반·학생·출석 기록을 지금 출석 관리 프로그램으로 옮깁니다.<br>"
                + "예전 데이터는 읽기만 하고 바꾸지 않습니다. 여러 번 실행해도 같은 데이터가 두 번 들어가지 않습니다.</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel forms = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        g.gridx = 0;
        g.insets = new Insets(0, 0, 8, 0);
        forms.add(oldPanel(), g);
        forms.add(newPanel(), g);
        content.add(forms, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        JPanel runRow = new JPanel(new BorderLayout(8, 0));
        migrateButton.setEnabled(false);
        runRow.add(migrateButton, BorderLayout.WEST);
        progress.setStringPainted(true);
        progress.setString("");
        runRow.add(progress, BorderLayout.CENTER);
        bottom.add(runRow, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(560, 180));
        bottom.add(logScroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        setContentPane(content);

        checkButton.addActionListener(e -> checkOldData(false));
        oldPassword.addActionListener(e -> checkOldData(false));
        oldAccount.addActionListener(e -> {
            if (!updatingAccounts) checkOldData(true);
        });
        migrateButton.addActionListener(e -> startMigration());

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private JPanel oldPanel() {
        JPanel p = titled("① 예전 데이터 (MariaDB)");
        GridBagConstraints g = cell();
        row(p, g, 0, "DB 주소", oldHost, "포트", oldPort);
        row(p, g, 1, "DB 이름", oldDb, null, null);
        row(p, g, 2, "DB 아이디", oldUser, null, null);
        row(p, g, 3, "DB 비밀번호", oldPassword, null, null);
        oldAccount.setVisible(false);
        oldAccountLabel.setVisible(false);
        g.gridy = 4; g.gridx = 0; p.add(oldAccountLabel, g);
        g.gridx = 1; p.add(oldAccount, g);
        g.gridy = 5; g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.NONE; g.anchor = GridBagConstraints.WEST;
        p.add(checkButton, g);
        g.gridy = 6; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(oldStatus, g);
        return p;
    }

    private JPanel newPanel() {
        JPanel p = titled("② 지금 출석 관리 프로그램 (먼저 실행해 두세요)");
        GridBagConstraints g = cell();
        row(p, g, 0, "서버 주소", serverUrl, null, null);
        row(p, g, 1, "아이디", appUser, null, null);
        row(p, g, 2, "비밀번호", appPassword, null, null);
        g.gridy = 3; g.gridx = 1; g.gridwidth = 3;
        p.add(createAccount, g);
        return p;
    }

    private static JPanel titled(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        return p;
    }

    private static GridBagConstraints cell() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 4, 3, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
    }

    private static void row(JPanel p, GridBagConstraints g, int y, String label, java.awt.Component field,
                            String label2, java.awt.Component field2) {
        g.gridy = y; g.gridwidth = 1;
        g.gridx = 0; g.weightx = 0; p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; p.add(field, g);
        if (label2 != null) {
            g.gridx = 2; g.weightx = 0; p.add(new JLabel(label2), g);
            g.gridx = 3; p.add(field2, g);
        }
        g.weightx = 0;
    }

    private OldDatabase oldDatabase() {
        int port;
        try {
            port = Integer.parseInt(oldPort.getText().trim());
        } catch (NumberFormatException e) {
            port = 3306;
        }
        return new OldDatabase(oldHost.getText().trim(), port, oldDb.getText().trim(), oldUser.getText().trim(),
                new String(oldPassword.getPassword()));
    }

    // ① 옛 DB에 접속해서 데이터 개수를 보여 줌. accountChanged면 옛 계정만 바꿔서 다시 읽음
    private void checkOldData(boolean accountChanged) {
        loaded = null;
        migrateButton.setEnabled(false);
        checkButton.setEnabled(false);
        oldStatus.setForeground(Color.DARK_GRAY);
        oldStatus.setText("확인 중...");
        Object selected = oldAccount.getSelectedItem();
        OldDatabase db = oldDatabase();

        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                List<OldDatabase.OldUser> users = db.listUsers();
                Integer userId = null;
                if (accountChanged && selected instanceof OldDatabase.OldUser u) userId = u.id();
                return new Object[]{users, db.load(userId)};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                checkButton.setEnabled(true);
                try {
                    Object[] r = get();
                    List<OldDatabase.OldUser> users = (List<OldDatabase.OldUser>) r[0];
                    loaded = (OldDatabase.Data) r[1];
                    if (!accountChanged) showAccounts(users);
                    oldStatus.setForeground(new Color(0, 110, 40));
                    oldStatus.setText("찾음: " + loaded.summary());
                    log("예전 데이터 " + loaded.summary());
                    migrateButton.setEnabled(true);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    oldStatus.setForeground(new Color(170, 0, 0));
                    oldStatus.setText("<html>" + explainDbError(cause, db) + "</html>");
                    if (isConnectionRefused(cause)) offerStartService();
                }
                pack();
            }
        }.execute();
    }

    private void showAccounts(List<OldDatabase.OldUser> users) {
        updatingAccounts = true;
        DefaultComboBoxModel<Object> model = new DefaultComboBoxModel<>();
        model.addElement(ALL_USERS);
        users.forEach(model::addElement);
        oldAccount.setModel(model);
        oldAccount.setVisible(!users.isEmpty());
        oldAccountLabel.setVisible(!users.isEmpty());
        updatingAccounts = false;
    }

    private static boolean isConnectionRefused(Throwable t) {
        String m = String.valueOf(t.getMessage()).toLowerCase();
        return m.contains("connection refused") || m.contains("could not connect") || m.contains("socket fail")
                || m.contains("connect timed out");
    }

    private String explainDbError(Throwable t, OldDatabase db) {
        String m = String.valueOf(t.getMessage());
        String lower = m.toLowerCase();
        if (isConnectionRefused(t)) {
            return "MariaDB에 연결할 수 없습니다. MariaDB가 켜져 있는지, 주소·포트가 맞는지 확인해 주세요.";
        }
        if (lower.contains("access denied")) {
            return "DB 아이디 또는 비밀번호가 틀렸습니다. (root 계정으로도 해 볼 수 있습니다)";
        }
        if (lower.contains("unknown database")) {
            try {
                List<String> names = db.listDatabases();
                return "'" + oldDb.getText().trim() + "' DB가 없습니다. 이 PC에 있는 DB: "
                        + (names.isEmpty() ? "(없음)" : String.join(", ", names));
            } catch (SQLException ignored) {
                return "'" + oldDb.getText().trim() + "' DB가 없습니다. DB 이름을 확인해 주세요.";
            }
        }
        return "읽기 실패: " + m;
    }

    private void offerStartService() {
        String host = oldHost.getText().trim().toLowerCase();
        if (!host.equals("localhost") && !host.equals("127.0.0.1")) return;
        String service = ServiceHelper.findStoppedDatabaseService();
        if (service == null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "이 PC의 MariaDB 서비스(" + service + ")가 꺼져 있습니다.\n지금 켤까요? (관리자 권한 확인 창이 뜹니다)",
                "MariaDB 켜기", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        oldStatus.setForeground(Color.DARK_GRAY);
        oldStatus.setText("MariaDB를 켜는 중...");
        checkButton.setEnabled(false);
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                if (!ServiceHelper.startWithElevation(service)) return false;
                Thread.sleep(2000); // 서비스가 접속을 받기 시작할 때까지 잠깐 기다림
                return true;
            }

            @Override
            protected void done() {
                checkButton.setEnabled(true);
                boolean ok;
                try {
                    ok = get();
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) {
                    log("MariaDB 서비스(" + service + ")를 켰습니다.");
                    checkOldData(false);
                } else {
                    oldStatus.setText("MariaDB를 켜지 못했습니다. (관리자 권한 확인을 취소했거나 시작에 실패함)");
                }
            }
        }.execute();
    }

    // ③ 옮기기
    private void startMigration() {
        if (loaded == null) return;
        String user = appUser.getText().trim();
        String pw = new String(appPassword.getPassword());
        if (user.isEmpty() || pw.isEmpty()) {
            JOptionPane.showMessageDialog(this, "지금 프로그램의 아이디와 비밀번호를 입력해 주세요.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "지금 프로그램의 '" + user + "' 계정으로 옮깁니다.\n" + loaded.summary() + "\n\n계속할까요?",
                "옮기기 확인", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;

        OldDatabase.Data data = loaded;
        NewServer server = new NewServer(serverUrl.getText());
        boolean create = createAccount.isSelected();
        setBusy(true);
        progress.setValue(0);
        progress.setString("");

        new SwingWorker<Migrator.Result, Object>() {
            @Override
            protected Migrator.Result doInBackground() throws Exception {
                if (!server.isUp()) {
                    throw new IOException("지금 출석 관리 프로그램에 연결할 수 없습니다.\n"
                            + "출석 관리 프로그램을 먼저 실행해 주세요. (로그인 화면까지만 떠 있어도 됩니다)");
                }
                server.login(user, pw, create);
                publish("'" + user + "' 계정으로 로그인했습니다. 옮기는 중...");
                return Migrator.run(data, server, new Migrator.Listener() {
                    @Override
                    public void log(String line) {
                        publish(line);
                    }

                    @Override
                    public void progress(int done, int total) {
                        publish(new int[]{done, total});
                    }
                });
            }

            @Override
            protected void process(List<Object> chunks) {
                for (Object c : chunks) {
                    if (c instanceof int[] p) {
                        progress.setMaximum(Math.max(1, p[1]));
                        progress.setValue(p[0]);
                        progress.setString(p[0] + " / " + p[1]);
                    } else {
                        log(c.toString());
                    }
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Migrator.Result r = get();
                    log("완료\n" + r.summary());
                    String saved = saveLog();
                    JOptionPane.showMessageDialog(MigrateWindow.this,
                            "옮기기를 마쳤습니다.\n\n" + r.summary()
                                    + "\n\n출석 관리 프로그램에서 새로고침을 누르면 보입니다."
                                    + (saved == null ? "" : "\n(기록: " + saved + ")"),
                            "완료", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log("실패: " + cause.getMessage());
                    saveLog();
                    JOptionPane.showMessageDialog(MigrateWindow.this,
                            cause.getMessage() + "\n\n중간까지 들어간 데이터는 남아 있고, 다시 실행하면 이어서 옮깁니다.",
                            "옮기지 못함", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        migrateButton.setEnabled(!busy);
        checkButton.setEnabled(!busy);
        oldAccount.setEnabled(!busy);
    }

    private void log(String line) {
        fullLog.append(line).append('\n');
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // 실행 기록을 ~/.attendance-desktop/migrate-log-날짜.txt로 남김 (문의 받을 때 확인용)
    private String saveLog() {
        try {
            File dir = new File(System.getProperty("user.home"), ".attendance-desktop");
            dir.mkdirs();
            File f = new File(dir, "migrate-log-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
            Files.writeString(f.toPath(), fullLog.toString(), StandardCharsets.UTF_8);
            return f.getAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> new MigrateWindow().setVisible(true));
    }
}
