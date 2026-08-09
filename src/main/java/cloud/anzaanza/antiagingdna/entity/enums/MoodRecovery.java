package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 당일 기분 전환·회복 활동 — 일지. 감정 영역의 두 번째 소스(첫 번째는 스트레스).
 *
 * <p>근거: 행동활성화(BA) 체계적 문헌고찰 — 긍정·의미 활동이 기분을 개선한다.
 * 기획 일지 §3 은 이 항목을 <b>채점 항목</b>(방향 정)으로 분류한다.
 *
 * <p><b>앵커 미정이라 {@link ScoredOption} 을 구현하지 않는다.</b> 기획 §8 정규화 기준표에
 * 이 항목의 행이 없고, 워크드 예시 B-3 의 '잠깐 = 60' 한 점만 확인된다. 나머지 두 값을
 * 채우려면 추측이 필요한데, 그건 {@link ExerciseType} 을 비채점으로 둔 기준과 어긋난다.
 * <b>기획에서 앵커 3개가 확정되면 그때 {@code ScoredOption} 을 구현한다.</b>
 */
public enum MoodRecovery {

    /** 안 함 */
    NONE,

    /** 잠깐 */
    BRIEF,

    /** 충분히 */
    ENOUGH
}
