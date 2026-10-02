package view;

import server.ServerLauncher;
import sync.DriveSync;
import sync.GoogleAuth;
import sync.RemoteStore;

import javax.swing.*;
import java.awt.*;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

// "구글 드라이브 동기화" 설정 화면: 구글 로그인 -> 동기화 비밀번호 정하기/입력 -> 켜기, 또는 끄기.
// 네트워크/암호 계산이 들어가는 단계는 전부 백그라운드에서 실행해서 화면이 멈추지 않게 한다.
public final class SyncDialog {

    private static final String NL = System.lineSeparator();

    private final Component parent;
    private final GoogleAuth auth;
    private final DriveSync sync;
    private final JDialog dialog;
    private final JLabel statusLabel = new JLabel();
    private final JButton toggleButton = new JButton("자동 동기화 허용");
    private final JPanel consentPanel = new JPanel(new GridLayout(0, 1, 0, 4));
    private final JCheckBox[] consents = {
            new JCheckBox("<html><div style='width:330px'>학생 이름·연락처 등 <b>출석 데이터 전체가 암호화되어</b> 내 구글 드라이브의 "
                    + "앱 전용 숨김 폴더에 저장되는 것에 동의합니다.</div></html>"),
            new JCheckBox("<html><div style='width:330px'>프로그램을 켤 때와 끌 때 <b>자동으로 동기화</b>되며, 다른 PC와 동시에 바꾼 "
                    + "경우 한쪽을 선택해야 함을 이해했습니다.</div></html>"),
            new JCheckBox("<html><div style='width:330px'>동기화 비밀번호를 잊으면 드라이브의 데이터를 <b>복구할 수 없음</b>을 "
                    + "이해했습니다.</div></html>"),
            new JCheckBox("<html><div style='width:330px'>학생·보호자의 개인정보를 구글 서버에 저장하는 것에 대해 "
                    + "필요한 안내와 동의를 받았음을 확인합니다.</div></html>")
    };

    private SyncDialog(Component parent, GoogleAuth auth, DriveSync sync) {
        this.parent = parent;
        this.auth = auth;
        this.sync = sync;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        this.dialog = new JDialog(owner, "구글 드라이브 동기화", Dialog.ModalityType.APPLICATION_MODAL);
    }

