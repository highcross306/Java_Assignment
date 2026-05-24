package view;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import model.*;

public class ClassManagePanel extends JPanel{
	private static final Color BTN_NORMAL = new Color(37,  99,  235); // 기본 파란색
	private static final Color BTN_HOVER  = new Color(29,  78,  216); // hover 시 진한 파란색
	private static final Color BTN_PRESS  = new Color(30,  64,  175); // 클릭 시 더 진한 파란색
	private DefaultTableModel tableModel;
	private JTable table;       // 필드로 선언 (repaint 호출에 필요)
	private int hoverRow = -1;  // 마우스가 올라간 행
	private int hoverBtn = -1;  // 0 = 수정, 1 = 삭제, -1 = 없음 (마우스 상태 확인용 필드)
	
	public ClassManagePanel() {
		//전체 영역 설정
		setLayout(new BorderLayout());
		setBackground(new Color(248, 249, 251));
		setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));
		
		add(buildTitleBar(), BorderLayout.NORTH); //상단-타이틀
		add(buildTable(),    BorderLayout.CENTER); //중단-표
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
		//table 생성(반 ID, 반 이름, 학생 수, 관리)
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
	}
	
	private void showAddDialog() {
		
	}
	
	private void showEditDialog(int row) {
		
	}
	
	private void showDeleteDialog(int row) {
		
	}
}
