package test;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;

import java.io.FileOutputStream;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Workbook 생성
            XSSFWorkbook wb = new XSSFWorkbook();
            XSSFSheet sheet = wb.createSheet("Table");

            // 2. 데이터 작성
            String[][] data = {
                {"이름", "나이", "학과"},
                {"홍길동", "20", "컴공"},
                {"김철수", "22", "전자"}
            };

            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < data[i].length; j++) {
                    row.createCell(j).setCellValue(data[i][j]);
                }
            }

            // 3. 테이블 생성

            AreaReference reference = new AreaReference(
                new CellReference(0, 0),
                new CellReference(2, 2),
                wb.getSpreadsheetVersion()
            );
            XSSFTable table = sheet.createTable(reference);

            // 5. 테이블 스타일
            table.getCTTable().addNewTableStyleInfo().setName("TableStyleMedium2");

            // 6. 파일 저장 (상대경로)
            FileOutputStream fos = new FileOutputStream("table.xlsx");
            wb.write(fos);

            fos.close();
            wb.close();

            System.out.println("엑셀 테이블 생성 완료");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}