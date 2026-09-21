package main;

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
				DataManager.setServerUrl("http://localhost:8080");
				startSession();
			}
		}.execute();
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
