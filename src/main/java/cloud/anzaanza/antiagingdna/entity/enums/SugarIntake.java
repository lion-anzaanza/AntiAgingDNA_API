package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 당분 섭취 횟수(패스트푸드·디저트·가당음료) — 일지. 근거: WHO 2015 자유당 총열량 10% 미만.
 *
 * <p>방향: <b>역</b>. 앵커는 기획 §8.
 *
 * <p>이 항목의 감점분에는 {@link SensitivityLevel} 로 산출한 {@code k_sugar} 를 곱한다
 * — 혈당 반응의 개인차가 크다는 근거(Zeevi et al., 2015)에 따른 것이다.
 */
@Getter
public enum SugarIntake implements ScoredOption {

    /** 0회 */
    NONE(100),

    /** 1~2회 */
    ONE_TO_TWO(55),

    /** 3회 이상 */
    THREE_OR_MORE(15);

    private final int score;

    SugarIntake(int score) {
        this.score = score;
    }
}
