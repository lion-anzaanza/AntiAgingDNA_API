package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 당일 걸은 시간 — 일지 <b>참고 항목</b>.
 *
 * <p><b>비채점</b> — {@link ScoredOption} 을 구현하지 않는다. 별도 표준이 없고
 * {@link ExerciseDuration}(운동) 항목과 중복되기 때문이다(기획 일지 §3 참고 항목 표).
 *
 * <p>기획에는 구간이 "30분↓ ~ 2시간↑" 로만 적혀 있고 중간 구간이 명시돼 있지 않다.
 * {@link SittingHours} 등 다른 구간 칩과 같은 4구간 형태로 잠정 정의했다 — 화면 확정 시 대조할 것.
 */
public enum WalkDuration {

    /** 30분 이하 */
    UNDER_30,

    /** 30분 ~ 1시간 */
    THIRTY_TO_60,

    /** 1 ~ 2시간 */
    ONE_TO_TWO_HOURS,

    /** 2시간 이상 */
    OVER_2_HOURS
}
