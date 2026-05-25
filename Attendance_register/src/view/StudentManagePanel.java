package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.*;
import model.*;

public class StudentManagePanel extends JPanel {
	//직렬화 아이디 선언(gui에서는 사용하지 않음)
	private static final long serialVersionUID = 1L;
	 
	// 색상 상수 (ClassManagePanel과 동일)
	private static final Color BG        = new Color(248, 249, 251);
	private static final Color WHITE     = Color.WHITE;
	private static final Color BLUE      = new Color(37,  99,  235);
	private static final Color BTN_HOVER = new Color(29,  78,  216);
	private static final Color BTN_PRESS = new Color(30,  64,  175);
	private static final Color GRAY_TXT  = new Color(107, 114, 128);
	
	//중단 테이릅 변수 선언
	private DefaultTableModel tableModel;
	private JTable table;
	
	public StudentManagePanel() {
		setLayout(new BorderLayout());
		setBackground(BG);
		setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));
 
		add(buildTitleBar(), BorderLayout.NORTH);
		add(buildTable(), BorderLayout.CENTER);
	}
 
	// 상단 타이틀바 (제목 + 학생 추가 버튼)
	private JPanel buildTitleBar() {
		JPanel titleBar = new JPanel(new BorderLayout());
		titleBar.setBackground(BG);
		titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
 
		// 제목
		JLabel title = new JLabel("학생 관리");
		title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
		title.setForeground(new Color(17, 24, 39));
 
		// 학생 추가 버튼
		JButton addBtn = new JButton("+ 학생 추가");
		addBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		addBtn.setBackground(BLUE);
		addBtn.setForeground(WHITE);
		addBtn.setContentAreaFilled(false); //L&F 배경 자동 그리기 금지
		addBtn.setOpaque(true); //배경색 보이기
		addBtn.setBorderPainted(false);
		addBtn.setFocusPainted(false);
		addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
		addBtn.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e)  { addBtn.setBackground(BTN_HOVER); }
			@Override public void mouseExited(MouseEvent e)   { addBtn.setBackground(BLUE);      }
			@Override public void mousePressed(MouseEvent e)  { addBtn.setBackground(BTN_PRESS); }
			@Override public void mouseReleased(MouseEvent e) { addBtn.setBackground(BTN_HOVER); }
		});
 
		titleBar.add(title,  BorderLayout.WEST);
		titleBar.add(addBtn, BorderLayout.EAST);
		return titleBar;
	}
	
	private JPanel buildTable() {
		//{...}, 초기 행수
		tableModel = new DefaultTableModel(
				new String[] {"학번", "이름", "학교", "반", "전화번호", "보호자 전화번호", "관리"}, 0) {
				@Override
				public boolean isCellEditable(int row, int col) {
					return false;
				}
		};
		
		//테이블 생성
		table = new JTable(tableModel);
		table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		table.setRowHeight(40);
		table.setGridColor(new Color(243, 244, 246));
		table.setShowVerticalLines(false); //수평 선 지우기
		table.setSelectionBackground(new Color(239, 246, 255));
		
		// 헤더 스타일
	    table.getTableHeader().setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	    table.getTableHeader().setBackground(new Color(250, 250, 250));
	    table.getTableHeader().setForeground(GRAY_TXT);
	    table.getTableHeader().setReorderingAllowed(false); //열 순서 드래그로 바꾸기 방지
	    
	    //셀 좌측 여백 렌더러
	    DefaultTableCellRenderer paddingRenderer = new DefaultTableCellRenderer() {
	    		@Override //상속 받아서 셀의 렌더링 방식을 바꿈
	    		public Component getTableCellRendererComponent(
	    				JTable t, Object val, boolean sel, boolean foc, int row, int col) {
	    			super.getTableCellRendererComponent(t, val, sel, foc, row, col);
	    			setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
	    			return this;
	    		}
	    };
	
	    //관리 열을 제외하고 여백 렌더러 적용
	    for (int i=0; i<6;i++) {
	    	table.getColumnModel().getColumn(i).setCellRenderer(paddingRenderer);
	    }
	    
	    //테이블 카드로 감싸기
	    JPanel card = new JPanel(new BorderLayout());
	    card.setBackground(WHITE);
	    card.setBorder(BorderFactory.createLineBorder(new Color(233, 236, 239), 1));
	    card.add(new JScrollPane(table), BorderLayout.CENTER);

	    refreshTable();
	    return card;
	}
	
	//데이터 갱신
	private void refreshTable() {
		//초기화
	    tableModel.setRowCount(0);
	    for (Student s : DataManager.getStudent()) {
	    	//반 검색
	        Class_Info cls = DataManager.findClassById(s.getClass_Id());
	        //반이 존재하면 반 이름을 가져온다(아닌 경우 "-"로 표시)
	        String className = (cls != null) ? cls.getClass_Name() : "-";
	        //줄 추가
	        tableModel.addRow(new Object[] {
	            s.getStudent_id(),
	            s.getName(),
	            s.getSchool(),
	            className,
	            s.getStudent_Phone()  == null ? "" : s.getStudent_Phone(),
	            s.getGuardian_Phone() == null ? "" : s.getGuardian_Phone(),
	            ""
	        });
	    }
	}
}
