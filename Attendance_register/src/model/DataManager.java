package model;

import java.io.*; //csv파일 읽기/쓰기
import java.time.*; //출석 기록의 날짜
import java.util.*; //자료구조 불러오기

public class DataManager {
	private static final String STUDENTS_FILE = "data/students.csv";
	private static final String CLASSES_FILE = "data/classes.csv";
	private static final String ATTENDANCE_FILE = "data/attendance.csv";
	
	private static ArrayList<Student> students = new ArrayList<>();
	private static ArrayList<Class_Info> classes = new ArrayList<>();
	private static ArrayList<AttendanceRecord> records = new ArrayList<>();
	
	//Hashmap으로 빠른 검색
	private static HashMap<Integer, Student>   studentMap = new HashMap<>();
	private static HashMap<Integer, Class_Info> classMap  = new HashMap<>();
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
		classMap.clear();
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
				classMap.put(cls.getClass_Id(), cls);
			}
		}catch(IOException e) {
			System.err.println("반 데이터 로드 실패: " + e.getMessage());
		}
	}
	
	private static void saveClasses() {
		new File("data").mkdirs();
		try(PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(CLASSES_FILE), "UTF-8"))){
			pw.println("class_id,class_name");//헤더 작성
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
		studentMap.clear();
		File file = new File(STUDENTS_FILE);
		if(!file.exists()) {
			addDefaultStudents();
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
				s.setGuardian_Phone(parts[5].trim()); //guardian_phone
				students.add(s);
				studentMap.put(s.getStudent_id(), s);
			}
		}catch(IOException e) {
			System.err.println("학생 데이터 로드 실패: " + e.getMessage());
		}
	}
	
	private static void saveStudents() {
		new File("data").mkdirs(); //파일이 없을 시 생성
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(STUDENTS_FILE), "UTF-8"))){
			pw.println("student_id,name,school,class_id,student_phone,guardian_phone");
			for (Student s : students) {
                pw.println(s.getStudent_id() + "," + s.getName() + "," +
                           s.getSchool() + "," + s.getClass_Id() + "," +
                           (s.getStudent_Phone()  == null ? "" : s.getStudent_Phone()) + "," +
                           (s.getGuardian_Phone() == null ? "" : s.getGuardian_Phone()));
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
    public static ArrayList<Class_Info> getClasses(){return classes;}
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
    	return studentMap.get(id);
    }
    //class_id로 반 조회
    public static Class_Info findClassById(int id) {
    	return classMap.get(id); //없다명 null 반환
    }
    
    //출석 기록 추가/수정
    public static void addRecord(AttendanceRecord record) {
    	records.add(record);
    }
    public static void updateRecord(int student_id, LocalDate date, AttendanceStatus status, String reason) {
    	for (AttendanceRecord r : records) {
    		//해당 하는 학생이 있는 경우 업데이트
    		if (r.getStudent_Id() == student_id && r.getDate().equals(date)) {
    			r.setStatus(status);
    			r.setReason(reason);
    			return;
    		}
    	}
    	
    	//학생이 없으면 새로 추가
    	Student s = findStudentById(student_id);
    	if ( s!=null) {
    		addRecord(new AttendanceRecord(student_id, s.getClass_Id(), date, status, reason));
    	}
    }
    
    //기본 데이터(csv 파일이 없을 때 만들 테스트용 데이터)
    private static void addDefaultClasses() {
        Class_Info c1 = new Class_Info(1, "1반");
        Class_Info c2 = new Class_Info(2, "2반");
        Class_Info c3 = new Class_Info(3, "3반");
        classes.add(c1); classMap.put(1, c1); //ArrayList와 HashMap 둘 다 저장
        classes.add(c2); classMap.put(2, c2);
        classes.add(c3); classMap.put(3, c3);
        saveClasses();
    }
 
    private static void addDefaultStudents() {
        students.add(new Student("김철수", "인하중학교", 1));
        students.get(0).setStudent_Id(1);
        students.get(0).setStudent_Phone("010-1234-5678");
        students.get(0).setGuardian_Phone("010-1111-2222");
 
        students.add(new Student("이영희", "인하중학교", 1));
        students.get(1).setStudent_Id(2);
        students.get(1).setStudent_Phone("010-2345-6789");
        students.get(1).setGuardian_Phone("010-2222-3333");
 
        students.add(new Student("박민수", "인하중학교", 2));
        students.get(2).setStudent_Id(3);
        students.get(2).setStudent_Phone("010-3456-7890");
        students.get(2).setGuardian_Phone("010-3333-4444");
 
        students.add(new Student("정수진", "인하중학교", 2));
        students.get(3).setStudent_Id(4);
        students.get(3).setStudent_Phone("010-4567-8901");
        students.get(3).setGuardian_Phone("010-4444-5555");
 
        students.add(new Student("최지훈", "인하중학교", 3));
        students.get(4).setStudent_Id(5);
        students.get(4).setStudent_Phone("010-5678-9012");
        students.get(4).setGuardian_Phone("010-5555-6666");
 
        students.add(new Student("강서연", "인하중학교", 3));
        students.get(5).setStudent_Id(6);
        students.get(5).setStudent_Phone("010-6789-0123");
        students.get(5).setGuardian_Phone("010-6666-7777");
        
        //HashMap에도 저장
        for (Student s : students) {
        	studentMap.put(s.getStudent_id(), s);
        }
        saveStudents();
    }
 
    private static void addDefaultAttendance() {
        LocalDate today = LocalDate.now();
        records.add(new AttendanceRecord(1, 1, today, AttendanceStatus.PRESENT, ""));
        records.add(new AttendanceRecord(2, 1, today, AttendanceStatus.LATE, "교통 지연"));
        records.add(new AttendanceRecord(3, 2, today, AttendanceStatus.PRESENT, ""));
        records.add(new AttendanceRecord(4, 2, today, AttendanceStatus.ABSENT, "병가"));
        records.add(new AttendanceRecord(5, 3, today, AttendanceStatus.PRESENT, ""));
        records.add(new AttendanceRecord(6, 3, today, AttendanceStatus.EARLY_LEAVE,"병원 방문"));
        saveAttendance();
    }
}
