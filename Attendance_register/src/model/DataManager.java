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
	
	//학생 호출/저장
	private static void loadStudents() {
		students.clear();
		File file = new File(STUDENTS_FILE);
		if(!file.exists()) {
			addDeafaultStudents();
			return;
		}
		try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))){
			String line;
			br.readLine(); //헤더 스킵
			while((line = br.readLine()) != null) { //파일 안의 내용들 읽기
				if(line.trim().isEmpty()) continue; //줄이 비어 있으면 스킵
				String[] parts = line.split(",", -1);
				Student s = new Student(
					parts[1].trim(), //name
					parts[2].trim(), //school
					Integer.parseInt(parts[3].trim()) //class_id
				);
				s.setStudent_Id(Integer.parseInt(parts[0].trim())); //student_id
				s.setStudent_Phone(parts[4].trim()); //phone_num
				students.add(s);
			}
		}catch(IOException e) {
			System.err.println("학생 데이터 로드 실패: " + e.getMessage());
		}
	}
	
	private static void saveStudents() {
		new File("data").mkdirs(); //파일이 없을 시 생성
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(STUDENTS_FILE), "UTF-8"))){
			pw.println("student_id, name, school, class_id, student_phone");
			for (Student s : students) {
                pw.println(s.getStudent_id() + "," + s.getName() + "," +
                           s.getSchool() + "," + s.getClass_Id() + "," +
                           (s.getStudent_Phone() == null ? "" : s.getStudent_Phone()));
			}
		}catch (IOException e) {
			System.err.println("학생 데이터 저장 실패: " + e.getMessage());
		}
	}
	
	//출석 기록 호출/저장
    private static void loadAttendance() {
        records.clear();
        File file = new File(ATTENDANCE_FILE);
        if (!file.exists()) {
            addDefaultAttendance();
            return;
        }
        //폰트 깨짐 방지를 위해 UTF-8로 불러오기
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            br.readLine(); // 헤더 스킵
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                records.add(AttendanceRecord.fromCsv(line));
            }
        } catch (IOException e) {
            System.err.println("출석 데이터 로드 실패: " + e.getMessage());
        }
    }
 
    private static void saveAttendance() {
        new File("data").mkdirs(); //파일 없을 시 생성
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(ATTENDANCE_FILE), "UTF-8"))) {
            pw.println("student_id,class_id,date,status,reason"); // 헤더
            for (AttendanceRecord r : records) {
                pw.println(r.toCsv());
            }
        } catch (IOException e) {
            System.err.println("출석 데이터 저장 실패: " + e.getMessage());
        }
    }
    
    //get 메소드
    public static ArrayList<Student> getStudent(){return students;}
    public static ArrayList<Class_Info> getClassese(){return classes;}
    public static ArrayList<AttendanceRecord> getRecords(){return records;}
    //오늘의 날짜
    public static ArrayList<AttendanceRecord> getTodayRecords(){
    	LocalDate today = LocalDate.now(); //오늘 날짜 불러오기
    	ArrayList<AttendanceRecord> result = new ArrayList<>();
    	for (AttendanceRecord r : records) {
    		if(r.getDate().equals(today)) { //오늘의 출석 기록들 불러오기
    			result.add(r);
    		}
    	}
    	return result;  //오늘의 출석 기록 반환
    }
    //오늘 status 상태의 학생 수
    public static int countTodayByStatus(AttendanceStatus status) {
    	int count = 0;
    	for(AttendanceRecord r: getTodayRecords()) {
    		if (r.getStatus() == status) count++;
    	}
    	return count;
    }
    //student_id로 학생 조회
    public static Student findStudentById(int id) {
    	for (Student s: students) {
    		if(s.getStudent_id() == id) {
    			return s;
    		}
    	}
    	return null; //없다명 null 반환
    }
    //class_id로 반 조회
    public static Class_Info findClassById(int id) {
    	for(Class_Info c : classes) {
    		if(c.getClass_Id() == id){
    			return c;
    		}
    	}
    	return null; //없다명 null 반환
    }
}
