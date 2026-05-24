package view;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import model.*;

public class ClassManagePanel extends JPanel{
	private DefaultTableModel tableModel;
	
	public ClassManagePanel() {
		//전체 영역 설정
		setLayout(new BorderLayout());
		setBackground(new Color(248, 249, 251));
		setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));
		
		add(buildTitleBar(), BorderLayout.NORTH);
	}
	
	private JPanel buildTitleBar() {
		JPanel titleBar = new JPanel(new BorderLayout());
		titleBar.setBackground(new Color(248, 249, 251));
		titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0)); //아래 여백 생성
		
		//제목 레이블 구현
		JLabel title = new JLabel("반 관리");
		title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
		title.setForeground(new Color(17, 24, 39));
		
		//반생성 추가 버튼 구현
		JButton addBtn = new JButton("+ 반 추가");
		addBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
	    addBtn.setBackground(new Color(37, 99, 235));
	    addBtn.setForeground(Color.WHITE);
	    addBtn.setContentAreaFilled(false); //L&F pressed 효과 제거(배경 자동 그리기 끄기)
	    addBtn.setOpaque(true); //배경색이 보이도록 설정
	    addBtn.setBorderPainted(false);
	    addBtn.setFocusPainted(false);
	    addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); //손가락 커서로 변경
	    addBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14)); //버튼 내부 여백
	    addBtn.addActionListener(e -> showAddDialog()); // 나중에 구현

	    titleBar.add(title,  BorderLayout.WEST);
	    titleBar.add(addBtn, BorderLayout.EAST);
	    return titleBar;
	}
	
	private JPanel buildTable() {
		//table 생성(반 ID, 반 이름, 학생 수, 관리)
		tableModel = new DefaultTableModel(
			new String[] {"반 ID", "반 이름", "학생 수", "관리"}, 0) {
			@Override
			public boolean isCellEditable(int row, int col) {
				return false; //셀 직접 수정 금지
			}
		};
		//table 설정
		JTable table = new JTable(tableModel);
		table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		table.setRowHeight(40); //row 높이 설정
		table.setGridColor(new Color(243, 244, 255));
		table.setShowVerticalLines(false); //수평 선 안보이게 하기
		table.setSelectionBackground(new Color(239, 246, 255));
		
		//헤더 스타일
		table.getTableHeader().setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		table.getTableHeader().setBackground(new Color(250, 250, 250));
		table.getTableHeader().setForeground(new Color(107, 114, 128));
		table.getTableHeader().setReorderingAllowed(false); //열 순서 변경 금지
		
		//카드 패널로 감싸기
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(Color.WHITE);
		card.setBorder(BorderFactory.createLineBorder(new Color(233, 236, 239), 1));
		
		card.add(new JScrollPane(table), BorderLayout.CENTER); //데이터가 많으면 스크롤바 자동으로 생성
		
		refreshTable(); //DataManger에서 데이터 불러오기
		return card;
	}
	
	private void refreshTable() {
		//전체 초기화
		tableModel.setRowCount(0);
		//각 반에 대하여
		for (Class_Info cls : DataManager.getClasses()) {
			int studentCount = 0;
			//각 학생에 대하여
			for (Student s : DataManager.getStudent()) {
				if (s.getClass_Id() == cls.getClass_Id()) studentCount++;
			}
			//줄 생성
			tableModel.addRow(new Object[] {
					cls.getClass_Id(),
					cls.getClass_Name(),
					studentCount + "명",
					"" //관리 버튼은 다음 단계에 추가
			});
		}
	}
	
	private void showAddDialog() {
		
	}
}
