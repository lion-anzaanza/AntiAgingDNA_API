package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 0~100 정규화 앵커를 가진 선택지 enum.
 *
 * <p>기획 [종합점수 산출 수식] §8 "항목별 정규화 기준표" 와 §A-1 "문항 정규화" 의 이산
 * 매핑값을 각 상수가 직접 들고 있다. 채점 로직이 {@code switch} 로 선택지를 나열하지 않고
 * {@link #getScore()} 만 호출하면 되므로, 선택지가 추가돼도 채점 코드는 건드릴 필요가 없다.
 *
 * <p><b>주의</b> — 앵커 수치는 기획상 전부 "초안"이며 Phase 2 에서 컨디션(결과) 예측
 * 기여도로 재보정될 예정이다. 재보정 시 이 상수들을 고치면 과거 점수와 의미가 달라지므로,
 * 반드시 {@code daily_score.scoring_version} 을 함께 올려 이력을 분리해야 한다.
 */
public interface ScoredOption {

    /** 0~100 정규화 점수. 방향(정/역/U자/하한형)은 이미 반영된 값이다. */
    int getScore();
}
