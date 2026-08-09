package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 스마트폰·화면 사용 시간 — 일지 <b>참고 항목</b>.
 *
 * <p><b>비채점</b> — {@link ScoredOption} 을 구현하지 않는다. 성인 화면시간에 대한 국제
 * 표준·역치가 없기 때문이다(기획 일지 §3 참고 항목 표). 취침 전 사용만 수면에 영향을
 * 준다는 근거가 있으나 그건 이 항목이 측정하는 값이 아니다.
 */
public enum ScreenTime {

    /** 2시간 이하 */
    UNDER_2,

    /** 2~4시간 */
    TWO_TO_FOUR,

    /** 4~6시간 */
    FOUR_TO_SIX,

    /** 6시간 이상 */
    OVER_6
}
