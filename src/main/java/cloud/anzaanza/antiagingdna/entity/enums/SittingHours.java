package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 앉아 있던 시간(좌식) — 일지. 근거: WHO 2020 좌식 시간 제한 권고.
 *
 * <p>방향: <b>역(단조 감소)</b> — U자가 아니다. 8시간을 넘어서면 감점이 가팔라진다
 * (기획 일지 §5 정정 사항). 앵커는 기획 §8.
 */
@Getter
public enum SittingHours implements ScoredOption {

    /** 4시간 이하 */
    UNDER_4(100),

    /** 4~8시간 */
    FOUR_TO_EIGHT(75),

    /** 8~10시간 */
    EIGHT_TO_TEN(45),

    /** 10시간 이상 */
    OVER_10(15);

    private final int score;

    SittingHours(int score) {
        this.score = score;
    }
}