    public static void show(Component parent) {
        GoogleAuth auth = ServerLauncher.getGoogleAuth();
        DriveSync sync = ServerLauncher.getDriveSync();
        if (auth == null || sync == null) {
            JOptionPane.showMessageDialog(parent, "동기화 기능을 사용할 수 없습니다.", "구글 드라이브 동기화", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SyncDialog(parent, auth, sync).open();
    }

    private void open() {
        JLabel intro = new JLabel("<html><div style='width:360px'>"
                + "출석 데이터를 <b>암호화</b>해서 내 구글 드라이브의 <b>앱 전용 숨김 폴더</b>에 올려두고, "
                + "다른 PC에서도 같은 데이터를 볼 수 있게 합니다.<br><br>"
                + "· 프로그램을 <b>켤 때</b> 드라이브의 최신 데이터를 받아오고, <b>끌 때</b> 변경된 데이터를 올립니다.<br>"
                + "· 암호화 키는 이 PC와 내가 정한 <b>동기화 비밀번호</b>에만 있어서, 구글도 내용을 볼 수 없습니다.<br>"
                + "· 이 앱은 드라이브의 다른 파일에는 접근할 수 없습니다."
                + "</div></html>");
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JButton closeButton = new JButton("닫기");
        closeButton.addActionListener(e -> dialog.dispose());
        toggleButton.addActionListener(e -> {
            if (sync.isEnabled()) disableSync();
            else enableSync();
        });

        // 동기화가 꺼져 있을 때만 보이는 동의 항목. 모두 체크해야 [자동 동기화 허용]이 눌린다
        consentPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
        for (JCheckBox box : consents) {
            box.setFocusPainted(false);
            box.addItemListener(e -> updateAllowButton());
            consentPanel.add(box);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(toggleButton);
        buttons.add(closeButton);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        JPanel top = new JPanel(new BorderLayout());
        top.add(intro, BorderLayout.NORTH);
        top.add(statusLabel, BorderLayout.CENTER);
        top.add(consentPanel, BorderLayout.SOUTH);
        content.add(top, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        refresh();
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder("<html><div style='width:360px'>");
        boolean enabled = sync.isEnabled();
        if (enabled) {
            sb.append("상태: <b>자동 동기화 켜짐</b>");
            if (!sync.getAccountEmail().isEmpty()) sb.append(" (").append(escape(sync.getAccountEmail())).append(")");
            if (!sync.getConsentAt().isEmpty()) sb.append("<br><font color='#6B7280'>동의: ").append(escape(sync.getConsentAt())).append("</font>");
        } else {
            sb.append("상태: <b>꺼짐</b> - 아래 내용에 모두 동의하면 [자동 동기화 허용]을 누를 수 있습니다.");
        }
        if (!auth.isClientConfigured()) {
            sb.append("<br><font color='#B91C1C'>※ 구글 로그인 설정 파일(google-client.json)이 아직 없습니다. ")
              .append("개발자 설정이 끝나기 전에는 허용해도 로그인할 수 없습니다. ")
              .append("(설치 폴더의 구글드라이브_동기화_설정.md 참고)</font>");
        }
        if (!sync.getLastSyncTime().isEmpty()) {
            sb.append("<br><font color='#6B7280'>").append(escape(sync.getLastSyncTime())).append(" · ")
              .append(escape(sync.getLastMessage())).append("</font>");
        }
        sb.append("</div></html>");
        statusLabel.setText(sb.toString());

        consentPanel.setVisible(!enabled);
        toggleButton.setText(enabled ? "자동 동기화 끄기" : "자동 동기화 허용");
        updateAllowButton();
        dialog.pack();
    }

    // 꺼져 있을 때: 동의 항목을 모두 체크해야 [허용] 가능. 켜져 있을 때: [끄기]는 항상 가능
    private void updateAllowButton() {
        if (sync.isEnabled()) {
            toggleButton.setEnabled(true);
            toggleButton.setToolTipText(null);
            return;
        }
        boolean all = true;
        for (JCheckBox box : consents) all &= box.isSelected();
        toggleButton.setEnabled(all);
        toggleButton.setToolTipText(all ? null : "동의 항목을 모두 체크해주세요");
    }

    // ---------------------------------------------------------------- 켜기

    private void enableSync() {
        for (JCheckBox box : consents) {
            if (!box.isSelected()) return; // 버튼이 비활성이라 오지 않지만, 동의 없이 켜지 않도록 한 번 더 확인
        }
        if (!auth.isClientConfigured()) {
            JOptionPane.showMessageDialog(dialog,
                    "구글 로그인 설정 파일(google-client.json)이 없어 지금은 연결할 수 없습니다." + NL
                            + "개발자 설정(구글드라이브_동기화_설정.md)을 마친 뒤 다시 시도해주세요.",
                    "구글 드라이브 동기화", JOptionPane.WARNING_MESSAGE);
            return;
        }
        sync.markConsentGiven();
        busy("브라우저에서 구글 로그인을 완료해주세요...");
        runAsync(() -> {
            if (!auth.isSignedIn()) auth.signIn();
            return sync.peekRemote();
        }, meta -> {
            if (meta == null) createNewPassphrase();
            else enterExistingPassphrase(meta);
        }, e -> fail("로그인/조회 실패", e));
    }

    // 이 계정으로 처음 켜는 PC: 새 동기화 비밀번호 정하기
    private void createNewPassphrase() {
        JPasswordField first = new JPasswordField(20);
        JPasswordField second = new JPasswordField(20);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("<html>동기화 비밀번호를 정해주세요 (8자 이상).<br>"
                + "<font color='#B91C1C'>다른 PC에서 데이터를 받을 때 필요하며,<br>"
                + "잊어버리면 드라이브의 데이터를 복구할 수 없습니다.</font></html>"));
        panel.add(first);
        panel.add(new JLabel("한 번 더 입력"));
        panel.add(second);

        while (true) {
            first.setText("");
            second.setText("");
            if (JOptionPane.showConfirmDialog(dialog, panel, "동기화 비밀번호 정하기",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
                refresh();
                return;
            }
            char[] a = first.getPassword();
            char[] b = second.getPassword();
            if (a.length < 8) {
                JOptionPane.showMessageDialog(dialog, "비밀번호는 8자 이상이어야 합니다.", "동기화 비밀번호", JOptionPane.WARNING_MESSAGE);
            } else if (!java.util.Arrays.equals(a, b)) {
                JOptionPane.showMessageDialog(dialog, "두 비밀번호가 일치하지 않습니다.", "동기화 비밀번호", JOptionPane.WARNING_MESSAGE);
            } else {
                busy("암호화 키를 만드는 중...");
                runAsync(() -> {
                    sync.enableWithNewPassphrase(a, auth.getEmail());
                    return null;
                }, ignored -> {
                    refresh();
                    JOptionPane.showMessageDialog(dialog, "동기화를 켰습니다." + NL
                            + "프로그램을 종료할 때 데이터가 암호화되어 드라이브에 올라갑니다.",
                            "구글 드라이브 동기화", JOptionPane.INFORMATION_MESSAGE);
                }, e -> fail("동기화 설정 실패", e));
                return;
            }
        }
    }

    // 드라이브에 이미 데이터가 있음(다른 PC에서 올림): 그 비밀번호를 입력해서 확인
    private void enterExistingPassphrase(RemoteStore.Meta meta) {
        JPasswordField field = new JPasswordField(20);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("<html>이 구글 계정의 드라이브에 이미 동기화 데이터가 있습니다.<br>"
                + "다른 PC에서 정한 <b>동기화 비밀번호</b>를 입력해주세요.</html>"));
        panel.add(field);

        field.setText("");
        if (JOptionPane.showConfirmDialog(dialog, panel, "동기화 비밀번호 입력",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            refresh();
            return;
        }
        char[] passphrase = field.getPassword();
        busy("비밀번호를 확인하는 중...");
        runAsync(() -> sync.verifyExisting(passphrase), verified -> chooseDataSource(verified), e -> {
            if (e instanceof GeneralSecurityException) {
                JOptionPane.showMessageDialog(dialog, "비밀번호가 다릅니다. 다시 입력해주세요.", "동기화 비밀번호", JOptionPane.WARNING_MESSAGE);
                refresh();
                enterExistingPassphrase(meta);
            } else {
                fail("확인 실패", e);
            }
        });
    }

    private void chooseDataSource(RemoteStore.Meta meta) {
        refresh();
        String[] options = {"드라이브 데이터 사용", "이 PC 데이터로 덮어쓰기", "취소"};
        int choice = JOptionPane.showOptionDialog(dialog,
                "드라이브의 데이터와 이 PC의 데이터는 합쳐지지 않습니다. 어느 쪽을 사용할까요?" + NL + NL
                        + "[드라이브 데이터 사용]  프로그램을 다시 시작하면 드라이브의 데이터를 받아옵니다." + NL
                        + "    (이 PC의 기존 데이터는 백업 폴더에 보관됩니다)" + NL
                        + "[이 PC 데이터로 덮어쓰기]  종료할 때 이 PC의 데이터가 드라이브를 덮어씁니다.",
                "데이터 선택", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice != 0 && choice != 1) return;

        boolean useDrive = choice == 0;
        runAsync(() -> {
            sync.enableWithExisting(meta, useDrive, auth.getEmail());
            return null;
        }, ignored -> {
            refresh();
            JOptionPane.showMessageDialog(dialog, useDrive
                            ? "동기화를 켰습니다. 프로그램을 종료한 뒤 다시 실행하면 드라이브의 데이터를 받아옵니다."
                            : "동기화를 켰습니다. 프로그램을 종료할 때 이 PC의 데이터가 드라이브에 올라갑니다.",
                    "구글 드라이브 동기화", JOptionPane.INFORMATION_MESSAGE);
        }, e -> fail("동기화 설정 실패", e));
    }

    // ---------------------------------------------------------------- 끄기

    private void disableSync() {
        if (JOptionPane.showConfirmDialog(dialog,
                "동기화를 끌까요?" + NL + "이 PC에서만 해제되며, 드라이브에 올라간 데이터는 그대로 남아 있습니다.",
                "동기화 끄기", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        sync.disable();
        auth.signOut();
        for (JCheckBox box : consents) box.setSelected(false);
        refresh();
    }

    // ---------------------------------------------------------------- 공통

    private void busy(String message) {
        toggleButton.setEnabled(false);
        statusLabel.setText("<html><div style='width:360px'>" + escape(message) + "</div></html>");
    }

    private void fail(String title, Exception e) {
        refresh();
        JOptionPane.showMessageDialog(dialog, e.getMessage() == null ? e.toString() : e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private static <T> void runAsync(Callable<T> work, Consumer<T> onSuccess, Consumer<Exception> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    onError.accept(cause instanceof Exception ex ? ex : new Exception(cause));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
