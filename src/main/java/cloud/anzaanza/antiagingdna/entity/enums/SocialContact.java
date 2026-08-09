package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 당일 사람 만남 — 일지. 사회 영역 당일 점수의 <b>유일한</b> 소스({@code 일지_사회 = 사람만남}).
 *
 * <p>근거: Holt-Lunstad et al., 2015. 방향: 정. 앵커는 기획 §8.
 *
 * <p>Phase 2 — 목업 미반영. {@link SocialContactLevel} 과 함께 빠지면 사회 영역이 통째로
 * 산출 불가가 된다.
 */
@Getter
public enum SocialContact implements ScoredOption {

    /** 거의 안 만남 */
    RARELY(30),

    /** 잠깐 */
    BRIEF(60),

    /** 여러 번 · 길게 */
    FREQUENT(100);

    private final int score;

    SocialContact(int score) {
        this.score = score;
    }
}
