package model;


public class Student {
	private String name;
	private String school;
	private int student_id;
	private int class_id;
	private String student_phone;
	private String guardian_phone;

	// 서버와 마지막으로 동기화한 이후 내용이 바뀌었는지 표시.
	// DataManager.saveStudents()가 이미 서버와 같은 상태인 학생까지 매번 PUT으로
	// 다시 보내지 않고, 실제로 바뀐 학생만 골라 보낼 수 있도록 하기 위한 용도.
	private boolean dirty = true;

	public Student (String name, String school, int class_id){
		setName(name);
		setSchool(school);
		setClass_Id(class_id);
	}

	public void setName(String name) {
		this.name = name;
		this.dirty = true;
	}
	public String getName() {
		return name;
	}

	public void setSchool(String school) {
		this.school=school;
		this.dirty = true;
	}
	public String getSchool() {
		return school;
	}

	public void setStudent_Id(int student_id) {
		this.student_id=student_id;
	}
	public int getStudent_id() {
		return student_id;
	}

	public void setClass_Id(int class_id) {
		this.class_id=class_id;
		this.dirty = true;
	}
	public int getClass_Id() {
		return class_id;
	}

	public void setStudent_Phone(String student_phone) {
		this.student_phone=student_phone;
		this.dirty = true;
	}
	public String getStudent_Phone() {
		return student_phone;
	}

	public void setGuardian_Phone(String guardian_phone) {
	    this.guardian_phone = guardian_phone;
	    this.dirty = true;
	}
	public String getGuardian_Phone() {
	    return guardian_phone;
	}

	public boolean isDirty() { return dirty; }
	public void clearDirty() { dirty = false; }
}
