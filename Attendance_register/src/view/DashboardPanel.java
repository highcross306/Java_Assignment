package view;

import javax.swing.*;
import java.awt.*;
import model.*;
import javax.swing.table.*;
import java.util.*;

public class DashboardPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 색상 상수
    private static final Color BG       = new Color(248, 249, 251);
    private static final Color WHITE    = Color.WHITE;
    private static final Color BLUE     = new Color(37,  99,  235);
    private static final Color GRAY_TXT = new Color(107, 114, 128);
    private static final Color BORDER   = new Color(229, 231, 235);
    
    //요약카드 필드
    private JLabel totalLabel;
    private JLabel presentLabel;
    private JLabel lateLabel;
    private JLabel absentLabel;
    
    //출결 현황 테이블 필드
    private DefaultTableModel tableModel;
    private JTable table;
    
    //정렬 필드
    private boolean sortByClass = true; // true = 반순, false = 이름순
    
    //대시보드패널 생성자
    public DashboardPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));

        JPanel topPanel = new JPanel(new BorderLayout()); //상단 패널 생성
        topPanel.setBackground(BG);
        topPanel.add(buildTitleBar(),  BorderLayout.NORTH); //패널에 타이틀 바와 요약카드 패널 추가
        topPanel.add(buildCardPanel(), BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH); //상단 패널 추가
        add(buildTable(), BorderLayout.CENTER); //출결 현황 테이블 추가
    }
    
    //타이틀바 생성
    private JPanel buildTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("대시보드");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setForeground(new Color(17, 24, 39));

        // 정렬 버튼
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setBackground(BG);
        
        // 정렬 버튼 2개
        JButton sortClassBtn = new JButton("반순");
        JButton sortNameBtn  = new JButton("이름순");

        for (JButton btn : new JButton[]{sortClassBtn, sortNameBtn}) { //정렬 버튼 세부 설정
            btn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            btn.setFocusPainted(false);
            btn.setOpaque(true);
            btn.setBorderPainted(true);
        }

        sortClassBtn.addActionListener(e -> { //반에 따라 정렬
            sortByClass = true;
            refreshTable();
        });
        sortNameBtn.addActionListener(e -> { //이름에 따라 정렬
            sortByClass = false;
            refreshTable();
        });

        btnPanel.add(sortClassBtn);
        btnPanel.add(sortNameBtn);

        titleBar.add(title,    BorderLayout.WEST);
        titleBar.add(btnPanel, BorderLayout.EAST);
        return titleBar;
    }
    
    //요약 카드 패널 생성
    private JPanel buildCardPanel() {
        JPanel cardPanel = new JPanel(new GridLayout(1, 4, 14, 0));
        cardPanel.setBackground(BG);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        totalLabel   = new JLabel("0"); //전체
        presentLabel = new JLabel("0"); //출석
        lateLabel    = new JLabel("0"); //지각
        absentLabel  = new JLabel("0"); //결석

        cardPanel.add(buildCard("전체 학생", totalLabel,   new Color(239, 246, 255), new Color(21,  128, 61)));
        cardPanel.add(buildCard("출석", presentLabel, new Color(240, 253, 244), new Color(21,  128, 61)));
        cardPanel.add(buildCard("지각", lateLabel,    new Color(254, 252, 232), new Color(161, 98,  7)));
        cardPanel.add(buildCard("결석", absentLabel,  new Color(254, 226, 226), new Color(185, 28,  28)));

        refreshCards();
        return cardPanel;
    }

    //요약 카드 생성
    private JPanel buildCard(String labelText, JLabel valueLabel, Color bgColor, Color valueColor) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        // 아이콘 박스
        JPanel iconBox = new JPanel(); 
        iconBox.setBackground(bgColor);
        iconBox.setPreferredSize(new Dimension(36, 36));
        iconBox.setMaximumSize(new Dimension(36, 36));
        iconBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 레이블
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        lbl.setForeground(GRAY_TXT);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 값
        valueLabel.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        valueLabel.setForeground(valueColor);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(iconBox);
        card.add(Box.createRigidArea(new Dimension(0, 10)));
        card.add(lbl);
        card.add(Box.createRigidArea(new Dimension(0, 4)));
        card.add(valueLabel);
        return card;
    }
    
    //요약카드 초기화
    private void refreshCards() {
        totalLabel.setText(String.valueOf(DataManager.getStudent().size()));
        presentLabel.setText(String.valueOf(DataManager.countTodayByStatus(AttendanceStatus.PRESENT)));
        lateLabel.setText(String.valueOf(DataManager.countTodayByStatus(AttendanceStatus.LATE)));
        absentLabel.setText(String.valueOf(DataManager.countTodayByStatus(AttendanceStatus.ABSENT)));
    }
    
    private JPanel buildTable() {
        tableModel = new DefaultTableModel(
                new String[] {"학번", "이름", "반", "출결 상태", "사유"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(40);
        table.setGridColor(new Color(243, 244, 246));
        table.setShowVerticalLines(false);
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
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // 셀 좌측 여백 렌더러
        DefaultTableCellRenderer paddingRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent( //셀 렌더러 오버라이드
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0)); //여백만들기
                return this;
            }
        };
        for (int i = 0; i < 5; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(paddingRenderer);
        }

        // 출결 상태 열 색상 렌더러
        table.getColumnModel().getColumn(3).setCellRenderer(
            (t, val, sel, foc, row, col) -> { //람다식을 불러오기
                JLabel lbl = new JLabel(val == null ? "" : val.toString()); //비어있으면 ""
                lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
                lbl.setOpaque(true);
                lbl.setBackground(sel ? new Color(239, 246, 255) : WHITE);
                // 상태별 색상
                switch (val == null ? "" : val.toString()) {
                    case "출석" -> lbl.setForeground(new Color(21,  128, 61));
                    case "지각" -> lbl.setForeground(new Color(161, 98,  7));
                    case "결석" -> lbl.setForeground(new Color(185, 28,  28));
                    case "조퇴" -> lbl.setForeground(new Color(37,  99,  235));
                    case "공결" -> lbl.setForeground(new Color(107, 114, 128));
                    default    -> lbl.setForeground(new Color(17,  24,  39));
                }
                return lbl;
            }
        );

        // 카드로 감싸기
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(233, 236, 239), 1));
        card.add(new JScrollPane(table), BorderLayout.CENTER);

        refreshTable();
        return card;
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        
        // 정렬
        ArrayList<Student> sorted = new ArrayList<>(DataManager.getStudent());
        if (sortByClass) {
            Collections.sort(sorted, (a, b) -> a.getClass_Id() - b.getClass_Id()); //반 순서에 따라 정렬
        } else {
            Collections.sort(sorted, (a, b) -> a.getName().compareTo(b.getName())); //이름 순으로 정렬
        }

        for (Student s : sorted) {
            Class_Info cls = DataManager.findClassById(s.getClass_Id());
            String className = (cls != null) ? cls.getClass_Name() : "-";

         // 오늘 출결 기록 조회
            String statusStr = "미입력"; //출결 상태 기본 출력
            String reason = ""; //출결 사유 기본 출력
            for (AttendanceRecord r : DataManager.getTodayRecords()) {
                if (r.getStudent_Id() == s.getStudent_id()) { //오늘 출결 상태 출력
                    statusStr = switch (r.getStatus()) {
                        case LATE        -> "지각";
                        case ABSENT      -> "결석";
                        case EARLY_LEAVE -> "조퇴";
                        case EXCUSED     -> "공결";
                        default          -> "출석";
                    };
                    reason = r.getReason() == null ? "" : r.getReason();
                    break;
                }
            }
            tableModel.addRow(new Object[] {
                s.getStudent_id(),
                s.getName(),
                className,
                statusStr,
                reason
            });
        }
    }
    
    //메인에서 쓸 refresh 메소드
    public void refresh() {
        refreshCards();
        refreshTable();
    }
}