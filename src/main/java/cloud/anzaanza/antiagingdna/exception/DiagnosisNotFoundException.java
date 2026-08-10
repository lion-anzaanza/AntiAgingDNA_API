package cloud.anzaanza.antiagingdna.exception;

/**
 * 초기 진단이 없는 사용자 — 404 Not Found.
 *
 * <p>가입이 진단을 포함한 한 트랜잭션이라 정상 경로에서는 발생하지 않는다. 스키마 초기화 등으로
 * {@code user} 만 남고 {@code dna_info} 가 사라진 상태를 조용히 넘기지 않기 위해 둔다.
 */
public class DiagnosisNotFoundException extends RuntimeException {

    public DiagnosisNotFoundException(String userId) {
        super("초기 진단 기록이 없습니다: " + userId);
    }
}
