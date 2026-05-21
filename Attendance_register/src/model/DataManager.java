package model;

import java.io.*; //csv 파일 읽기/쓰기
import java.time.*; //출석 기록의 날짜
import java.util.*; //자료구조 불러오기
import java.util.stream.*; // stream결과 편집

public class DataManager {
	private static final String STUDENTS_FILE = "data/students.csv";
	private static final String CLASSES_FILE = "data/classes.csv";
	private static final String ATTENDANCE_FILE = "data/attendance.csv";
	
	private static ArrayList<Student> students = new ArrayList<>();
	private static ArrayList<Class_Info> classes = new ArrayList<>();
	private static ArrayList<AttendanceRecord> records = new ArrayList<>();
	
	//프로그램 시작 시 호출, 전체 로드
	public static void loadAll() {
		loadClasses();
		loadStudents();
		loadAttendance();
	}
	//프로그램 종료 또는 저장 시 호출, 젠체 저장
	public static void saveAll() {
		saveClasses();
		saveStudents();
		saveAttendance();
	}
	
	//반 호출/저장
	private static void loadClasses() {
		classes.clear();
		File file = new File(CLASSES_FILE); //파일 불러오기
		if(!file.exists()) {
			addDefaultClasses(); //파일 없으면 기본 데이터 생성
			return;
		}
		//파일을 바이트 단위로 읽고, 바이트를 UTF-8로 변환하고, readLine()으로 한 줄씩 감쌈
		try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))){
			String line;
			br.readLine(); //헤더 스킵
			while((line = br.readLine()) != null) { //줄이 없을 때까지 반복
				if (line.trim().isEmpty()) continue;
				String[] parts = line.split(",", -1); //CSV 한 줄 분리
				Class_Info cls = new Class_Info(
						Integer.parseInt(parts[0].trim()), //class_id
						parts[1].trim() //class_name
				);
				classes.add(cls);
			}
		}catch(IOException e) {
			System.err.println("반 데이터 로드 실패: " + e.getMessage());
		}
	}
	
	private static void saveClasses() {
		new File("data").mkdirs();
		try(PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(CLASSES_FILE), "UTF-8"))){
			pw.println("class_id, class_name");//헤더 작성
			for(Class_Info cls : classes) {
				pw.println(cls.getClass_Id() + "," + cls.getClass_Name());
			}
		}catch(IOException e) {
			System.err.println("반 데이터 저장 실패: " + e.getMessage());
		}
	}
}
