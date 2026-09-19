package model;


import java.util.*;

public class Class_Info {
	private int class_id;
	private String class_name;
	private Vector<Student> student_list;

	// 서버와 마지막으로 동기화한 이후 내용이 바뀌었는지 표시.
	// DataManager.saveClasses()가 이미 서버와 같은 상태인 반까지 매번 PUT으로
	// 다시 보내지 않고, 실제로 바뀐 반만 골라 보낼 수 있도록 하기 위한 용도.
	private boolean dirty = true;

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
		this.dirty = true;
	}
	public String getClass_Name() {
		return class_name;
	}

	public boolean isDirty() { return dirty; }
	public void clearDirty() { dirty = false; }
}
