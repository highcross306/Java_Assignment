package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.*;
import model.*;

//borderfactory는 컴퍼넌트의 테두리를 만둘때 많이 쓰인다.

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
	
	//과리열 상태 필드
	private int hoverRow = -1; //0=수정, 1=삭제
	private int hoverBtn = -1;
	
	//다이얼로그 크기 필드
	private final int Dialog_weight = 400;
	private final int Dialog_height = 450;
	
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
		addBtn.addActionListener(e -> showAddDialog());
 
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
		table.setRowHeight(55);
		table.setGridColor(new Color(243, 244, 246));
		table.setShowVerticalLines(false); //수평 선 지우기
		table.setSelectionBackground(new Color(239, 246, 255));
		
		// 헤더 스타일
	    table.getTableHeader().setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	    table.getTableHeader().setBackground(new Color(250, 250, 250));
	    table.getTableHeader().setForeground(GRAY_TXT);
	    table.getTableHeader().setReorderingAllowed(false); //열 순서 드래그로 바꾸기 방지
	    
	    // 열 너비 설정
	    table.getColumnModel().getColumn(0).setPreferredWidth(45);  // 학번
	    table.getColumnModel().getColumn(0).setMaxWidth(45);        // 학번 최대 너비 고정
	    table.getColumnModel().getColumn(1).setPreferredWidth(70);  // 이름
	    table.getColumnModel().getColumn(6).setPreferredWidth(160); // 관리
	    
	    //테이블 자동 열 너비 조정 방지
	    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
	    
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
	    
	 // 관리 열 렌더러 (수정 / 삭제 버튼)
	    //관리 열 선택
	    table.getColumnModel().getColumn(6).setCellRenderer(
	        (t, val, sel, foc, row, col) -> { //랍다 식으로 렌더러 입력 바기
	            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 15));
	            p.setBackground(sel ? new Color(239, 246, 255) : WHITE);
	            
	            //수정 버튼 생성
	            JButton editBtn = new JButton("수정");
	            editBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	            editBtn.setFocusPainted(false);
	            editBtn.setOpaque(true);
	            editBtn.setBorderPainted(true);
	            
	            //삭제 버튼 생성
	            JButton delBtn = new JButton("삭제");
	            delBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	            delBtn.setFocusPainted(false);
	            delBtn.setOpaque(true);
	            delBtn.setBorderPainted(true);

	            // hover 상태에 따른 색상
	            if (row == hoverRow && hoverBtn == 0) { //수정 버튼일때의 색깔 변화
	                editBtn.setBackground(new Color(229, 231, 235));
	                editBtn.setForeground(new Color(17, 24, 39));
	            } else {
	                editBtn.setBackground(new Color(249, 250, 251));
	                editBtn.setForeground(new Color(55, 65, 81));
	            }
	            if (row == hoverRow && hoverBtn == 1) { //삭제 버튼 일때의 색깔 변화
	                delBtn.setBackground(new Color(254, 226, 226));
	                delBtn.setForeground(new Color(185, 28, 28));
	            } else {
	                delBtn.setBackground(WHITE);
	                delBtn.setForeground(new Color(220, 38, 38));
	            }
	            	
	            //패널에 버튼 추가 후 패널을 반환
	            p.add(editBtn);
	            p.add(delBtn);
	            return p;
	        }
	    );

	    // 마우스 움직임 → hover 갱신
	    table.addMouseMotionListener(new MouseMotionAdapter() { //마우스 이동 감지
	        @Override
	        public void mouseMoved(MouseEvent e) {
	            int row = table.rowAtPoint(e.getPoint());
	            int col = table.columnAtPoint(e.getPoint());
	            if (col == 6) { //관리 열에서 x좌표를 불러와 좌표에 따라서 어떤 버튼 위에 마우스가 있는지 검색
	                int localX = e.getX() - table.getCellRect(row, col, true).x;
	                hoverRow = row;
	                hoverBtn = (localX < 60) ? 0 : 1;
	            } else { //마우스가 버튼 위에 있지 않을 경울 두 값은 -1로 고정
	                hoverRow = -1;
	                hoverBtn = -1;
	            }
	            table.repaint();
	        }
	    });

	    // 마우스 벗어남 → hover 초기화
	    table.addMouseListener(new MouseAdapter() { //마우스 버튼 동작 감지
	        @Override
	        public void mouseExited(MouseEvent e) {
	            hoverRow = -1;
	            hoverBtn = -1;
	            table.repaint();
	        }
	        @Override
	        public void mouseClicked(MouseEvent e) {
	            int row = table.rowAtPoint(e.getPoint());
	            int col = table.columnAtPoint(e.getPoint());
	            if (col == 6) {
	                int localX = e.getX() - table.getCellRect(row, col, true).x;
	                if (localX < 60) showEditDialog(row);
	                else             showDeleteDialog(row);
	            }
	        }
	    });
	    
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
	//학생 정보 추가 창
	private void showAddDialog() {
		//부모 창과 연동, 부모 창 선택 불가
	    JDialog dialog = new JDialog(
	        (Frame) SwingUtilities.getWindowAncestor(this), "학생 추가", true);
	    dialog.setSize(Dialog_weight, Dialog_height);
	    dialog.setLocationRelativeTo(this);
	    dialog.setLayout(new BorderLayout());
	    dialog.add(buildFormPanel(null), BorderLayout.CENTER);
	    dialog.setVisible(true);
	}
	
	//학생 정보 수정 창
	private void showEditDialog(int row) {
	    Student target = DataManager.getStudent().get(row);
	    //부모 창과 연동, 부모 창 선택 불가
	    JDialog dialog = new JDialog(
	        (Frame) SwingUtilities.getWindowAncestor(this), "학생 수정", true);
	    dialog.setSize(Dialog_weight, Dialog_height);
	    dialog.setLocationRelativeTo(this);
	    dialog.setLayout(new BorderLayout());
	    dialog.add(buildFormPanel(target), BorderLayout.CENTER);
	    dialog.setVisible(true);
	}
	
	//학생 정보 삭제 창
	private void showDeleteDialog(int row) {
	    Student target = DataManager.getStudent().get(row);
	    int result = JOptionPane.showConfirmDialog( //경고 창 띄우기
	        this,
	        "'" + target.getName() + "' 학생을 삭제하시겠습니까?",
	        "학생 삭제",
	        JOptionPane.YES_NO_OPTION,
	        JOptionPane.WARNING_MESSAGE
	    );
	    if (result == JOptionPane.YES_OPTION) { //학생 정보 지우기
	        DataManager.getStudent().removeIf(
	            s -> s.getStudent_id() == target.getStudent_id());
	        refreshTable();
	    }
	}
	
	//학생 정보 입력 폼
	private JPanel buildFormPanel(Student student) {
	    JPanel body = new JPanel();
	    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
	    body.setBackground(WHITE);
	    body.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

	    JTextField nameField = makeTextField(student != null ? student.getName() : ""); //이름
	    JTextField schoolField = makeTextField(student != null ? student.getSchool() : ""); //학교
	    JTextField phoneField = makeTextField(student != null ? //전화번호
	        (student.getStudent_Phone() != null ? student.getStudent_Phone() : "") : "");
	    JTextField guardianField = makeTextField(student != null ? //보호자 전화번호
	        (student.getGuardian_Phone() != null ? student.getGuardian_Phone() : "") : "");

	    JComboBox<String> classCombo = new JComboBox<>(); //반(콤보 박스의 형태로 입력)
	    classCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
	    classCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
	    for (Class_Info cls : DataManager.getClasses()) {
	        classCombo.addItem(cls.getClass_Name());
	        if (student != null && cls.getClass_Id() == student.getClass_Id()) {
	            classCombo.setSelectedItem(cls.getClass_Name());
	        }
	    }
	    
	    //텍스트, 텍트스 필드를 입력해서 각각의 항목들을 생성
	    body.add(makeFieldGroup("이름", nameField));
	    body.add(Box.createRigidArea(new Dimension(0, 10)));
	    body.add(makeFieldGroup("학교", schoolField));
	    body.add(Box.createRigidArea(new Dimension(0, 10)));
	    body.add(makeFieldGroup("반", classCombo));
	    body.add(Box.createRigidArea(new Dimension(0, 10)));
	    body.add(makeFieldGroup("전화번호", phoneField));
	    body.add(Box.createRigidArea(new Dimension(0, 10)));
	    body.add(makeFieldGroup("보호자 전화번호", guardianField));

	    return body;
	}
	
	//입력 폼에서 레이블 만들기
	private JLabel makeLabel(String text) {
	    JLabel lbl = new JLabel(text);
	    lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	    lbl.setForeground(GRAY_TXT);
	    lbl.setAlignmentX(Component.LEFT_ALIGNMENT); // 왼쪽 정렬
	    //레이블 반환
	    return lbl;
	}
	
	//입력 폼에서 정보 입력 텍스트 필드 만들기
	private JTextField makeTextField(String value) {
	    JTextField field = new JTextField(value);
	    field.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
	    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
	    field.setBorder(BorderFactory.createCompoundBorder(
	        BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
	        BorderFactory.createEmptyBorder(6, 10, 6, 10)));
	    //필드 반환
	    return field;
	}
	
	//각 입력 항목 생성
	private JPanel makeFieldGroup(String labelText, JComponent field) {
	    JPanel group = new JPanel();
	    group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
	    group.setBackground(Color.WHITE);
	    group.setBorder(BorderFactory.createCompoundBorder(
	        BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
	        BorderFactory.createEmptyBorder(8, 10, 8, 10)
	    ));
	    group.setAlignmentX(Component.LEFT_ALIGNMENT); //왼쪽 정렬
	    group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65)); 

	    JLabel lbl = makeLabel(labelText); //텍스트 레이블 생성
	    lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
	    field.setAlignmentX(Component.LEFT_ALIGNMENT);

	    group.add(lbl); //첵스트 레이블 항목에 추가
	    group.add(Box.createRigidArea(new Dimension(0, 4)));
	    group.add(field); //텍스트 필드 항목에 추가
	    // 항목 반환
	    return group;
	}
}
