package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 당일 운동 종류 — 일지.
 *
 * <p><b>비채점</b> — {@link ScoredOption} 을 구현하지 않는다. WHO 2020 이 "근력 주 2회"를
 * 별도로 권고하지만, 기획 §8 정규화 기준표에 운동 종류별 앵커가 정의돼 있지 않다.
 * 근거 없는 수치를 지어내지 않기 위해 기록만 하고 점수에는 반영하지 않는다.
 * 주간 근력 횟수 채점을 도입하려면 기획에서 앵커를 먼저 확정해야 한다.
 */
public enum ExerciseType {

    /** 걷기 */
    WALKING,

    /** 유산소 */
    AEROBIC,

    /** 근력 */
    STRENGTH,

    /** 근력 + 유산소 */
    STRENGTH_AND_AEROBIC
}
