package model;


import java.util.*;

public class Class_Info {
	private int class_id;
	private String class_name;
	private Vector<Student> student_list;
	
	public Class_Info(int class_id, String class_name){
		setClass_Id(class_id);
		setClass_Name(class_name);
		student_list=new Vector<>();
	}
	
	public void addStudent(Student student) {
		student_list.add(student);
	}
	
	public void setClass_Id(int class_id) {
		this.class_id=class_id;
	}
	public int getClass_Id() {
		return class_id;
	}
	
	public void setClass_Name(String class_name) {
		this.class_name=class_name;
	}
	public String getClass_Name() {
		return class_name;
	}
	
	
}
