package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.*;
import model.*;
import java.util.*;
import java.util.List;
import java.time.LocalDate;

public class AttendanceInputPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String[] STATUSES = {"출석", "지각", "결석", "조퇴", "공결"};
    
    // 색상 상수
    private static final Color BG       = new Color(248, 249, 251);
    private static final Color WHITE    = Color.WHITE;
    private static final Color BLUE     = new Color(37,  99,  235);
    private static final Color BTN_HOVER = new Color(29,  78,  216);
    private static final Color BTN_PRESS = new Color(30,  64,  175);
    private static final Color GRAY_TXT = new Color(107, 114, 128);
    private static final Color BORDER   = new Color(229, 231, 235);
    
    //데이터 입력 필드
    private JComboBox<String> classCombo;
    private JSpinner dateSpinner;
    
    //출석 테이블 필드
    private DefaultTableModel tableModel;
    private JTable table;
    
    //해시 맵을 이용해서 출결상태/사유 임시 저장
    private HashMap<Integer, String> statusMap  = new HashMap<>();
    private HashMap<Integer, String> reasonMap  = new HashMap<>();
    
    public AttendanceInputPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG);
        topPanel.add(buildTitleBar(), BorderLayout.NORTH);
        topPanel.add(buildSelectBar(), BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH); //상단-타이틀 바 추가
        add(buildTable(), BorderLayout.CENTER); //중단-입력 테이블 추가
        add(buildBtnBar(), BorderLayout.SOUTH); //하단-저장 및 초기과 바 추가
    }

    // 상단 타이틀바
    private JPanel buildTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("출석 입력");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setForeground(new Color(17, 24, 39));

        titleBar.add(title, BorderLayout.WEST); //왼쪽 정렬	
        return titleBar;
    }
    
    //반, 날짜 선택 바
    private JPanel buildSelectBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        // 반 선택 레이블
        JLabel classLabel = new JLabel("반 선택");
        classLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        classLabel.setForeground(GRAY_TXT);

        // 반 콤보박스
        classCombo = new JComboBox<>();
        classCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        classCombo.setPreferredSize(new Dimension(120, 32)); //권장크기 지정(120 X 32)
        for (Class_Info cls : DataManager.getClasses()) { //반 선택
            classCombo.addItem(cls.getClass_Name());
        }
        // 반 변경 시 테이블 갱신
        classCombo.addActionListener(e -> refreshTable());

        // 날짜 선택 레이블
        JLabel dateLabel = new JLabel("날짜 선택");
        dateLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        dateLabel.setForeground(GRAY_TXT);

        // 날짜 스피너 (오늘 날짜 기본값)
        SpinnerDateModel dateModel = new SpinnerDateModel();
        dateSpinner = new JSpinner(dateModel);
        dateSpinner.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        dateSpinner.setPreferredSize(new Dimension(130, 32));
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"); //표시 형식을 날짜로 지정하고 스피너와 연결하기 위해 dateEditor를 사용지
        dateSpinner.setEditor(dateEditor);
        
        // 날짜 변경 시 테이블 갱신
        dateSpinner.addChangeListener(e -> refreshTable());

        applyDemoDateBounds(); // 데모 상태면 스피너로 선택 가능한 날짜를 이번 달로 제한

        bar.add(classLabel);
        bar.add(classCombo); //반 선택 콤보 상자
        bar.add(Box.createHorizontalStrut(16));
        bar.add(dateLabel);
        bar.add(dateSpinner); //날짜 선택 스피터

        return bar; //바 반환
    }
    
    //출석 상태 입력 테이블
    private JPanel buildTable() {
        tableModel = new DefaultTableModel(
                new String[] {"학번", "이름", "출결 상태", "사유"}, 0) {
        	@Override
        	public boolean isCellEditable(int row, int col) {
        	    return col == 3; // 사유 열만 편집 허용
        	}
        };

        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(45);
        table.setGridColor(new Color(243, 244, 246));
        table.setShowVerticalLines(false); //세로선 지우기
        table.setSelectionBackground(new Color(239, 246, 255));

        // 헤더 스타일
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        table.getTableHeader().setBackground(new Color(250, 250, 250));
        table.getTableHeader().setForeground(GRAY_TXT);
        table.getTableHeader().setReorderingAllowed(false);

        // 열 너비
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // 셀 좌측 여백 렌더러
        DefaultTableCellRenderer paddingRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
                return this;
            }
        };
        for (int i = 0; i < 4; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(paddingRenderer);
        }
        
     // 출결 상태 열 라디오 버튼 렌더러
        table.getColumnModel().getColumn(2).setCellRenderer(
            (t, val, sel, foc, row, col) -> {
                JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 8));
                p.setBackground(sel ? new Color(239, 246, 255) : WHITE);
                ButtonGroup group = new ButtonGroup(); //radio를 묶기 위해서 ButtonGroup을 만든다.
                for (String status : STATUSES) {
                    JRadioButton rb = new JRadioButton(status); //radio를 반든다.
                    rb.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
                    rb.setBackground(sel ? new Color(239, 246, 255) : WHITE);
                    rb.setSelected(status.equals(val)); //출결 상채를 확인하고 반영한다.
                    group.add(rb); //ButtonGruop에 radio를 추가한다.
                    p.add(rb);
                }
                return p;
            }
        );
        
        // 사유 열 렌더러
        table.getColumnModel().getColumn(3).setCellRenderer(
            (t, val, sel, foc, row, col) -> {
                JTextField field = new JTextField(val == null ? "" : val.toString()); //사유가 없음면 빈칸, 있다면 사유를 불러온다.
                field.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
                field.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
                field.setBackground(sel ? new Color(239, 246, 255) : WHITE);
                return field;
            }
        );
        
        // 사유 열 에디터 (클릭 시 바로 입력 가능)
        JTextField editor = new JTextField(); //textfield를 통해 사유 열 작성
        editor.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        editor.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        table.getColumnModel().getColumn(3).setCellEditor(
            new DefaultCellEditor(editor)
        );
        
        //테이블 선택 감지
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (col == 2) {
                    Rectangle cellRect = table.getCellRect(row, col, true);
                    int cellX = e.getX() - cellRect.x;
                    // 셀 너비를 5등분해서 어떤 라디오 버튼인지 계산
                    int btnWidth = cellRect.width / STATUSES.length;
                    int idx = cellX / btnWidth;
                    //선택이 라디오 중 하나일 경우, 상태를 해당하는 값으로 교채
                    if (idx >= 0 && idx < STATUSES.length) {
                        int studentId = (int) tableModel.getValueAt(row, 0);
                        tableModel.setValueAt(STATUSES[idx], row, 2);
                        statusMap.put(studentId, STATUSES[idx]); // 맵에 저장
                        table.repaint();
                    }
                }
            }
        });

        // 카드로 감싸기
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(233, 236, 239), 1));
        card.add(new JScrollPane(table), BorderLayout.CENTER);

        refreshTable();
        return card;
    }
    
    //테이블 초기화
    private void refreshTable() {
        tableModel.setRowCount(0);
        if (classCombo.getItemCount() == 0) return;

        int selIdx = classCombo.getSelectedIndex();
        Class_Info selectedClass = DataManager.getClasses().get(selIdx);

        // 선택된 날짜 가져오기
        Date selected = (Date) dateSpinner.getValue();
        LocalDate date = selected.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate(); //절대 시각으로 선택된 날짜의 시스템 상의 날짜를 불러온다.

        // 해당 날짜의 기존 출석 기록을 맵에 로드
        statusMap.clear();
        reasonMap.clear();
        for (AttendanceRecord r : DataManager.getRecords()) {
            if (r.getDate().equals(date)) { //현재 선택된 날짜와 일치하는 기록만 불러오기
                String statusStr = switch (r.getStatus()) {
                    case LATE        -> "지각";
                    case ABSENT      -> "결석";
                    case EARLY_LEAVE -> "조퇴";
                    case EXCUSED     -> "공결";
                    default          -> "출석";
                };
                statusMap.put(r.getStudent_Id(), statusStr); //상태 맵에 저장
                reasonMap.put(r.getStudent_Id(), r.getReason() == null ? "" : r.getReason()); //사유 맵에 저장
            }
        }

        // 테이블 채우기
        for (Student s : DataManager.getStudent()) {
            if (s.getClass_Id() != selectedClass.getClass_Id()) continue;
            String status = statusMap.getOrDefault(s.getStudent_id(), "출석"); //기본상태는 "출석"
            String reason = reasonMap.getOrDefault(s.getStudent_id(), ""); //기본상태는 빈칸
            tableModel.addRow(new Object[] {
                s.getStudent_id(),
                s.getName(),
                status,
                reason
            });
        }
    }
    
    //하단 바 
    private JPanel buildBtnBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        bar.setBackground(BG);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        // 초기화 버튼
        JButton resetBtn = new JButton("초기화");
        resetBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                this,
                "입력 내용을 초기화하시겠습니까?\n저장된 출석 기록도 삭제됩니다.",
                "초기화",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (result == JOptionPane.YES_OPTION) {
                // 선택된 날짜 가져오기
                Date selected = (Date) dateSpinner.getValue();
                LocalDate date = selected.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();

                // 선택된 반 가져오기
                int selIdx = classCombo.getSelectedIndex();
                Class_Info selectedClass = DataManager.getClasses().get(selIdx);

                // 해당 날짜 + 해당 반의 출석 기록 삭제 (로컬 + 서버 동시 반영)
                List<String> errors = DataManager.deleteRecordsByClassAndDate(
                    selectedClass.getClass_Id(), date
                );

                // 임시 저장 맵 초기화
                statusMap.clear();
                reasonMap.clear();

                refreshTable();

                if (!errors.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        String.join("\n", errors), "삭제 실패", JOptionPane.ERROR_MESSAGE);
                }
            }
        });;

        // 저장 버튼
        JButton saveBtn = new JButton("저장");
        saveBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        saveBtn.setBackground(BLUE);
        saveBtn.setForeground(WHITE);
        saveBtn.setContentAreaFilled(false); //L&F 자동 배경 그리기 끄기
        saveBtn.setOpaque(true);
        saveBtn.setBorderPainted(false);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        saveBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e)  { saveBtn.setBackground(BTN_HOVER); }
            @Override public void mouseExited(MouseEvent e)   { saveBtn.setBackground(BLUE);      }
            @Override public void mousePressed(MouseEvent e)  { saveBtn.setBackground(BTN_PRESS); }
            @Override public void mouseReleased(MouseEvent e) { saveBtn.setBackground(BTN_HOVER); }
        });
        saveBtn.addActionListener(e -> saveAttendance());

        bar.add(resetBtn);
        bar.add(saveBtn);
        return bar;
    }

    private void saveAttendance() {
        // 날짜 변환
        Date selected = (Date) dateSpinner.getValue();
        LocalDate date = selected.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(); //오늘 날짜를 LocalDate 타입으로 변환

        int selIdx = classCombo.getSelectedIndex();
        Class_Info selectedClass = DataManager.getClasses().get(selIdx);

        // 테이블 편집 중인 셀 확인
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        
        //각 열별로 출석 상태 저장
        List<String> errors = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            int studentId = (int) tableModel.getValueAt(row, 0);
            String studentName = (String) tableModel.getValueAt(row, 1);
            String statusStr = (String) tableModel.getValueAt(row, 2);
            String reason   = tableModel.getValueAt(row, 3) == null ? "" : tableModel.getValueAt(row, 3).toString(); //사유는 비어 있으면 ""을 저장

            // 출결 상태 문자열 → enum 변환
            AttendanceStatus status = switch (statusStr) {
                case "지각" -> AttendanceStatus.LATE;
                case "결석" -> AttendanceStatus.ABSENT;
                case "조퇴" -> AttendanceStatus.EARLY_LEAVE;
                case "공결" -> AttendanceStatus.EXCUSED;
                default    -> AttendanceStatus.PRESENT;
            };

            //DataManger를 통해 내용 저장하기
            String error = DataManager.updateRecord(studentId, date, status, reason);
            if (error != null) {
                errors.add(studentName + ": " + error);
            }
        }

        refreshTable(); // 실패한 항목은 저장 시도 전 상태로 되돌려져 있으므로 다시 반영

        if (errors.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                selectedClass.getClass_Name() + " 출석이 저장되었습니다.",
                "저장 완료", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                "일부 출석 기록 저장에 실패했습니다:\n" + String.join("\n", errors),
                "저장 실패", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    //메인에서 쓸 refresh 메소드
    public void refresh() {
        classCombo.removeAllItems(); //반 다시 불러오기
        for (Class_Info cls : DataManager.getClasses()) {
            classCombo.addItem(cls.getClass_Name());
        }
        applyDemoDateBounds(); // 구독 상태가 바뀌었을 수 있으므로 다시 확인
        refreshTable(); //테이블 초기화
    }

    // 데모 상태면 날짜 스피너로 선택 가능한 범위를 이번 달로 제한 (ACTIVE면 제한 없음)
    private void applyDemoDateBounds() {
        SpinnerDateModel model = (SpinnerDateModel) dateSpinner.getModel();
        java.time.YearMonth allowed = DataManager.getAllowedMonth();
        if (allowed == null) {
            model.setStart(null);
            model.setEnd(null);
            return;
        }

        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        Date startDate = Date.from(allowed.atDay(1).atStartOfDay(zone).toInstant());
        Date endDate = Date.from(allowed.atEndOfMonth().atStartOfDay(zone).toInstant());
        model.setStart(startDate);
        model.setEnd(endDate);

        // 범위를 벗어난 값이 선택돼 있으면 오늘 날짜로 되돌림
        Date current = (Date) model.getValue();
        if (current.before(startDate) || current.after(endDate)) {
            model.setValue(new Date());
        }
    }
}