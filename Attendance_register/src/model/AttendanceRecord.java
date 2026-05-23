package model;

import java.time.*;

public class AttendanceRecord {
	private int student_id;
	private int class_id;
	private LocalDate date;
	private AttendanceStatus status;
	private String reason;
	
	public AttendanceRecord(int student_id, int class_id, LocalDate date, AttendanceStatus status, String reason) {
		this.student_id = student_id;
		this.class_id = class_id;
		this.date = date;
		this.status = status;
		this.reason = reason;
	}
	
	public int getStudent_Id() {
		return student_id;
	}
	public int getClass_Id() {
		return class_id;
	}
	public LocalDate getDate() {
		return date;
	}
	public AttendanceStatus getStatus() {
		return status;
	}
	public String getReason() {
		return reason;
	}
	
	public void setStatus(AttendanceStatus status) {
		this.status = status;
	}
	public void setReason(String reason) {
		this.reason = reason;
	}
	
	//CSV 변환
	public String toCsv() {
		return student_id + "," + class_id + "," + date + "," + status.name() + "," + (reason == null ? "" : reason);
	}
	
	//CSV에서 입력을 받아 객체 생성
	public static AttendanceRecord fromCsv(String line) {
		String[] parts = line.split(",", -1);//빈값도 포함해서 분리
		//문장의 각 요소들을 추출하여 객체를 생성
		int student_id = Integer.parseInt(parts[0].trim()); //student_id
		int class_id = Integer.parseInt(parts[1].trim()); //class_id
		LocalDate date = LocalDate.parse(parts[2].trim()); //date
		AttendanceStatus status = AttendanceStatus.valueOf(parts[3].trim()); //status
		String reason = parts[4].trim(); //reason
		return new AttendanceRecord(student_id, class_id, date, status, reason);
	}
}
