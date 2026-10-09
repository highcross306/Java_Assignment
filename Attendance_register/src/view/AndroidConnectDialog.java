package view;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import server.ServerLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

// 설정 > "Android 연결 정보" 창.
// 왼쪽: 폰 카메라로 찍으면 이 PC의 내장 서버에서 Android 앱(APK)을 내려받는 QR 코드
// 오른쪽: 앱이 이 PC를 찾는 방법과, 자동으로 못 찾을 때 [서버 주소 직접 입력]에 넣을 주소
final class AndroidConnectDialog {

	private static final int QR_SIZE = 220;
	private static final Font TITLE_FONT = new Font("맑은 고딕", Font.BOLD, 14);
	private static final Font BODY_FONT = new Font("맑은 고딕", Font.PLAIN, 13);
	private static final Font ADDRESS_FONT = new Font("맑은 고딕", Font.BOLD, 20);
	private static final Color GRAY_TXT = new Color(107, 114, 128);

	private AndroidConnectDialog() {}

	static void show(Component parent) {
		JPanel root = new JPanel(new BorderLayout(24, 0));
		root.add(buildInstallPanel(), BorderLayout.WEST);
		root.add(buildConnectPanel(), BorderLayout.CENTER);
		JOptionPane.showMessageDialog(parent, root, "Android 연결 정보", JOptionPane.PLAIN_MESSAGE);
	}

	private static JPanel buildInstallPanel() {
		JPanel panel = column();
		panel.add(label("① 앱 설치", TITLE_FONT, Color.BLACK));
		panel.add(Box.createRigidArea(new Dimension(0, 8)));

		String url = ServerLauncher.getApkDownloadUrl();
		if (ServerLauncher.findApk() == null) {
			panel.add(label("<html>이 PC에 Android 앱 파일<br>(AttendanceApp.apk)이 없습니다.<br><br>"
					+ "설치 프로그램으로 설치하면<br>함께 들어갑니다.</html>", BODY_FONT, GRAY_TXT));
		} else if (url == null) {
			panel.add(label("<html>Wi-Fi에 연결된 이 PC의 주소를<br>찾지 못해 QR 코드를 만들 수 없습니다.</html>",
					BODY_FONT, GRAY_TXT));
		} else {
			try {
				JLabel qr = new JLabel(new ImageIcon(qr(url, QR_SIZE)));
				qr.setAlignmentX(Component.LEFT_ALIGNMENT);
				panel.add(qr);
			} catch (WriterException e) {
				panel.add(label("QR 코드를 만들지 못했습니다.", BODY_FONT, GRAY_TXT));
			}
			panel.add(Box.createRigidArea(new Dimension(0, 8)));
			panel.add(label("<html>같은 Wi-Fi의 폰 카메라로 찍어서<br>내려받은 파일을 열어 설치하세요.<br>"
					+ "('출처를 알 수 없는 앱' 허용이 필요할 수 있음)</html>", BODY_FONT, GRAY_TXT));
			JTextField urlField = new JTextField(url); // 카메라로 못 찍을 때 직접 입력할 수 있게 (복사 가능)
			urlField.setEditable(false);
			urlField.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			urlField.setAlignmentX(Component.LEFT_ALIGNMENT);
			urlField.setMaximumSize(urlField.getPreferredSize());
			panel.add(Box.createRigidArea(new Dimension(0, 6)));
			panel.add(urlField);
		}
		return panel;
	}

	private static JPanel buildConnectPanel() {
		JPanel panel = column();
		panel.add(label("② 서버 연결", TITLE_FONT, Color.BLACK));
		panel.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(label("<html>같은 Wi-Fi에 연결된 Android 앱은<br>이 PC를 자동으로 찾습니다.<br>"
				+ "(이 프로그램이 켜져 있어야 합니다)<br><br>"
				+ "자동으로 찾지 못하면 앱의<br>[서버 주소 직접 입력]에 아래 주소를<br>입력하세요:</html>",
				BODY_FONT, GRAY_TXT));
		panel.add(Box.createRigidArea(new Dimension(0, 10)));
		JTextField address = new JTextField(ServerLauncher.getLanAddressHint());
		address.setEditable(false);
		address.setBorder(BorderFactory.createEmptyBorder());
		address.setOpaque(false);
		address.setFont(ADDRESS_FONT);
		address.setAlignmentX(Component.LEFT_ALIGNMENT);
		address.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		panel.add(address);
		// 설치할 때 기본 포트를 쓸 수 없어 포트가 바뀐 PC: 앱의 [포트 설정]에 넣어야 자동 검색(와이파이 전체 확인)도 됨
		if (!ServerLauncher.isDefaultPort()) {
			panel.add(Box.createRigidArea(new Dimension(0, 10)));
			panel.add(label("<html>이 PC는 포트 " + ServerLauncher.getPort() + "을(를) 사용합니다.<br>"
					+ "앱 로그인 화면의 [포트 설정]에<br>" + ServerLauncher.getPort() + "을(를) 입력하세요.</html>",
					BODY_FONT, new Color(220, 38, 38)));
		}
		return panel;
	}

	private static JPanel column() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		return panel;
	}

	private static JLabel label(String text, Font font, Color color) {
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static BufferedImage qr(String text, int size) throws WriterException {
		BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
				Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
			}
		}
		return img;
	}
}
