package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import main.Main;
import model.DataManager;
import server.BackupManager;
import server.ServerLauncher;

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
		sidebar.add(buildRefreshButton(), BorderLayout.SOUTH); //하단에 새로고침 버튼 추가
		return sidebar;
	}
	
	private JMenuItem settingsItem(String text, ActionListener action) {
		JMenuItem item = new JMenuItem(text);
		item.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
		item.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
		item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		item.addActionListener(action);
		return item;
	}

	//새로고침 버튼 + 설정 버튼(설정 메뉴: 계정/연결 정보/백업/동기화/라이선스/로그아웃) 생성
	private JPanel buildRefreshButton() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(WHITE);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));

		JButton refreshBtn = new JButton("⟳ 새로고침");
		refreshBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		refreshBtn.setForeground(WHITE);
		refreshBtn.setBackground(BLUE);
		refreshBtn.setFocusPainted(false);
		refreshBtn.setBorderPainted(false);
		refreshBtn.setOpaque(true);
		refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		refreshBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		refreshBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		refreshBtn.addActionListener(e -> refreshCurrentPanel()); //클릭 시 현재 화면 새로고침

		// 새로고침을 뺀 나머지 기능(계정, 연결 정보, 백업, 동기화, 라이선스, 로그아웃)은 "설정" 버튼 하나로 묶고,
		// 누르면 앱의 환경설정처럼 세로 메뉴가 펼쳐진다. 버튼이 화면 맨 아래라서 메뉴는 위쪽으로 열린다
		JPopupMenu settingsMenu = new JPopupMenu();
		JMenuItem accountItem = new JMenuItem(DataManager.getCurrentUsername() + "님");
		accountItem.setEnabled(false); // 로그인한 계정 표시용 (누를 수 없음)
		accountItem.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		settingsMenu.add(accountItem);
		settingsMenu.addSeparator();
		settingsMenu.add(settingsItem("Android 연결 정보", e -> showAndroidConnectInfo()));
		settingsMenu.add(settingsItem("데이터 백업/복원", e -> showBackupMenu()));
		settingsMenu.add(settingsItem("구글 드라이브 동기화", e -> SyncDialog.show(this)));
		settingsMenu.add(settingsItem("라이선스 등록", e -> showLicenseDialog()));
		settingsMenu.addSeparator();
		settingsMenu.add(settingsItem("로그아웃", e -> logout()));

		JButton settingsBtn = new JButton("설정 ▲");
		settingsBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
		settingsBtn.setForeground(GRAY_TXT);
		settingsBtn.setBackground(WHITE);
		settingsBtn.setFocusPainted(false);
		settingsBtn.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219)));
		settingsBtn.setOpaque(true);
		settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		settingsBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		settingsBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		settingsBtn.addActionListener(e -> {
			// 메뉴 폭을 버튼 폭에 맞추고, 버튼 바로 위에 붙여서 연다.
			// 이전에 지정한 크기가 남아 있으면 getPreferredSize()가 그 값을 돌려주므로 먼저 지우고 다시 계산
			settingsMenu.setPreferredSize(null);
			Dimension natural = settingsMenu.getPreferredSize();
			settingsMenu.setPopupSize(Math.max(settingsBtn.getWidth(), natural.width), natural.height);
			settingsMenu.show(settingsBtn, 0, -natural.height);
		});

		panel.add(refreshBtn);
		panel.add(Box.createRigidArea(new Dimension(0, 6)));
		panel.add(settingsBtn);
		return panel;
	}

	// Android 앱은 같은 Wi-Fi에서 이 PC를 자동으로 찾음. 자동 검색이 막히는 네트워크를 위해
	// 앱의 [서버 주소 직접 입력]에 넣을 이 PC의 LAN 주소를 함께 보여줌
	private void showAndroidConnectInfo() {
		String address = ServerLauncher.getLanAddressHint();
		JOptionPane.showMessageDialog(this,
				"같은 Wi-Fi에 연결된 Android 앱은 이 PC를 자동으로 찾습니다.\n"
						+ "(이 프로그램이 켜져 있고, 같은 Wi-Fi여야 합니다)\n\n"
						+ "자동으로 찾지 못하면 앱의 [서버 주소 직접 입력]에\n"
						+ "아래 주소를 입력하세요:\n\n" + address,
				"Android 연결 정보", JOptionPane.INFORMATION_MESSAGE);
	}

	// 자동 백업(앱 시작/종료 시 하루 단위, 최근 14일)이 쌓이는 폴더를 열거나, 그중 하나로 복원을 예약.
	// 복원은 DB 파일이 열려 있는 동안 바꿀 수 없어서 앱을 다시 시작할 때 적용된다
	private void showBackupMenu() {
		BackupManager backups = ServerLauncher.getBackupManager();
		if (backups == null) {
			JOptionPane.showMessageDialog(this, "백업 기능을 사용할 수 없습니다.", "데이터 백업/복원", JOptionPane.WARNING_MESSAGE);
			return;
		}
		String[] options = {"백업 폴더 열기", "백업에서 복원", "닫기"};
		int choice = JOptionPane.showOptionDialog(this,
				"출석 데이터는 앱을 켜고 끌 때마다 자동으로 백업됩니다 (최근 14일).\n\n"
						+ "백업 위치: " + backups.getBackupDir().getAbsolutePath(),
				"데이터 백업/복원", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
				null, options, options[0]);
		if (choice == 0) {
			try {
				Desktop.getDesktop().open(backups.getBackupDir());
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this, "폴더를 열지 못했습니다: " + ex.getMessage(),
						"데이터 백업/복원", JOptionPane.ERROR_MESSAGE);
			}
		} else if (choice == 1) {
			restoreFromBackup(backups);
		}
	}

	private void restoreFromBackup(BackupManager backups) {
		JFileChooser chooser = new JFileChooser(backups.getBackupDir());
		chooser.setDialogTitle("복원할 백업 파일 선택");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("백업 파일 (*.mv.db)", "db"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		int confirm = JOptionPane.showConfirmDialog(this,
				"선택한 백업으로 현재 데이터를 되돌립니다.\n"
						+ "(복원 직전의 현재 데이터도 백업 폴더에 따로 보관됩니다)\n\n"
						+ "복원은 프로그램을 다시 시작할 때 적용됩니다. 지금 프로그램을 종료할까요?",
				"데이터 복원", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.OK_OPTION) return;

		try {
			backups.scheduleRestore(chooser.getSelectedFile());
		} catch (java.io.IOException ex) {
			JOptionPane.showMessageDialog(this, "복원 예약 실패: " + ex.getMessage(),
					"데이터 복원", JOptionPane.ERROR_MESSAGE);
			return;
		}
		System.exit(0);
	}

	// 현재 플랜 상태를 보여주고, 발급받은 라이선스 키(ATT-...)를 입력받아 활성화/연장
	private void showLicenseDialog() {
		JTextArea keyField = new JTextArea(4, 36);
		keyField.setLineWrap(true);
		keyField.setWrapStyleWord(false);

		JPanel box = new JPanel(new BorderLayout(0, 8));
		box.add(new JLabel("<html>" + describePlan() + "</html>"), BorderLayout.NORTH);
		box.add(new JLabel("발급받은 라이선스 키를 붙여넣으세요:"), BorderLayout.CENTER);
		JPanel input = new JPanel(new BorderLayout());
		input.add(box, BorderLayout.NORTH);
		input.add(new JScrollPane(keyField), BorderLayout.CENTER);

		int result = JOptionPane.showConfirmDialog(this, input, "라이선스 등록",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION) return;

		String key = keyField.getText().trim();
		if (key.isEmpty()) return;
		String error = DataManager.activateLicense(key);
		if (error != null) {
			JOptionPane.showMessageDialog(this, error, "라이선스 등록 실패", JOptionPane.ERROR_MESSAGE);
			return;
		}
		JOptionPane.showMessageDialog(this, "<html>" + describePlan() + "</html>",
				"라이선스 등록 완료", JOptionPane.INFORMATION_MESSAGE);
		refreshCurrentPanel(); // 바뀐 제한값을 현재 화면에 즉시 반영
	}

	private String describePlan() {
		java.time.LocalDate expires = DataManager.getLicenseExpiresAt();
		String holder = DataManager.getLicenseHolder();
		if (DataManager.isDemo()) {
			String base = "현재 상태: <b>체험(DEMO)</b> - 반 " + DataManager.getMaxClasses() + "개, 학생 "
					+ DataManager.getMaxStudents() + "명, 이번 달 출석만 가능";
			if (expires != null) base += "<br><font color='red'>라이선스가 " + expires + "에 만료되었습니다.</font>";
			return base;
		}
		if (expires == null) return "현재 상태: <b>정식(ACTIVE)</b> - 기한 없음";
		return "현재 상태: <b>정식(ACTIVE)</b>" + (holder == null || holder.isEmpty() ? "" : " - " + holder)
				+ "<br>라이선스 만료일: " + expires;
	}

	//로그아웃: 확인 후 변경사항을 저장하고, 이 창을 닫은 뒤 로그인 화면부터 다시 시작
	private void logout() {
		int choice = JOptionPane.showConfirmDialog(
				this, "로그아웃하시겠습니까?", "로그아웃", JOptionPane.YES_NO_OPTION);
		if (choice != JOptionPane.YES_OPTION) return;

		DataManager.saveAll(); // 나가기 전에 변경사항 저장
		DataManager.logout();
		dispose();
		SwingUtilities.invokeLater(Main::startSession);
	}
	
	//현재 보고 있는 화면을 서버 최신 데이터로 갱신
	private void refreshCurrentPanel() {
		reloadDataAsync(() -> refreshPanel(currentIdx));
	}

	// idx에 해당하는 화면만 다시 그림 (서버 재조회는 하지 않음)
	private void refreshPanel(int idx) {
		switch (idx) {
			case 0 -> dashboardPanel.refresh();
			case 1 -> classPanel.refresh();
			case 2 -> studentPanel.refresh();
			case 3 -> attendancePanel.refresh();
			case 4 -> calendarPanel.refresh();
			case 5 -> excelPanel.refresh();
		}
	}

	// true인 동안 서버 통신이 진행 중 -> 메뉴를 연타해도 loadAll()이 겹쳐 실행되며
	// DataManager의 공유 리스트를 동시에 건드리는 일이 없도록 막는 용도
	private boolean loading = false;

	// DataManager.loadAll()(서버로 GET 4번)은 화면 전환/새로고침마다 호출되는데,
	// 예전에는 이걸 EDT(화면을 그리는 스레드)에서 그대로 실행해서 응답이 올 때까지
	// 창 전체가 멈춘 것처럼 굳어 있었음(특히 Render 무료 요금제는 첫 요청이 느림).
	// SwingWorker로 백그라운드 스레드에서 통신하고, 끝난 뒤 결과만 EDT로 돌아와 반영함.
	private void reloadDataAsync(Runnable afterLoad) {
		if (loading) return;
		loading = true;
		setNavigationEnabled(false);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				DataManager.loadAll();
				return null;
			}
			@Override
			protected void done() {
				loading = false;
				setNavigationEnabled(true);
				afterLoad.run();
			}
		}.execute();
	}

	// 데이터를 불러오는 동안 메뉴를 눌러 화면을 또 전환하지 못하게 막음
	private void setNavigationEnabled(boolean enabled) {
		for (JButton btn : menuButtons) {
			btn.setEnabled(enabled);
		}
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

		// 화면 전환 시 서버에서 최신 데이터를 다시 불러옴 (다른 기기에서 변경한 내용 반영).
		// 통신은 백그라운드에서 하고, 끝나면 해당 화면만 갱신함 (아래 reloadDataAsync 참고)
		reloadDataAsync(() -> refreshPanel(idx));
	}
}