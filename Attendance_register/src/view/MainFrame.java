package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainFrame extends JFrame{
	//직렬화 아이디 선언(gui에서는 사용하지 않음)
	private static final long serialVersionUID = 1L;
	
	private CardLayout cardLayout;
	private JPanel contentPanel;
	private JButton[] menuButtons; //메뉴 panel의 버튼 리스트
	private int currentIdx = 0; //현재 선택된 inx 추적
	
	//색깔들 미리 정의
	private static final Color BG =new Color(248, 249, 251); //기본 배경 색깔
	private static final Color WHITE = Color.white;
	private static final Color BLUE = new Color(37, 99, 235);
	private static final Color BORDER = new Color(229, 231, 235); //border 색깔
	private static final Color GRAY_TXT = new Color(107, 114, 128); //기본 글자 색깔
	private static final Color HOVER_BG = new Color(239, 246, 255); //마우스 호버 시 배경 색깔
	private static final Color HOVER_TXT = new Color(37, 99, 235); //마우스 호버 시 글자 색깔
	
	private final String[] MENU_LABELS = {"대시보드", "반 관리", "학생 관리", "출석 입력", "출석 달력", "엑셀 내보개기"}; //메뉴 이름들
	private final String[] CARD_KEYS = {"dashboard", "class", "student", "attendance", "calendar", "excel"}; //메뉴 구분 키
	private final String[] ICONS = {"■ ", "● ", "◆ ", "□ ", "◇ ", "▼ "}; //메뉴 아이콘
	
	//페이지 초기화를 위한 필드들
	private DashboardPanel      dashboardPanel;
	private ClassManagePanel    classPanel;
	private StudentManagePanel  studentPanel;
	private AttendanceInputPanel attendancePanel;
	private AttendanceCalendarPanel calendarPanel;
	private ExcelExportPanel excelPanel;

	public MainFrame() {
		super("학원 출석 관리 프로그램");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1100, 700);
		setLayout(new BorderLayout(0, 0));
		setBackground(BG);
		
		add(buildSidebar(), BorderLayout.WEST);
		add(buildContent(), BorderLayout.CENTER);
		
		switchCard(0);
		
		// 창 닫기 버튼 클릭 시 저장 후 종료
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0); // ShutdownHook → saveAll() 자동 호출
			}
		});
		
		setLocationRelativeTo(null); //null->모니터 화면 정중앙에 배치
		setVisible(true);
	}
	
	//사이드바 생성
	private JPanel buildSidebar() {
		JPanel sidebar = new JPanel(new BorderLayout());
		sidebar.setPreferredSize(new Dimension(215, 0)); //이상적으로 원하는 크기를 설정(크기가 부족하면 무시)
		sidebar.setBackground(WHITE);
		sidebar.setBorder(BorderFactory.createMatteBorder(0,  0,  0,  1,  BORDER)); //오른쪽에만 선을 그음, 색깔은 BORDER
		sidebar.add(buildLogo(), BorderLayout.NORTH);
		sidebar.add(buildMenuPanel(), BorderLayout.CENTER);
		return sidebar;
	}
	
	//좌측 상단 로고 생성
	private JPanel buildLogo() {
		JPanel logoPanel = new JPanel(new BorderLayout());
		logoPanel.setBackground(WHITE);
		logoPanel.setBorder(BorderFactory.createMatteBorder(0,  0,  1,  0,  BORDER)); //아래쪽에만 선을 그음, 색깔은 BORDER
		
		JLabel logo = new JLabel("<html><div style='line-height:1.6'>학원 출석<br>관리 프로그램</div></html>");
		logo.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		logo.setForeground(new Color(17, 24, 39));
		logo.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16)); //빈 border를 생성
		logoPanel.add(logo);
		return logoPanel;
	}
	
	//메뉴 panel 생성
	private JPanel buildMenuPanel() {
		JPanel menuPanel = new JPanel();
		menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
		menuPanel.setBackground(WHITE);
		menuPanel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8)); //빈 border 생성
		
		menuButtons = new JButton[MENU_LABELS.length]; //메뉴 개수만큼 메뉴 버튼 생성
	
		for(int i=0; i < MENU_LABELS.length; i++) {
			final int idx = i; //람다식에서 상수값만 가능하므로 상수 idx를 생성
			
			JButton btn = new JButton(ICONS[i] + MENU_LABELS[i]); //아이콘과 이름을 합쳐서 버튼 생성
		
			btn.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
			btn.setHorizontalAlignment(SwingConstants.LEFT); //버튼 텍스트 왼쪽 정렬
			btn.setBorderPainted(false); //기본 테두리 제거
			btn.setFocusPainted(false);// 클릭 시 발생하는 점선 포커스 표시 제거
			btn.setContentAreaFilled(false);  // L&F의 배경 자동 그리기 비활성화 (버튼을 눌렀을 때 눌린 느낌을 주기 위한 색변화 금지)
			btn.setOpaque(true); //배경색이 보이도록 설정 (안 하면 background는 투명하게 보인다.)
			
			btn.setBackground(WHITE);
			btn.setForeground(GRAY_TXT); //글자 색 지정
			btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); //버튼에 마우스를 올린다면 손가락 커서로 변경
			btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); //넓이는 최대로 늘어난다.
			btn.setBorder(BorderFactory.createEmptyBorder(8, 14, 9, 14));
			
			btn.addMouseListener(new MouseAdapter() {
				@Override //메뉴 클리 시 switchCard를 불러와 색상 변경
				public void mousePressed(MouseEvent e) {
					switchCard(idx);
				}
				@Override //마우스가 위에 있으면 HOVER 색깔로 변경
				public void mouseEntered(MouseEvent e) {
					if(idx != currentIdx) {
						btn.setBackground(HOVER_BG);
						btn.setForeground(HOVER_TXT);
					}
				}
				@Override //마우스가 위에 없다면 기본 색깔 유지
				public void mouseExited(MouseEvent e) {
					if(idx != currentIdx) {
						btn.setBackground(WHITE);
						btn.setForeground(GRAY_TXT);
					}
				}
			});
			
			menuButtons[i] = btn;
			menuPanel.add(btn);
			menuPanel.add(Box.createRigidArea(new Dimension(0, 2))); //버튼 사이의 간격 추가(2.ㅔㅌ)
		}
		
		return menuPanel;
	}
	
	//패널 선택 시 불러오는 페이지들 지정
	private JPanel buildContent() {
	    cardLayout = new CardLayout();
	    contentPanel = new JPanel(cardLayout);
	    contentPanel.setBackground(BG);
	    
	    //각 페이지 정의
	    dashboardPanel   = new DashboardPanel();
	    classPanel       = new ClassManagePanel();
	    studentPanel     = new StudentManagePanel();
	    attendancePanel  = new AttendanceInputPanel();
	    calendarPanel = new AttendanceCalendarPanel();
	    excelPanel = new ExcelExportPanel();

	    contentPanel.add(dashboardPanel,              "dashboard");
	    contentPanel.add(classPanel,                  "class");
	    contentPanel.add(studentPanel,                "student");
	    contentPanel.add(attendancePanel,             "attendance");
	    contentPanel.add(calendarPanel, "calendar");
	    contentPanel.add(new ExcelExportPanel(),        "excel");
	    contentPanel.add(excelPanel, "excel");
	    
	    return contentPanel;
	}
	
	//메뉴 전환
	private void switchCard(int idx) {
		currentIdx = idx;
		
		for (int i=0; i < menuButtons.length; i++) {
			if (i==idx) {
				menuButtons[i].setForeground(WHITE); //글자색을 먼저 지정함으로써 안보이는 오류 해결
				menuButtons[i].setBackground(BLUE); //메뉴가 선택 되면 파란색으로 설정
			} else {
				menuButtons[i].setBackground(WHITE); //메뉴가 선택되지 않으면 하얀색으로 설정
				menuButtons[i].setForeground(GRAY_TXT);
			}
		}
		cardLayout.show(contentPanel,  CARD_KEYS[idx]); //메뉴에 해당되는 페이지 띄우기
		
		 // 화면 전환 시 데이터 갱신
	    switch (idx) { //인덱스 번호를 통해서 해당 화면 초기화
	        case 0 -> dashboardPanel.refresh();
	        case 1 -> classPanel.refresh();
	        case 2 -> studentPanel.refresh();
	        case 3 -> attendancePanel.refresh();
	        case 4 -> calendarPanel.refresh();
	        case 5 -> excelPanel.refresh();
	    }
	}
}
