package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import model.*;
import javax.swing.table.*;

public class ExcelExportPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // 색상 상수
    private static final Color BG        = new Color(248, 249, 251);
    private static final Color WHITE     = Color.WHITE;
    private static final Color BLUE      = new Color(37,  99,  235);
    private static final Color BTN_HOVER = new Color(29,  78,  216);
    private static final Color BTN_PRESS = new Color(30,  64,  175);
    private static final Color GRAY_TXT  = new Color(107, 114, 128);
    private static final Color BORDER    = new Color(229, 231, 235);
    
    //옵션 선택 필드
    private JSpinner startSpinner;
    private JSpinner endSpinner;
    private JCheckBox allClassCheck;
    private JPanel classCheckPanel;
    
    //미리보기 테이블
    private DefaultTableModel tableModel;
    private JTable table;

    //엑셀 추출 버튼 (작업 중 비활성화 처리를 위해 필드로 보관)
    private JButton exportBtn;
    
    //엑셀 페이지 생성자
    public ExcelExportPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(28, 24, 28, 28));
        
        //상단 패널
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG);
        topPanel.add(buildTitleBar(),   BorderLayout.NORTH);
        topPanel.add(buildOptionPanel(), BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(buildPreviewPanel(), BorderLayout.CENTER);
    }
    
    //타이틀바 생성
    private JPanel buildTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(BG);
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        JLabel title = new JLabel("엑셀 내보내기");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setForeground(new Color(17, 24, 39));

        titleBar.add(title, BorderLayout.WEST);
        return titleBar;
    }
    
    //옵션 선택 패널
    private JPanel buildOptionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        // 기간 선택
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        dateRow.setBackground(WHITE);

        JLabel dateLabel = new JLabel("기간");
        dateLabel.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        dateLabel.setForeground(new Color(17, 24, 39));
        dateLabel.setPreferredSize(new Dimension(60, 32));

        startSpinner = makeDateSpinner(); //시작 날짜 스피너
        JLabel toLabel = new JLabel("~");
        toLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        toLabel.setForeground(GRAY_TXT);
        endSpinner = makeDateSpinner(); //끝 날짜 스피너
        
        //레이블과 스피너 추가
        dateRow.add(dateLabel);
        dateRow.add(startSpinner);
        dateRow.add(toLabel);
        dateRow.add(endSpinner);

        // 반 선택
        JPanel classRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        classRow.setBackground(WHITE);
        classRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel classLabel = new JLabel("반 선택");
        classLabel.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        classLabel.setForeground(new Color(17, 24, 39));
        classLabel.setPreferredSize(new Dimension(60, 32));
        
        //모든 반 선택
        allClassCheck = new JCheckBox("전체");
        allClassCheck.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        allClassCheck.setBackground(WHITE);
        allClassCheck.setSelected(true);
        
        //클래스 선택확인 패널
        classCheckPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        classCheckPanel.setBackground(WHITE);
        for (Class_Info cls : DataManager.getClasses()) { //반 데이터 불러오기
        	JCheckBox cb = new JCheckBox(cls.getClass_Name());
            cb.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            cb.setBackground(WHITE);
            cb.setSelected(true);
            // 개별 체크박스 변경 시 전체 체크박스 상태 갱신
            cb.addActionListener(e -> {
                boolean allSelected = true;
                for (Component c : classCheckPanel.getComponents()) {
                    if (!((JCheckBox) c).isSelected()) {
                        allSelected = false;
                        break;
                    }
                }
                allClassCheck.setSelected(allSelected);
            });
            classCheckPanel.add(cb); //반 선택 패널에 추가
        }

        // 전체 선택 시 개별 체크박스 연동
        allClassCheck.addActionListener(e -> { //모든 선택 박스를 다 포함
            boolean selected = allClassCheck.isSelected();
            for (Component c : classCheckPanel.getComponents()) { //반 선택 패널에서 반 정보 가져오기
                ((JCheckBox) c).setSelected(selected);
            }
        });
        
        //클래스 선택 추가
        classRow.add(classLabel);
        classRow.add(allClassCheck);
        classRow.add(classCheckPanel);

        panel.add(dateRow);
        panel.add(classRow);

        return panel;
    }

    //날짜 스피너 생성
    private JSpinner makeDateSpinner() {
        SpinnerDateModel model = new SpinnerDateModel();
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        spinner.setPreferredSize(new Dimension(130, 32));
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "yyyy-MM-dd"); //날짜의 형식으로 스피너를 설정
        spinner.setEditor(editor);
        return spinner;
    }
    
    //미리보기 패널 생성
    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        // 미리보기 제목 레이블 생성
        JLabel previewLabel = new JLabel("미리보기");
        previewLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        previewLabel.setForeground(new Color(17, 24, 39));
        previewLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        // 테이블
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) { //셀 직접 편집 금지
                return false;
            }
        };
        
        //테이블 생성
        table = new JTable(tableModel);
        table.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        table.setRowHeight(36);
        table.setGridColor(new Color(243, 244, 246));
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(239, 246, 255));
        
        //테이블 헤더 설정
        table.getTableHeader().setFont(new Font("맑은 고딕", Font.PLAIN, 11));
        table.getTableHeader().setBackground(new Color(250, 250, 250));
        table.getTableHeader().setForeground(GRAY_TXT);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(233, 236, 239), 1));
        card.add(new JScrollPane(table), BorderLayout.CENTER);

        // 미리보기 버튼
        JButton previewBtn = new JButton("미리보기");
        previewBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        previewBtn.setFocusPainted(false);
        previewBtn.setBackground(WHITE);
        previewBtn.setOpaque(true);
        previewBtn.setBorderPainted(true);
        previewBtn.setBorder(BorderFactory.createCompoundBorder( //경계선 설정
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        previewBtn.addActionListener(e -> refreshPreview()); //미리보기 다시 불러오기
        
        // 엑셀 추출 버튼
        exportBtn = new JButton("엑셀 추출"); 
        exportBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        exportBtn.setBackground(BLUE);
        exportBtn.setForeground(WHITE);
        exportBtn.setContentAreaFilled(false);
        exportBtn.setOpaque(true);
        exportBtn.setBorderPainted(false);
        exportBtn.setFocusPainted(false);
        exportBtn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        exportBtn.addMouseListener(new MouseAdapter() { //버튼의 색깔 변경
            @Override public void mouseEntered(MouseEvent e)  { exportBtn.setBackground(BTN_HOVER); }
            @Override public void mouseExited(MouseEvent e)   { exportBtn.setBackground(BLUE);      }
            @Override public void mousePressed(MouseEvent e)  { exportBtn.setBackground(BTN_PRESS); }
            @Override public void mouseReleased(MouseEvent e) { exportBtn.setBackground(BTN_HOVER); }
        });
        exportBtn.addActionListener(e -> exportExcel());

        //버튼 패널 생성
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnRow.setBackground(BG);
        btnRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        btnRow.add(previewBtn);
        btnRow.add(Box.createHorizontalStrut(8));
        btnRow.add(exportBtn);
        
        //패널에 모든 미리보기 요소 추가
        panel.add(previewLabel, BorderLayout.NORTH);
        panel.add(btnRow, BorderLayout.BEFORE_FIRST_LINE);
        panel.add(card, BorderLayout.CENTER);

        return panel;
    }
    
    
    //미리보기 초기화
    private void refreshPreview() {
        // 날짜 범위
        java.util.Date startDate = (java.util.Date) startSpinner.getValue(); //시작 스피너에서 시작 날짜 불러오기
        java.util.Date endDate   = (java.util.Date) endSpinner.getValue(); //끝 스피너에서 끝 날짜 불러오기
        java.time.LocalDate start = startDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(); //Date에서 날짜만 추출한다.
        java.time.LocalDate end = endDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(); //Date에서 날짜만 추출한다.
        
        //예외처리(날짜 올바르게 잡기)
        if (start.isAfter(end)) {
            JOptionPane.showMessageDialog(this,
                "시작일이 종료일보다 늦습니다.", "날짜 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 선택된 반 목록
        java.util.List<Class_Info> selectedClasses = getSelectedClasses();
        if (selectedClasses.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "반을 하나 이상 선택하세요.", "선택 오류", JOptionPane.WARNING_MESSAGE); //반은 하나이상 선택
            return;
        }

        // 컬럼 구성 — 이름 + 날짜들
        java.util.List<String> columns = new java.util.ArrayList<>();
        columns.add("반");
        columns.add("이름");
        
        java.time.LocalDate d = start;
        while (!d.isAfter(end)) { //모든 날짜를 열에 추가
            columns.add(d.toString());
            d = d.plusDays(1);
        }
        tableModel.setColumnIdentifiers(columns.toArray());
        tableModel.setRowCount(0);

        // 행 추가
        for (Class_Info cls : selectedClasses) {
            for (Student s : DataManager.getStudent()) {
                if (s.getClass_Id() != cls.getClass_Id()) continue; //선택된 반만 미리보기에 표시
                java.util.List<Object> row = new java.util.ArrayList<>(); //행을 생성
                row.add(cls.getClass_Name());
                row.add(s.getName());
                java.time.LocalDate date = start; //시작 날짜로 지정
                while (!date.isAfter(end)) {
                    String status = getStatusStr(s.getStudent_id(), date); //출결 상태 불러오기
                    row.add(status);
                    date = date.plusDays(1);
                }
                //각 행을 추가
                tableModel.addRow(row.toArray());
            }
        }
    }
    
    //각 날짜의 출결 상태 불러오기
    private String getStatusStr(int studentId, java.time.LocalDate date) {
        for (AttendanceRecord r : DataManager.getRecords()) {
            if (r.getStudent_Id() == studentId && r.getDate().equals(date)) {
                return switch (r.getStatus()) { //상태에 따라서 string으로 반환
                    case LATE        -> "지각";
                    case ABSENT      -> "결석";
                    case EARLY_LEAVE -> "조퇴";
                    case EXCUSED     -> "공결";
                    default          -> "출석";
                };
            }
        }
        return ""; //없으면 빈칸으로 반환
    }

    //각 날짜의 결석/지각 등 사유 불러오기
    private String getReasonStr(int studentId, java.time.LocalDate date) {
        for (AttendanceRecord r : DataManager.getRecords()) {
            if (r.getStudent_Id() == studentId && r.getDate().equals(date)) {
                return r.getReason();
            }
        }
        return null; //없으면 null로 반환
    }

    //선택 된 반 가져오기
    private java.util.List<Class_Info> getSelectedClasses() {
        java.util.List<Class_Info> result = new java.util.ArrayList<>();
        Component[] checkBoxes = classCheckPanel.getComponents(); //반 선택 체크박스 전체 가져오기
        java.util.List<Class_Info> classes = DataManager.getClasses();
        for (int i = 0; i < checkBoxes.length; i++) {
            if (((JCheckBox) checkBoxes[i]).isSelected()) { //선택된 반만 result에 추가해서 반환
                result.add(classes.get(i));
            }
        }
        return result;
    }
    
    //엑셀 파일 추출 코드
    private void exportExcel() {
        // 날짜 범위
        java.util.Date startDate = (java.util.Date) startSpinner.getValue(); //시작 날짜 가져오기
        java.util.Date endDate   = (java.util.Date) endSpinner.getValue(); //끝 날짜 가져오기
        java.time.LocalDate start = startDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate end = endDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        
        //예외 처리(날짜와 반 선택)
        if (start.isAfter(end)) {
            JOptionPane.showMessageDialog(this,
                "시작일이 종료일보다 늦습니다.", "날짜 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<Class_Info> selectedClasses = getSelectedClasses();
        if (selectedClasses.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "반을 하나 이상 선택하세요.", "선택 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 저장 경로 선택
        JFileChooser fileChooser = new JFileChooser(); //저장항 위치를 선택
        fileChooser.setDialogTitle("저장 위치 선택"); //다이어로그 제목 설정
        fileChooser.setSelectedFile(new java.io.File("출석부.xlsx")); //미리 출석부.xlsx로 채워넣기
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel 파일 (*.xlsx)", "xlsx")); //파일 형식 지정
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return; //저장 다이얼로그를 띄워서 APPROVE_OPTION이 아니면 저장 다이어로그 꺼
        java.io.File file = fileChooser.getSelectedFile();
        if (!file.getName().endsWith(".xlsx")) { //확장자 체크, 없으면 자동으로 xlsx를 붙여준다.
            file = new java.io.File(file.getAbsolutePath() + ".xlsx");
        }

        final java.io.File targetFile = file;

        // 버튼 비활성화 + 진행 중 표시 (작업 중 중복 클릭 방지)
        exportBtn.setEnabled(false);
        exportBtn.setText("추출 중...");

        // 진행 중임을 알리는 non-modal 다이얼로그
        // modal=false 이므로 떠 있는 동안에도 메인 창(다른 탭, 메뉴 등) 조작 가능-부모창으로부터 자유
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog progressDialog = new JDialog(owner, "엑셀 추출 중", Dialog.ModalityType.MODELESS); //진행도를 확인하기 위한 다이얼로그
        JPanel progressPanel = new JPanel(new BorderLayout(0, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        JLabel progressLabel = new JLabel("엑셀 파일을 생성하고 있습니다...");
        progressLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true); // 진행률을 알 수 없으므로 무한 반복 애니메이션, 바에서 좌우로 계속해서 움직인다.
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressDialog.getContentPane().add(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setVisible(true);
        // setVisible(true)는 non-modal이므로 즉시 리턴됨 -> 아래 코드 계속 실행

        // 엑셀 생성은 시간이 걸릴 수 있으므로 백그라운드 스레드(SwingWorker)에서 처리
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                generateExcelFile(targetFile, start, end, selectedClasses);
                return null;
            }

            @Override
            protected void done() {
                // UI 갱신은 done()에서 (EDT 보장)
                progressDialog.dispose();
                exportBtn.setEnabled(true);
                exportBtn.setText("엑셀 추출");
                try {
                    get(); // doInBackground에서 발생한 예외 확인
                    JOptionPane.showMessageDialog(ExcelExportPanel.this,
                        "저장 완료: " + targetFile.getAbsolutePath(),
                        "완료", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ExcelExportPanel.this,
                        "추출 실패: " + ex.getCause(),
                        "오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    //실제 엑셀 워크북 생성 및 파일 저장 (백그라운드 스레드에서 실행됨)
    private void generateExcelFile(java.io.File file, java.time.LocalDate start, java.time.LocalDate end,
                                    java.util.List<Class_Info> selectedClasses) throws Exception {

        // 엑셀 생성
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            org.apache.poi.ss.usermodel.CellStyle headerStyle =workbook.createCellStyle(); //헤더스타일은 한 번만 만들어서 재사용한다.
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont(); //헤더폰트는 한 번만 만들어서 재사용한다.
            headerFont.setBold(true); //글씨 굵게
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor( new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte)37, (byte)99, (byte)235}, null)); //색깔 RGB( , , )로 지정(파란색) 
            headerStyle.setFillPattern( //채우기
                org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setAlignment(
                org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            // 반별 시트 생성
            for (Class_Info cls : selectedClasses) { //선택된 반별로 시트 생성
                org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet(cls.getClass_Name()); //반 이름으로 시트 생성
                org.apache.poi.ss.usermodel.Drawing<?> drawing = sheet.createDrawingPatriarch(); //사유 메모(코멘트)를 달기 위한 드로잉 영역
                org.apache.poi.ss.usermodel.CreationHelper helper = workbook.getCreationHelper();

                // 헤더 행 — 이름 + 날짜들 + 통계
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0); //헤더 행 생성
                int col = 0;
                headerRow.createCell(col++).setCellValue("이름");//헤더에 이름 추가

                java.util.List<java.time.LocalDate> dates = new java.util.ArrayList<>(); //헤더에 날짜들 추가
                java.time.LocalDate d = start;
                while (!d.isAfter(end)) {
                    dates.add(d);
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(col++);
                    cell.setCellValue(d.toString());
                    cell.setCellStyle(headerStyle);
                    d = d.plusDays(1);
                }
                // 통계 헤더
                for (String stat : new String[]{"출석", "지각", "결석", "조퇴", "공결"}) { //헤더에 각 출결 결과 추가
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(col++);
                    cell.setCellValue(stat);
                    cell.setCellStyle(headerStyle);
                }
                headerRow.getCell(0).setCellStyle(headerStyle);

                // 학생별 행
                int rowIdx = 1;
                for (Student s : DataManager.getStudent()) {
                    if (s.getClass_Id() != cls.getClass_Id()) continue; //선택된 반의 데이터만 사용
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++); //학생 정보를 위한 행을 생성
                    col = 0;
                    row.createCell(col++).setCellValue(s.getName()); //

                    int present = 0, late = 0, absent = 0, earlyLeave = 0, excused = 0;
                    for (java.time.LocalDate date : dates) { //통계 계산
                        String status = getStatusStr(s.getStudent_id(), date); //학생과 날짜를 입려 받아서 상태를 String 타입으로 받기
                        org.apache.poi.ss.usermodel.Cell dateCell = row.createCell(col++);
                        dateCell.setCellValue(status);

                        // 사유가 입력된 날짜는 셀에 메모(코멘트)로 사유 표시 (기본적으로 접힌/숨김 상태)
                        String reason = getReasonStr(s.getStudent_id(), date);
                        if (reason != null && !reason.isBlank()) {
                            org.apache.poi.ss.usermodel.ClientAnchor anchor = helper.createClientAnchor();
                            anchor.setCol1(dateCell.getColumnIndex());
                            anchor.setCol2(dateCell.getColumnIndex() + 2);
                            anchor.setRow1(row.getRowNum());
                            anchor.setRow2(row.getRowNum() + 3);
                            org.apache.poi.ss.usermodel.Comment comment = drawing.createCellComment(anchor);
                            comment.setString(helper.createRichTextString(reason));
                            comment.setVisible(false); // 메모 기본 상태를 접힘(숨김)으로 설정
                            dateCell.setCellComment(comment);
                        }

                        switch (status) {
                            case "출석" -> present++;
                            case "지각" -> late++;
                            case "결석" -> absent++;
                            case "조퇴" -> earlyLeave++;
                            case "공결" -> excused++;
                        }
                    }
                    // 통계 출력
                    row.createCell(col++).setCellValue(present);
                    row.createCell(col++).setCellValue(late);
                    row.createCell(col++).setCellValue(absent);
                    row.createCell(col++).setCellValue(earlyLeave);
                    row.createCell(col++).setCellValue(excused);
                }

                // 열 너비 자동 조정
                for (int i = 0; i < col; i++) {
                    sheet.autoSizeColumn(i);
                }
                // 통계 열 5개 너비 고정 (dates.size() + 1 이후부터)
                int statStartCol = dates.size() + 1;
                for (int i = statStartCol; i < statStartCol + 5; i++) {
                    sheet.setColumnWidth(i, 2500);
                    sheet.setColumnWidth(0, 3500); // 이름 열 너비 고정
                }
                
                // 빈 행 구분
                sheet.createRow(rowIdx++);

                // 반 전체 통계 헤더 행
                org.apache.poi.ss.usermodel.Row totalHeaderRow = sheet.createRow(rowIdx++);
                org.apache.poi.ss.usermodel.Cell totalHeaderCell = totalHeaderRow.createCell(0);
                totalHeaderCell.setCellValue("반 전체 통계");
                totalHeaderCell.setCellStyle(headerStyle);

                // 반 전체 합산
                int totalPresent = 0, totalLate = 0, totalAbsent = 0,
                    totalEarlyLeave = 0, totalExcused = 0;

                for (Student s : DataManager.getStudent()) { //학생별 출결 상태를 불러와 전테 통계에 합산
                    if (s.getClass_Id() != cls.getClass_Id()) continue;
                    for (java.time.LocalDate date : dates) {
                        String status = getStatusStr(s.getStudent_id(), date);
                        switch (status) {
                            case "출석" -> totalPresent++;
                            case "지각" -> totalLate++;
                            case "결석" -> totalAbsent++;
                            case "조퇴" -> totalEarlyLeave++;
                            case "공결" -> totalExcused++;
                        }
                    }
                }

                // 반 전체 통계 데이터 행
                org.apache.poi.ss.usermodel.Row totalRow = sheet.createRow(rowIdx++);
                totalRow.createCell(0).setCellValue("합계");
                int statCol = dates.size() + 1;
                //통계 추가
                totalRow.createCell(statCol++).setCellValue(totalPresent); 
                totalRow.createCell(statCol++).setCellValue(totalLate);
                totalRow.createCell(statCol++).setCellValue(totalAbsent);
                totalRow.createCell(statCol++).setCellValue(totalEarlyLeave);
                totalRow.createCell(statCol++).setCellValue(totalExcused);
            }

            // 파일 저장
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                workbook.write(fos);
            }
        }
    }
    
    //초기화
    public void refresh() {
        classCheckPanel.removeAll();
        for (Class_Info cls : DataManager.getClasses()) { //데이터 확인해서 초기화
            JCheckBox cb = new JCheckBox(cls.getClass_Name());
            cb.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            cb.setBackground(WHITE);
            cb.setSelected(true);
            // 개별 체크박스 변경 시 전체 체크박스 상태 갱신
            cb.addActionListener(e -> {
                boolean allSelected = true;
                for (Component c : classCheckPanel.getComponents()) {
                    if (!((JCheckBox) c).isSelected()) {
                        allSelected = false;
                        break;
                    }
                }
                allClassCheck.setSelected(allSelected);
            });
            classCheckPanel.add(cb);
        }
        classCheckPanel.revalidate();
        classCheckPanel.repaint();
    }
}