package main;

import server.FirewallSetup;
import server.ServerLauncher;
import view.LoginWindow;
import view.MainFrame;
import model.DataManager;
import javax.swing.*;

public class Main {
	public static void main(String[] args) {
		// 종료 방식에 상관없이 항상 저장(종료 시 thread를 생성)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			DataManager.saveAll();
		}));

		SwingUtilities.invokeLater(Main::startWithEmbeddedServer);
	}

	// 로그인 화면을 열기 전에, 이 PC 안에서 백엔드 서버(attendance-server)를 먼저
	// 자식 프로세스로 띄우고 응답할 때까지 기다린다. Android 앱은 같은 와이파이 안에서
	// 이 PC의 LAN 주소로 같은 서버에 접속한다 (MainFrame의 "Android 연결 정보" 참고).
	private static void startWithEmbeddedServer() {
		JWindow splash = new JWindow();
		JLabel label = new JLabel("내장 서버 시작 중...", SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
		splash.add(label);
		splash.pack();
		splash.setLocationRelativeTo(null);
		splash.setVisible(true);

		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() {
				return ServerLauncher.start();
			}
			@Override
			protected void done() {
				splash.dispose();
				String error;
				try {
					error = get();
				} catch (Exception e) {
					error = "내장 서버 시작 실패: " + e.getMessage();
				}
				if (error != null) {
					JOptionPane.showMessageDialog(null, error, "내장 서버 시작 실패", JOptionPane.ERROR_MESSAGE);
					System.exit(1);
					return;
				}
				DataManager.setServerUrl(ServerLauncher.getBaseUrl());
				ensureFirewall();
				startSession();
			}
		}.execute();
	}

	// Android 앱이 이 PC에 접속하려면 방화벽 허용 규칙이 필요함. 없으면 관리자 권한 요청이
	// 한 번 뜬다고 먼저 안내한 뒤 등록하고, 거부/실패해도 PC 쪽 사용은 막지 않고 경고만 한다
	private static void ensureFirewall() {
		if (FirewallSetup.isConfigured()) return;
		JOptionPane.showMessageDialog(null,
				"Android 앱이 이 PC에 연결하려면 Windows 방화벽 허용이 필요합니다.\n"
				+ "곧 나오는 '관리자 권한 요청'에서 [예]를 눌러주세요. (처음 한 번만 필요합니다)",
				"방화벽 설정", JOptionPane.INFORMATION_MESSAGE);
		if (!FirewallSetup.configure(ServerLauncher.getPort(), ServerLauncher.getDiscoveryPort())) {
			JOptionPane.showMessageDialog(null,
					"방화벽 허용이 완료되지 않았습니다. Android 앱에서 이 PC를 찾지 못할 수 있습니다.\n"
					+ "앱을 다시 실행해서 [예]를 누르거나, 관리자 권한으로 한 번 실행해주세요.",
					"방화벽 설정 실패", JOptionPane.WARNING_MESSAGE);
		}
	}

	// 로그인 화면 -> 메인 화면 진입까지의 한 번의 세션.
	// 로그아웃하면 MainFrame이 이 메서드를 다시 호출해서 로그인 화면으로 돌아감
	public static void startSession() {
		// 서버가 회원가입/로그인 시스템을 갖추면서, 데이터를 불러오기 전에
		// 먼저 로그인해야 함 (로그인해야 어떤 사용자의 데이터를 가져올지 알 수 있음).
		// LoginWindow는 JFrame이라 작업표시줄에 독립적으로 뜨며, 로그인 성공 시
		// 콜백으로 이어서 데이터를 불러오고 MainFrame을 연다
		LoginWindow loginWindow = new LoginWindow(() -> {
			// 로그인한 사용자의 데이터를 서버에서 불러오는 동안(GET 4번) 화면이 멈춘 것처럼
			// 굳어 보이지 않도록, 통신은 백그라운드 스레드에서 하고 끝난 뒤에만 메인 화면을 연다
			new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground() {
					DataManager.loadAll();
					return null;
				}
				@Override
				protected void done() {
					new MainFrame();
				}
			}.execute();
		});
		loginWindow.setVisible(true);
	}
}
