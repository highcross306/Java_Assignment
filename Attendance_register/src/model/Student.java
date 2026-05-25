package model;


public class Student {
	private String name;
	private String school;
	private int student_id;
	private int class_id;
	private String student_phone;
	private String guardian_phone;
	
	public Student (String name, String school, int class_id){
		setName(name);
		setSchool(school);
		setClass_Id(class_id);
	}
	
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	
	public void setSchool(String school) {
		this.school=school;
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
	}
	public int getClass_Id() {
		return class_id;
	}
	
	public void setStudent_Phone(String student_phone) {
		this.student_phone=student_phone;
	}
	public String getStudent_Phone() {
		return student_phone;
	}
	
	public void setGuardian_Phone(String guardian_phone) {
	    this.guardian_phone = guardian_phone;
	}
	public String getGuardian_Phone() {
	    return guardian_phone;
	}
}
