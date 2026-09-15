package main;

import view.LoginDialog;
import view.MainFrame;
import model.DataManager;
import javax.swing.*;

public class Main {
	public static void main(String[] args) {
		// 서버가 회원가입/로그인 시스템을 갖추면서, 데이터를 불러오기 전에
		// 먼저 로그인해야 함 (로그인해야 어떤 사용자의 데이터를 가져올지 알 수 있음)
		LoginDialog loginDialog = new LoginDialog(null);
		loginDialog.setVisible(true); // 모달이라 로그인 성공/창 닫기 전까지 여기서 대기

		if (!loginDialog.isSuccess()) {
			System.exit(0); // 로그인하지 않고 창을 닫으면 프로그램 종료
		}

		DataManager.loadAll(); // 로그인한 사용자의 데이터를 서버에서 불러오기

		// 종료 방식에 상관없이 항상 저장(종료 시 thread를 생성)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			DataManager.saveAll();
		}));

		SwingUtilities.invokeLater(MainFrame::new);
	}
}