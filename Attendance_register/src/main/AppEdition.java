package main;

// 배포판 구분. 베타 테스터용 빌드에서는 아직 확정되지 않은 기능을 잠시 감춘다.
// 감춘 기능의 코드는 그대로 두었으므로 BETA를 false로 바꾸면 정식판 동작으로 돌아간다.
//   - 구글 드라이브 동기화: 설정 메뉴에서 숨기고, 시작/종료 시 동기화도 하지 않음 (build_dist.ps1은 google-client.json을 넣지 않음)
//   - 라이선스: 설정 메뉴의 "라이선스 등록"을 숨김 (데모 제한은 서버의 PlanService.BETA가 끔)
// build_dist.ps1이 이 줄을 읽어 베타 여부를 판단하므로 형식(BETA = true/false)을 바꾸지 말 것
public final class AppEdition {
    public static final boolean BETA = true;

    private AppEdition() {}
}
