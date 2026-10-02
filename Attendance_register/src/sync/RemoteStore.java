package sync;

import java.io.IOException;

// 암호화된 데이터 파일 하나를 보관하는 원격 저장소. 실제 구현은 구글 드라이브(DriveClient)이고,
// 동기화 로직(DriveSync)을 네트워크 없이 시험할 수 있도록 인터페이스로 분리해 둠
public interface RemoteStore {

    // version: 이 파일이 올라갈 때마다 1씩 증가하는 번호 (PC 간에 "누가 더 최신인지" 판단하는 기준)
    record Meta(long version, String deviceId, String modifiedTime) {}

    // 원격에 파일이 없으면 null
    Meta head() throws IOException;

    byte[] download() throws IOException;

    void upload(byte[] data, long version, String deviceId) throws IOException;
}
