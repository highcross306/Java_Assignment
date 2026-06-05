package view;

import javax.swing.*;
import java.awt.*;
import model.*;
import java.time.*;
import java.awt.event.*;

public class AttendanceCalendarPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 색상 상수
    private static final Color BG       = new Color(248, 249, 251);
    private static final Color WHITE    = Color.WHITE;
    private static final Color BLUE     = new Color(37,  99,  235);
    private static final Color GRAY_TXT = new Color(107, 114, 128);
    private static final Color BORDER   = new Color(229, 231, 235);
    
    //상단 선택 바 필드
    private JComboBox<String> classCombo;
    private JComboBox<String> studentCombo;
    private JLabel monthLabel;
    private java.time.YearMonth currentMonth = java.time.YearMonth.now();
    
    //달력 필드
    private JPanel calendarPanel;
    
    //상세 정보 필드
    private JPanel detailPanel;
    
    //선택된 날짜인지 확인하기 위한 필드
    private java.time.LocalDate selectedDate = null;
    
    //캘린더 생성
    public AttendanceCalendarPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG);
        topPanel.add(buildTitleBar(),  BorderLayout.NORTH); //타이틀바
        topPanel.add(buildSelectBar(), BorderLayout.SOUTH); //상단 선택바

        add(topPanel, BorderLayout.NORTH);
        
        //분할 pane, 달력과 상세정보 배치
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildCalendarArea(), buildDetailPanel()); // 왼쪽/오른쪽으로 나눔
        splitPane.setDividerLocation(630); //경계선 초기 위치
        splitPane.setDividerSize(1); //경계선 두께
        splitPane.setBorder(null); //기본 테두리 제거
        splitPane.setBackground(BG);
        add(splitPane, BorderLayout.CENTER);
    }
    
    //상단 타이틀 바
    private JPanel buildTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        
        //타이틀 레이블
        JLabel title = new JLabel("출석 달력");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setForeground(new Color(17, 24, 39));

        titleBar.add(title, BorderLayout.WEST);
        return titleBar;
    }
    
    //상단 선택 바
    private JPanel buildSelectBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        // 반 선택
        JLabel classLabel = new JLabel("반");
        classLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        classLabel.setForeground(GRAY_TXT);
        
        // 반 콤보 상자
        classCombo = new JComboBox<>();
        classCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        classCombo.setPreferredSize(new Dimension(100, 32));
        for (Class_Info cls : DataManager.getClasses()) { //콤보 상자에 반 추가
            classCombo.addItem(cls.getClass_Name());
        }
        classCombo.addActionListener(e -> refreshStudentCombo());

        // 학생 선택
        JLabel studentLabel = new JLabel("학생");
        studentLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        studentLabel.setForeground(GRAY_TXT);
        
        // 학생 콤보 상자
        studentCombo = new JComboBox<>();
        studentCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        studentCombo.setPreferredSize(new Dimension(100, 32));
        studentCombo.addActionListener(e -> refreshCalendar()); //학생 선택 시 달력 초기화

        // 월 이동 버튼
        JButton prevBtn = new JButton("< 이전");
        JButton nextBtn = new JButton("다음 >");
        monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        monthLabel.setPreferredSize(new Dimension(100, 32));

        for (JButton btn : new JButton[]{prevBtn, nextBtn}) {
            btn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            btn.setFocusPainted(false);
            btn.setBackground(WHITE);
            btn.setOpaque(true);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        prevBtn.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1); //이전
            refreshCalendar();
        });
        nextBtn.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1); //다음
            refreshCalendar();
        });

        bar.add(classLabel); //반
        bar.add(classCombo);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(studentLabel); //학생
        bar.add(studentCombo);
        bar.add(Box.createHorizontalStrut(24));
        bar.add(prevBtn); //이전, 다음
        bar.add(monthLabel);
        bar.add(nextBtn);

        refreshStudentCombo();
        return bar;
    }
    
    //달력 생성 패널
    private JPanel buildCalendarArea() {
        calendarPanel = new JPanel(new BorderLayout());
        calendarPanel.setBackground(WHITE);
        calendarPanel.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        refreshCalendar();
        return calendarPanel;
    }

    // 반 변경 시 학생 콤보박스 갱신
    private void refreshStudentCombo() {
        studentCombo.removeAllItems(); //학생 콤보 상자 지우기
        if (classCombo.getItemCount() == 0) return;

        int selIdx = classCombo.getSelectedIndex();
        Class_Info selectedClass = DataManager.getClasses().get(selIdx); //반 정보에서 선택된 클래스 아이디 가져오기

        for (Student s : DataManager.getStudent()) {
            if (s.getClass_Id() == selectedClass.getClass_Id()) {
                studentCombo.addItem(s.getName());
            }
        }
    }
    
    //달력 초기화
    private void refreshCalendar() {
    	if (calendarPanel == null || detailPanel == null) return; // calendarPanel 미초기화 시 종료
    	
        monthLabel.setText(currentMonth.getYear() + "년 " +
            currentMonth.getMonthValue() + "월");

        calendarPanel.removeAll(); //기존 달력 지우기

        // 요일 헤더
        String[] days = {"일", "월", "화", "수", "목", "금", "토"};
        JPanel header = new JPanel(new GridLayout(1, 7));
        header.setBackground(new Color(250, 250, 250));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        for (int i = 0; i < days.length; i++) {
            JLabel lbl = new JLabel(days[i], SwingConstants.CENTER); //요일별 레이블 생성
            lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
            lbl.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
            if (i == 0) lbl.setForeground(new Color(220, 38, 38));      // 일요일 빨강
            else if (i == 6) lbl.setForeground(new Color(37, 99, 235)); // 토요일 파랑
            else lbl.setForeground(GRAY_TXT);
            header.add(lbl); //헤더에 레이블 추가
        }

        // 날짜 그리드
        JPanel grid = new JPanel(new GridLayout(6, 7, 1, 1));
        grid.setBackground(BORDER);
        grid.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        // 이달 1일의 요일 (0=일, 1=월 ...)
        java.time.LocalDate firstDay = currentMonth.atDay(1); //달의 첫째날
        int startDow = firstDay.getDayOfWeek().getValue() % 7; //시작 요일
        int lastDay  = currentMonth.lengthOfMonth(); //마지막 날짜

        // 선택한 학생의 출결 기록 조회
        Student selectedStudent = getSelectedStudent();

        // 빈 셀 채우기
        for (int i = 0; i < startDow; i++) {
            JPanel empty = new JPanel(); //빈 셀
            empty.setBackground(new Color(245, 246, 248));
            grid.add(empty);
        }

        // 날짜 셀
        for (int day = 1; day <= lastDay; day++) {
            java.time.LocalDate date = currentMonth.atDay(day); //날짜 셀
            JPanel cell = buildDayCell(day, date, selectedStudent);
            grid.add(cell);
        }

        // 나머지 빈 셀
        int remaining = 42 - startDow - lastDay;
        for (int i = 0; i < remaining; i++) {
            JPanel empty = new JPanel(); //빈 셀
            empty.setBackground(new Color(245, 246, 248));
            grid.add(empty);
        }

        calendarPanel.add(header, BorderLayout.NORTH);
        calendarPanel.add(grid,   BorderLayout.CENTER);
        calendarPanel.revalidate(); //패널을 다시 계산해서 컴포넌트 재비치
        calendarPanel.repaint(); //재배치한 컴포넌트 다시 보이기
        
     // 통계 갱신
        detailPanel.removeAll(); //디테일 패널 초기화
        JLabel placeholder = new JLabel("날짜를 클릭하세요"); //월병 통계 기본 상태
        placeholder.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        placeholder.setForeground(GRAY_TXT);
        placeholder.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(placeholder);
        detailPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        detailPanel.add(buildDetailDivider()); //선 나누기
        detailPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        detailPanel.add(buildMonthStats()); //해당 월의 통계 출력
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    // 날짜 셀 생성
    private JPanel buildDayCell(int day, java.time.LocalDate date, Student student) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setBackground(WHITE);
     // 기본 테두리
        cell.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(229, 231, 235), 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 날짜 숫자
        JLabel dayLbl = new JLabel(String.valueOf(day));
        dayLbl.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        
        //요일별 테두리 색상
        int dow = date.getDayOfWeek().getValue() % 7; //요일 구하기
        if (dow == 0) {
            cell.setBorder(BorderFactory.createCompoundBorder( //일요일
                BorderFactory.createLineBorder(new Color(254, 202, 202), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        } else if (dow == 6) {
            cell.setBorder(BorderFactory.createCompoundBorder( //토요일
                BorderFactory.createLineBorder(new Color(191, 219, 254), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        }
        
        // 오늘 날짜는 항상 마지막에 덮어씀
        if (date.equals(java.time.LocalDate.now())) {
            cell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BLUE, 2),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        }

        // 출결 상태 표시
        JLabel statusLbl = new JLabel("", SwingConstants.CENTER);
        statusLbl.setFont(new Font("맑은 고딕", Font.BOLD, 10));

        if (student != null) { //학생이 있을 경우
            for (AttendanceRecord r : DataManager.getRecords()) { //출결 기록에 따라서 글자를 출력한다.
                if (r.getStudent_Id() == student.getStudent_id() &&
                        r.getDate().equals(date)) {
                	switch (r.getStatus()) { //출결 상태에 따라서 글자와 테두리와 칸의 색깔이 변한다.
                    case PRESENT -> {
                        statusLbl.setText("출석"); //출석 상태일때의 테두리 변화
                        statusLbl.setForeground(new Color(21, 128, 61));
                        cell.setBackground(new Color(240, 253, 244));
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(34, 197, 94), 1),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    case LATE -> {
                        statusLbl.setText("지각"); //지각 상태일때의 테두리 변화
                        statusLbl.setForeground(new Color(161, 98, 7));
                        cell.setBackground(new Color(254, 252, 232));
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(234, 179, 8), 1),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    case ABSENT -> {
                        statusLbl.setText("결석"); //결석 상태일때의 테두리 변화
                        statusLbl.setForeground(new Color(185, 28, 28));
                        cell.setBackground(new Color(254, 226, 226));
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(239, 68, 68), 1),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    case EARLY_LEAVE -> {
                        statusLbl.setText("조퇴"); //조퇴 상태일때의 테두리 변화
                        statusLbl.setForeground(new Color(37, 99, 235));
                        cell.setBackground(new Color(239, 246, 255));
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BLUE, 1),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    case EXCUSED -> {
                        statusLbl.setText("공결"); //공결 상태일때의 테두리 변화
                        statusLbl.setForeground(GRAY_TXT);
                        cell.setBackground(new Color(243, 244, 246));
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(156, 163, 175), 1),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
                    }
                    
                }
                	// 오늘 날짜는 항상 마지막에 덮어씀
                    if (date.equals(java.time.LocalDate.now())) {
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(BLUE, 2),
                            BorderFactory.createEmptyBorder(4, 6, 4, 6)
                        ));
                    }
                    break;
                }
            }
        }

        cell.add(dayLbl,    BorderLayout.NORTH);
        cell.add(statusLbl, BorderLayout.CENTER);
        
        //셀 클릭 시 상세정보를 띄우기 위한 리스너
        cell.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDetail(date); //해당 날짜의 정보 출력
            }
            @Override
            public void mouseEntered(MouseEvent e) { //색깔 어둡게 바꾸기
                cell.setBackground(cell.getBackground().darker());
                cell.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
            	cell.setBackground(cell.getBackground().brighter()); // 원래 색으로 복구
            	cell.repaint();
            }
        });
        
        return cell;
    }

    // 선택된 학생 객체 반환
    private Student getSelectedStudent() {
        if (studentCombo.getItemCount() == 0) return null; //학생이 없다면 null 반환
        String name = (String) studentCombo.getSelectedItem(); //학생 이름
        int selIdx  = classCombo.getSelectedIndex(); //선택된 인덱스
        Class_Info  selectedClass = DataManager.getClasses().get(selIdx); //선택된 반
        for (Student s : DataManager.getStudent()) {
            if (s.getClass_Id() == selectedClass.getClass_Id() &&
                    s.getName().equals(name)) return s;
        }
        return null;
    }
    
    //상세정보 패널 생성
    private JPanel buildDetailPanel() {
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(WHITE);
        detailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(20, 16, 20, 16)
        ));
        
        //상세 정보 패널 기본 상태
        JLabel placeholder = new JLabel("날짜를 클릭하세요"); //기본 상태(날짜 클릭 대기)
        placeholder.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        placeholder.setForeground(GRAY_TXT);
        placeholder.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailPanel.add(placeholder);

        detailPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        detailPanel.add(buildDetailDivider());
        detailPanel.add(Box.createRigidArea(new Dimension(0, 16)));
        detailPanel.add(buildMonthStats());


        return detailPanel;
    }
    
    //상세 정보 출력하기
    private void showDetail(java.time.LocalDate date) {
    	if (date.equals(selectedDate)) { //날짜를 다시 선택할 경우 캘린더 초기화를 통해 초기 화면으로 초기화
            selectedDate = null;
            refreshCalendar();
            return;
        }
    	
    	selectedDate = date; //선택된 날짜로 현재 날짜 지정
    	
        detailPanel.removeAll();

        // 날짜 제목
        JLabel dateLabel = new JLabel(date.getYear() + "년 " +
            date.getMonthValue() + "월 " + date.getDayOfMonth() + "일");
        dateLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        dateLabel.setForeground(new Color(17, 24, 39));
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        detailPanel.add(dateLabel);
        detailPanel.add(buildDetailDivider());

        // 출결 기록 조회
        Student student = getSelectedStudent();
        boolean found = false;
        
        //학생 정보가 있는지 확인
        if (student != null) {
            for (AttendanceRecord r : DataManager.getRecords()) {
                if (r.getStudent_Id() == student.getStudent_id() &&
                        r.getDate().equals(date)) {

                    String statusStr = switch (r.getStatus()) {
                        case LATE        -> "지각";
                        case ABSENT      -> "결석";
                        case EARLY_LEAVE -> "조퇴";
                        case EXCUSED     -> "공결";
                        default          -> "출석";
                    };
                    Color statusColor = switch (r.getStatus()) {
                        case LATE        -> new Color(161, 98,  7);
                        case ABSENT      -> new Color(185, 28,  28);
                        case EARLY_LEAVE -> new Color(37,  99,  235);
                        case EXCUSED     -> GRAY_TXT;
                        default          -> new Color(21,  128, 61);
                    };

                    detailPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                    detailPanel.add(buildDetailRow("출결 상태", statusStr, statusColor)); //출결 상태 출력
                    detailPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                    detailPanel.add(buildDetailRow("사유",
                        r.getReason() == null || r.getReason().isEmpty() ? "-" : r.getReason(), //사유 출력(미입력 시 "-" 출력)
                        new Color(17, 24, 39)));
                    found = true;
                    break;
                }
            }
        }
        
        //해당 날짜에 출결 기록이 없을 시 출력
        if (!found) {
            detailPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            JLabel noRecord = new JLabel("출결 기록 없음");
            noRecord.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            noRecord.setForeground(GRAY_TXT);
            noRecord.setAlignmentX(Component.LEFT_ALIGNMENT);
            detailPanel.add(noRecord);
        }
        
        detailPanel.revalidate(); //레이아웃을 다시 계산해서 컴퍼넌트를 재배치
        detailPanel.repaint(); //화면 다시 그리기(revalidate() 반영)
    }
    
    //상세 정보행 생성
    private JPanel buildDetailRow(String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(WHITE);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        lbl.setForeground(GRAY_TXT);
        lbl.setPreferredSize(new Dimension(70, 32));

        JLabel val = new JLabel(value);
        val.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        val.setForeground(valueColor);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        return row;
    }
    
    //상세정보 나누기
    private JSeparator buildDetailDivider() {
        JSeparator sep = new JSeparator(); //수평 구분선 그리기 컴퍼넌트
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1)); //필요 이상으로 커지는 것을 방지
        sep.setForeground(BORDER);
        return sep;
    }
    
    //월별 통계 출력
    private JPanel buildMonthStats() {
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(WHITE);
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 월별 통계 제목
        JLabel title = new JLabel(currentMonth.getMonthValue() + "월 통계"); //현재 달의 통계
        title.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        title.setForeground(new Color(17, 24, 39));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // 통계 계산 (합산)
        Student student = getSelectedStudent();
        int present = 0, late = 0, absent = 0, earlyLeave = 0, excused = 0;

        if (student != null) {
            for (AttendanceRecord r : DataManager.getRecords()) {
                if (r.getStudent_Id() == student.getStudent_id() &&
                        r.getDate().getYear()  == currentMonth.getYear() &&
                        r.getDate().getMonthValue() == currentMonth.getMonthValue()) {
                    switch (r.getStatus()) {
                        case PRESENT     -> present++;
                        case LATE        -> late++;
                        case ABSENT      -> absent++;
                        case EARLY_LEAVE -> earlyLeave++;
                        case EXCUSED     -> excused++;
                    }
                }
            }
        }
        
        //제목과 각 통계 자료를 출력
        statsPanel.add(title);
        //buildStatRow를 사용하여 출결 상태, 날짜순으로 추가
        statsPanel.add(buildStatRow("출석", present,    new Color(21,  128, 61)));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        statsPanel.add(buildStatRow("지각", late,       new Color(161, 98,  7)));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        statsPanel.add(buildStatRow("결석", absent,     new Color(185, 28,  28)));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        statsPanel.add(buildStatRow("조퇴", earlyLeave, new Color(37,  99,  235)));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 6)));
        statsPanel.add(buildStatRow("공결", excused,    GRAY_TXT));

        return statsPanel;
    }
    
    //통계정보 행
    private JPanel buildStatRow(String label, int count, Color color) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(WHITE);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        //출결 상태
        JLabel lbl = new JLabel(label); 
        lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        lbl.setForeground(GRAY_TXT);
        lbl.setPreferredSize(new Dimension(50, 28));
        
        //날짜
        JLabel val = new JLabel(count + "일");  
        val.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        val.setForeground(color);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        return row;
    }
    
    public void refresh() {
        classCombo.removeAllItems();
        for (Class_Info cls : DataManager.getClasses()) {
            classCombo.addItem(cls.getClass_Name());
        }
        refreshStudentCombo(); //학생 콤보 상자 초기화
        refreshCalendar(); //달력 초기화
    }
}