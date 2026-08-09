package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 평소 사람들과의 교류 빈도(Ⓑ 4점) — 초기 진단 Q11. 사회 영역 baseline 의 <b>유일한</b> 소스.
 *
 * <p>근거: Holt-Lunstad et al., 2015 — 사회적 고립·외로움이 사망위험을 높인다.
 * 앵커는 기획 §A-1.
 *
 * <p>Phase 2 — 기획에는 확정돼 있으나 목업 `02_INITIAL_DIAGNOSIS` 에 반영돼 있지 않다.
 * 이 문항과 {@code diary.social_contact} 가 둘 다 없으면 사회 영역 점수가 산출되지 않는다.
 */
@Getter
public enum SocialContactLevel implements ScoredOption {

    /** 거의 안 함 */
    RARELY(30),

    /** 주 1~2회 */
    ONE_TO_TWO_PER_WEEK(60),

    /** 주 3~4회 */
    THREE_TO_FOUR_PER_WEEK(85),

    /** 거의 매일 */
    ALMOST_DAILY(100);

    private final int score;

    SocialContactLevel(int score) {
        this.score = score;
    }
}
