package main;
 
import view.MainFrame;
import model.DataManager;
import javax.swing.*;
 
public class Main {
	public static void main(String[] args) {
		DataManager.loadAll(); // CSV 파일 읽기
		
		// 종료 방식에 상관없이 항상 저장(종료 시 thread를 생성)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			DataManager.saveAll();
		}));
		
		SwingUtilities.invokeLater(MainFrame::new);
	}
}