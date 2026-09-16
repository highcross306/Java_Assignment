package main;

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

		SwingUtilities.invokeLater(Main::startSession);
	}

	// 로그인 화면 -> 메인 화면 진입까지의 한 번의 세션.
	// 로그아웃하면 MainFrame이 이 메서드를 다시 호출해서 로그인 화면으로 돌아감
	public static void startSession() {
		// 서버가 회원가입/로그인 시스템을 갖추면서, 데이터를 불러오기 전에
		// 먼저 로그인해야 함 (로그인해야 어떤 사용자의 데이터를 가져올지 알 수 있음).
		// LoginWindow는 JFrame이라 작업표시줄에 독립적으로 뜨며, 로그인 성공 시
		// 콜백으로 이어서 데이터를 불러오고 MainFrame을 연다
		LoginWindow loginWindow = new LoginWindow(() -> {
			DataManager.loadAll(); // 로그인한 사용자의 데이터를 서버에서 불러오기
			new MainFrame();
		});
		loginWindow.setVisible(true);
	}
}
