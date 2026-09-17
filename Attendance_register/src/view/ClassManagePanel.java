package view;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import model.*;

public class ClassManagePanel extends JPanel{
	//직렬화 아이디 선언(gui에서는 사용하지 않음)
	private static final long serialVersionUID = 1L;
		
	private static final Color BTN_NORMAL = new Color(37,  99,  235); // 기본 파란색
	private static final Color BTN_HOVER  = new Color(29,  78,  216); // hover 시 진한 파란색
	private static final Color BTN_PRESS  = new Color(30,  64,  175); // 클릭 시 더 진한 파란색
	private DefaultTableModel tableModel;
	private JTable table;       // 필드로 선언 (repaint 호출에 필요)
	private int hoverRow = -1;  // 마우스가 올라간 행
	private int hoverBtn = -1;  // 0 = 수정, 1 = 삭제, -1 = 없음 (마우스 상태 확인용 필드)
	private JButton addBtn;     // 데모 상태에서 반 개수 제한에 걸리면 비활성화하기 위해 필드로 보관
	
	//통계 자료 레이블
	private JLabel classCountLabel; //전체 반 수
	private JLabel studentCountLabel; //전체 학생 수
	
	public ClassManagePanel() {
		//전체 영역 설정
		setLayout(new BorderLayout());
		setBackground(new Color(248, 249, 251));
		setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));
		
		add(buildTitleBar(), BorderLayout.NORTH); //상단-타이틀
		add(buildTable(),    BorderLayout.CENTER); //중단-표
		add(buildStats(),    BorderLayout.SOUTH); //하단-통계 자료
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
		addBtn = new JButton("+ 반 추가");
		addBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
	    addBtn.setBackground(new Color(37, 99, 235));
	    addBtn.setForeground(Color.WHITE);
	    addBtn.setContentAreaFilled(false); //L&F pressed 효과 제거(배경 자동 그리기 끄기)
	    addBtn.setOpaque(true); //배경색이 보이도록 설정
	    addBtn.setBorderPainted(false);
	    addBtn.setFocusPainted(false);
	    addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); //손가락 커서로 변경
	    addBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14)); //버튼 내부 여백
	    addBtn.addActionListener(e -> { //데모 상태에서 제한에 걸리면 다이얼로그 자체를 열지 않음
	        if (isClassLimitReached()) {
	            JOptionPane.showMessageDialog(this,
	                "데모 상태에서는 반을 " + DataManager.getMaxClasses() + "개까지만 만들 수 있습니다.\n결제 후 이용해주세요.",
	                "데모 제한", JOptionPane.WARNING_MESSAGE);
	            return;
	        }
	        showAddDialog();
	    });
	    addBtn.addMouseListener(new MouseAdapter() {
	        @Override
	        public void mouseEntered(MouseEvent e) {
	            addBtn.setBackground(BTN_HOVER);  // 마우스 올리면 진한 파란색
	        }

	        @Override
	        public void mouseExited(MouseEvent e) {
	            addBtn.setBackground(BTN_NORMAL); // 마우스 나가면 원래 파란색
	        }

	        @Override
	        public void mousePressed(MouseEvent e) {
	            addBtn.setBackground(BTN_PRESS);  // 누르는 순간 더 진한 파란색
	        }

	        @Override
	        public void mouseReleased(MouseEvent e) {
	            addBtn.setBackground(BTN_HOVER);  // 떼면 hover 색으로 복귀
	        }
	    });

	    titleBar.add(title,  BorderLayout.WEST);
	    titleBar.add(addBtn, BorderLayout.EAST);
	    return titleBar;
	}
	
	private JPanel buildTable() {
		//table 생성(반 ID, 반 이름, 학생 수, 관리), 초기행수
		tableModel = new DefaultTableModel(
			new String[] {"반 ID", "반 이름", "학생 수", "관리"}, 0) {
			@Override
			public boolean isCellEditable(int row, int col) {
				return false; //셀 직접 수정 금지
			}
		};
		//table 설정
		table = new JTable(tableModel);
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
		
		// 셀 텍스트 왼쪽 여백 추가
		DefaultTableCellRenderer paddingRenderer = new DefaultTableCellRenderer() {
		    @Override
		    public Component getTableCellRendererComponent( //랜더러 가져오기
		            JTable t, Object val, boolean sel, boolean foc, int row, int col) {
		        super.getTableCellRendererComponent(t, val, sel, foc, row, col); //super사용해서 cellRender 변경
		        setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0)); // 좌우 14px 여백
		        return this;
		    }
		};

		// 관리 열(3번) 제외하고 나머지 열에 적용
		table.getColumnModel().getColumn(0).setCellRenderer(paddingRenderer);
		table.getColumnModel().getColumn(1).setCellRenderer(paddingRenderer);
		table.getColumnModel().getColumn(2).setCellRenderer(paddingRenderer);
		
		//관리 열 생성
		table.getColumnModel().getColumn(3).setCellRenderer( //관리 데이터를 가져오기
				(t, val, sel, foc, row, col) ->{ //현재 테이블, 셀의 값, 셀 선택 여부, 셀 포커스 상태 여부, 행 번호, 열 번호 //추상 메소드를 람다식으로 불러오기
					JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
					p.setBackground(sel ? new Color(238, 246, 255) : Color.white); //선택된 행이면 파란 배경, 아니면 흰색
					
					 JButton editBtn = new JButton("수정");
			         editBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			         editBtn.setBackground(new Color(249, 250, 251));
			         editBtn.setForeground(new Color(55, 65, 81));
			         editBtn.setOpaque(true); //배경색 보이기
			         editBtn.setBorderPainted(true);
			         editBtn.setFocusPainted(false);

			         JButton delBtn = new JButton("삭제");
			         delBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			         delBtn.setBackground(Color.WHITE);
			         delBtn.setForeground(new Color(220, 38, 38));
			         delBtn.setOpaque(true); //배경색 보이기
			         delBtn.setBorderPainted(true);
			         delBtn.setFocusPainted(false);
			         
			      // hover 상태에 따라 색상 변경
			         if (row == hoverRow && hoverBtn == 0) {
			             editBtn.setBackground(new Color(229, 231, 235)); // 수정 hover
			             editBtn.setForeground(new Color(17, 24, 39));
			         } else {
			             editBtn.setBackground(new Color(249, 250, 251)); // 수정 기본
			             editBtn.setForeground(new Color(55, 65, 81));
			         }

			         if (row == hoverRow && hoverBtn == 1) {
			             delBtn.setBackground(new Color(254, 226, 226)); // 삭제 hover
			             delBtn.setForeground(new Color(185, 28, 28));
			         } else {
			             delBtn.setBackground(Color.WHITE); // 삭제 기본
			             delBtn.setForeground(new Color(220, 38, 38));
			         }


			         p.add(editBtn);
			         p.add(delBtn);
			         return p;
				}
		);
		
		// 마우스 움직임 감지 → hover 상태 갱신
		table.addMouseMotionListener(new MouseMotionAdapter() {
		    @Override
		    public void mouseMoved(MouseEvent e) {
		        int col = table.columnAtPoint(e.getPoint());
		        int row = table.rowAtPoint(e.getPoint());

		        if (col == 3) {
		            int localX = e.getX() - table.getCellRect(row, col, true).x;
		            hoverRow = row;
		            hoverBtn = (localX < 60) ? 0 : 1; // 0=수정, 1=삭제
		        } else {
		            hoverRow = -1;
		            hoverBtn = -1;
		        }
		        table.repaint(); // 색상 변경 즉시 반영
		    }
		});
		
		// 관리 열 마우스 클릭 이벤트
		table.addMouseListener(new MouseAdapter() {
			@Override //마우스 없으면 상태 hoverBtn -1로 변경
		    public void mouseExited(MouseEvent e) {
		        hoverRow = -1;
		        hoverBtn = -1;
		        table.repaint();
		    }
			@Override
			public void mouseClicked(MouseEvent e) {
				int col = table.columnAtPoint(e.getPoint());
				int row = table.rowAtPoint(e.getPoint());
				
				if (col == 3) {
					int localX = e.getX() - table.getCellRect(row, col, true).x; //렌더러로 그림을 그려서 진행하므로, 관리 열의 x좌표 값을 불러와서 계산
					if (localX < 60) {
						showEditDialog(row);
					}else {
						showDeleteDialog(row);
					}
				}
			}
		});
		
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
		
		// 통계 카드 갱신
	    if (classCountLabel != null) {
	        classCountLabel.setText(String.valueOf(DataManager.getClasses().size()));
	        studentCountLabel.setText(String.valueOf(DataManager.getStudent().size()));
	    }

	    updateAddButtonState();
	}

	// 데모 상태에서 이미 반 개수 제한에 도달했는지 확인
	private boolean isClassLimitReached() {
		return DataManager.isDemo() && DataManager.getMaxClasses() != null
				&& DataManager.getClasses().size() >= DataManager.getMaxClasses();
	}

	// 제한에 도달하면 "+ 반 추가" 버튼을 비활성화해서 못 누르게 함
	private void updateAddButtonState() {
		if (addBtn == null) return;
		boolean limitReached = isClassLimitReached();
		addBtn.setEnabled(!limitReached);
		addBtn.setToolTipText(limitReached
				? "데모 상태에서는 반을 " + DataManager.getMaxClasses() + "개까지만 만들 수 있습니다"
				: null);
	}
	
	//추가 다이어로그
	private void showAddDialog() {
		//dialog 생성, 부모 창을 반환해서, 부모 창과 연동을 한다. true이므로 부모 창 클릭 불가
		JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "반 추가", true);
		dialog.setSize(280, 180);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());
		
		//입력 영역
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Color.WHITE);
		body.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
		
		//반 이름 레이블
		JLabel nameLabel = new JLabel("반 이름");
		nameLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		nameLabel.setForeground(new Color(107, 114, 128));
		
		//반 이름 입력 상자
		JTextField nameField = new JTextField();
		nameField.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		nameField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(209, 213, 219), 1), 
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		
		body.add(nameLabel);
		body.add(Box.createRigidArea(new Dimension(0, 4)));
		body.add(nameField);
		
		//버튼 영역
		JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
		btnBar.setBackground(Color.WHITE);
		btnBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(243, 244, 246)));
		
		//취소 버튼
		JButton cancelBtn = new JButton("취소");
		cancelBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		cancelBtn.setFocusPainted(false);
		cancelBtn.addActionListener(e->dialog.dispose()); //누르면 창 닫기
		
		//저장 버튼
		JButton saveBtn = new JButton("저장");
	    saveBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
	    saveBtn.setBackground(new Color(37, 99, 235));
	    saveBtn.setForeground(Color.WHITE);
	    saveBtn.setOpaque(true);
	    saveBtn.setBorderPainted(false);
	    saveBtn.setFocusPainted(false);
	    saveBtn.addActionListener(e -> {
	        String name = nameField.getText().trim(); //JTextField에 입력 된 문자려, 공백을 잘라서 반환

	        // 빈 값 검사
	        if (name.isEmpty()) {
	            JOptionPane.showMessageDialog(dialog,
	                "반 이름을 입력하세요.", "입력 오류",
	                JOptionPane.WARNING_MESSAGE);
	            return;
	        }

	        // 새 반 ID = 현재 반 수 + 1
	        int newId = DataManager.getClasses().size() + 1;
	        DataManager.getClasses().add(new Class_Info(newId, name));
	        List<String> errors = DataManager.saveAll(); // 즉시 저장
	        refreshTable();   // 테이블 갱신 (실패한 항목은 저장 시 자동으로 되돌려짐)
	        if (!errors.isEmpty()) {
	            JOptionPane.showMessageDialog(dialog,
	                String.join("\n", errors), "저장 실패", JOptionPane.ERROR_MESSAGE);
	            return; // 다이얼로그를 닫지 않고 사용자가 확인할 수 있게 함
	        }
	        dialog.dispose();
	    });

	    btnBar.add(cancelBtn);
	    btnBar.add(saveBtn);

	    dialog.add(body,   BorderLayout.CENTER);
	    dialog.add(btnBar, BorderLayout.SOUTH);
	    dialog.setVisible(true);
	}
	
	private void showEditDialog(int row) {
	    // 선택한 행에서 현재 데이터 가져오기
	    int classId   = (int) tableModel.getValueAt(row, 0);
	    String curName = (String) tableModel.getValueAt(row, 1);
	    
	    //반 정보 수정 창
	    JDialog dialog = new JDialog(
	        (Frame) SwingUtilities.getWindowAncestor(this), "반 수정", true);
	    dialog.setSize(280, 180);
	    dialog.setLocationRelativeTo(this);
	    dialog.setLayout(new BorderLayout());

	    // 입력 영역
	    JPanel body = new JPanel();
	    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
	    body.setBackground(Color.WHITE);
	    body.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
	    
	    //반 이름 레이블
	    JLabel nameLabel = new JLabel("반 이름");
	    nameLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	    nameLabel.setForeground(new Color(107, 114, 128));
	    
	    //반 이름 입력 상자
	    JTextField nameField = new JTextField(curName); // 현재 이름 미리 입력
	    nameField.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
	    nameField.setBorder(BorderFactory.createCompoundBorder(
	        BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
	        BorderFactory.createEmptyBorder(6, 10, 6, 10)));

	    body.add(nameLabel);
	    body.add(Box.createRigidArea(new Dimension(0, 4)));
	    body.add(nameField);

	    // 버튼 영역
	    JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
	    btnBar.setBackground(Color.WHITE);
	    btnBar.setBorder(BorderFactory.createMatteBorder(
	        1, 0, 0, 0, new Color(243, 244, 246)));
	    
	    //취소 버튼
	    JButton cancelBtn = new JButton("취소");
	    cancelBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
	    cancelBtn.setFocusPainted(false);
	    cancelBtn.addActionListener(e -> dialog.dispose());
	    
	    //저장 버튼
	    JButton saveBtn = new JButton("저장");
	    saveBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
	    saveBtn.setBackground(new Color(37, 99, 235));
	    saveBtn.setForeground(Color.WHITE);
	    saveBtn.setOpaque(true); //배경색 보이게 하기
	    saveBtn.setBorderPainted(false);
	    saveBtn.setFocusPainted(false);
	    saveBtn.addActionListener(e -> {
	        String name = nameField.getText().trim(); //JTextField에 입력 된 문자려, 공백을 잘라서 반환

	        // 빈 값 검사
	        if (name.isEmpty()) {
	            JOptionPane.showMessageDialog(dialog,
	                "반 이름을 입력하세요.", "입력 오류",
	                JOptionPane.WARNING_MESSAGE);
	            return;
	        }

	        // DataManager 에서 해당 반 찾아서 이름 수정
	        Class_Info cls = DataManager.findClassById(classId);
	        if (cls != null) {
	            cls.setClass_Name(name);
	        }
	        List<String> errors = DataManager.saveAll(); // 즉시 저장
	        refreshTable();   // 테이블 갱신
	        if (!errors.isEmpty()) {
	            JOptionPane.showMessageDialog(dialog,
	                String.join("\n", errors), "저장 실패", JOptionPane.ERROR_MESSAGE);
	            return;
	        }
	        dialog.dispose();
	    });

	    btnBar.add(cancelBtn);
	    btnBar.add(saveBtn);

	    dialog.add(body,   BorderLayout.CENTER);
	    dialog.add(btnBar, BorderLayout.SOUTH);
	    dialog.setVisible(true);
	}
	
	private void showDeleteDialog(int row) {
	    // 선택한 행에서 현재 데이터 가져오기
	    int classId    = (int) tableModel.getValueAt(row, 0);
	    String className = (String) tableModel.getValueAt(row, 1);

	    // 확인 다이얼로그, [예], [아니요] 버튼 구성
	    int result = JOptionPane.showConfirmDialog(
	        this,
	        "'" + className + "' 반을 삭제하시겠습니까?\n" +
	        "해당 반의 학생 데이터도 함께 삭제됩니다.",
	        "반 삭제",
	        JOptionPane.YES_NO_OPTION, //[예](0 반환), [아니요](1 반환)
	        JOptionPane.WARNING_MESSAGE
	    );

	    if (result == JOptionPane.YES_OPTION) { //삭제 다이얼로그에서 '예'를 눌렀을 때
	        // 해당 반 학생 먼저 삭제
	        DataManager.getStudent().removeIf(
	            s -> s.getClass_Id() == classId
	        );

	        // 반 삭제
	        DataManager.getClasses().removeIf(
	            cls -> cls.getClass_Id() == classId
	        );
	        List<String> errors = DataManager.saveAll(); // CSV로 즉시 저장
	        refreshTable(); // 테이블 갱신
	        if (!errors.isEmpty()) {
	            JOptionPane.showMessageDialog(this,
	                String.join("\n", errors), "삭제 실패", JOptionPane.ERROR_MESSAGE);
	        }
	    }
	}
	
	//통계 자료 추출하기
	private JPanel buildStats() {
	    JPanel statsPanel = new JPanel(new GridLayout(1, 2, 14, 0));
	    statsPanel.setBackground(new Color(248, 249, 251));
	    statsPanel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

	    // 레이블을 필드에 통계 값 저장
	    classCountLabel   = new JLabel(String.valueOf(DataManager.getClasses().size()));
	    studentCountLabel = new JLabel(String.valueOf(DataManager.getStudent().size()));

	    classCountLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
	    classCountLabel.setForeground(new Color(29, 78, 216));

	    studentCountLabel.setFont(new Font("맑은 고딕", Font.BOLD, 20));
	    studentCountLabel.setForeground(new Color(21, 128, 61));
	    
	    //통계 카드로 값 전달
	    statsPanel.add(buildStatCard("전체 반 수",   classCountLabel,
	        new Color(239, 246, 255)));
	    statsPanel.add(buildStatCard("전체 학생 수", studentCountLabel,
	        new Color(240, 253, 244)));

	    return statsPanel;
	}
	
	//통계 카드 생성
	private JPanel buildStatCard(String label, JLabel valueLabel, Color iconBg) {
	    JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 14));
	    card.setBackground(Color.WHITE);
	    card.setBorder(BorderFactory.createLineBorder(new Color(243, 244, 246), 1));
	    
	    //색상 박스
	    JPanel iconBox = new JPanel();
	    iconBox.setBackground(iconBg);
	    iconBox.setPreferredSize(new Dimension(38, 38));
	    
	    //제이블과 숫자를 세로로 쌓는 패널
	    JPanel info = new JPanel();
	    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
	    info.setBackground(Color.WHITE);

	    //카드 위의 설명 텍스트
	    JLabel labelComp = new JLabel(label);
	    labelComp.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
	    labelComp.setForeground(new Color(107, 114, 128));

	    info.add(labelComp);
	    info.add(valueLabel);  // 필드 레이블 직접 추가

	    card.add(iconBox);
	    card.add(info);
	    return card;
	}
	
	//메인에서 쓸 refresh 메소드
    public void refresh() {
        refreshTable();
    }
}
