package sync;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

// 시작할 때 "이 PC와 드라이브 양쪽이 모두 바뀌었다"는 충돌을 발견하면 사용자에게 어느 쪽을 쓸지 묻는 창.
// 서버를 띄우는 백그라운드 스레드에서 호출되므로 화면 처리는 EDT에서 하고 결과만 기다린다
public class SwingSyncUi implements DriveSync.Ui {

    @Override
    public DriveSync.Conflict askConflict(String remoteTime) {
        AtomicReference<DriveSync.Conflict> result = new AtomicReference<>(DriveSync.Conflict.LATER);
        Runnable ask = () -> {
            String[] options = {"드라이브 데이터 사용", "이 PC 데이터 유지", "나중에"};
            String nl = System.lineSeparator();
            int choice = JOptionPane.showOptionDialog(null,
                    "이 PC와 구글 드라이브의 데이터가 서로 다르게 바뀌었습니다." + nl
                            + "(드라이브의 마지막 업로드: " + remoteTime + ")" + nl + nl
                            + "[드라이브 데이터 사용]  드라이브의 데이터로 바꿉니다." + nl
                            + "    이 PC의 변경 내용은 백업 폴더에 따로 보관됩니다." + nl
                            + "[이 PC 데이터 유지]  종료할 때 이 PC의 데이터가 드라이브를 덮어씁니다." + nl
                            + "    다른 PC에서 바꾼 내용은 사라집니다." + nl
                            + "[나중에]  이번 실행에서는 동기화하지 않습니다.",
                    "동기화 충돌", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            result.set(switch (choice) {
                case 0 -> DriveSync.Conflict.USE_DRIVE;
                case 1 -> DriveSync.Conflict.KEEP_LOCAL;
                default -> DriveSync.Conflict.LATER;
            });
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) ask.run();
            else SwingUtilities.invokeAndWait(ask);
        } catch (InvocationTargetException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return DriveSync.Conflict.LATER; // 묻지 못하면 데이터를 건드리지 않음
        }
        return result.get();
    }
}
