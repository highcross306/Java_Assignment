package model;


public enum AttendanceStatus {
    PRESENT("출석"),
    LATE("지각"),
    ABSENT("결석"),
    EARLY_LEAVE("조퇴"),
    EXCUSED("공결");

    private final String korean;

    AttendanceStatus(String korean) {
        this.korean = korean;
    }

    public String getKorean() {
        return korean;
    }
}