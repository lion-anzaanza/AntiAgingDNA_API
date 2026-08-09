package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 당일 운동 시간 — 일지. 근거: WHO 2020 주 150~300분 중강도.
 *
 * <p>방향: <b>정(단조 증가)</b>. 앵커는 기획 §8.
 *
 * <p>"안 함 = 0점"은 이 enum 이 아니라 {@code diary.exercised = false} 로 표현한다
 * — 운동을 안 한 날은 시간·종류를 물을 필요가 없으므로 두 컬럼이 NULL 이 된다.
 */
@Getter
public enum ExerciseDuration implements ScoredOption {

    /** 15분 이하 */
    UNDER_15(30),

    /** 30분 */
    ABOUT_30(55),

    /** 1시간 */
    ABOUT_60(80),

    /** 1시간 이상 */
    OVER_60(100);

    private final int score;

    ExerciseDuration(int score) {
        this.score = score;
    }
}
