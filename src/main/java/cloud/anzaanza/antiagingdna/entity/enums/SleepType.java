package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 수면 유형(크로노타입) — 초기 진단 Q1. 근거: MEQ (Horne &amp; Östberg, 1976).
 *
 * <p>점수 문항이 아니라 <b>파라미터 문항</b>이다(기획 §A-2). 유형별로 취침 시각 채점의
 * 기준점을 이동시키는 데 쓰인다.
 *
 * <p>기준점은 여기 두지 않는다. 기획 §6② 는 아침형 22~23시 · 저녁형 24~01시 · 일반형
 * 23~24시까지만 정하고 <b>예민형은 "baseline 폭 축소"라고만 적혀 있어 구간도 축소 계수도
 * 없다</b>. 네 값 중 하나가 비어 있는 표를 enum 에 박아 넣으면 그 자리를 추측으로 채우게
 * 되므로, 기획이 확정될 때까지 채점 계층의 매핑 테이블로 남긴다.
 */
public enum SleepType {

    /** 아침형 */
    MORNING,

    /** 저녁형 */
    EVENING,

    /** 일반형 */
    NORMAL,

    /** 예민형 */
    SENSITIVE
}
