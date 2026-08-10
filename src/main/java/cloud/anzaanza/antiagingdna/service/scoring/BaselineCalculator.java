package cloud.anzaanza.antiagingdna.service.scoring;

import static cloud.anzaanza.antiagingdna.service.scoring.ItemScores.mean;
import static cloud.anzaanza.antiagingdna.service.scoring.ItemScores.anchor;

import cloud.anzaanza.antiagingdna.entity.DnaInfo;

/**
 * 초기 진단 → 영역 baseline (기획 [종합점수 산출 수식] §A-3).
 *
 * <pre>
 * baseline_신체 = avg(수면질, 운동, 음주, 흡연)
 * baseline_정신 = avg(수면질, 생활리듬, 사회교류, 기분·활력)
 * baseline_감정 = avg(생활리듬, 기분·활력)
 * baseline_사회 = 사회교류
 * </pre>
 *
 * <p><b>환경은 산출하지 않는다.</b> 기획 §A-3 은 {@code baseline_환경 = 50}(중립 상수)이지만
 * 온보딩·일지 어디에도 환경 입력이 없어 모든 사용자에게 같은 값이 붙는다. Phase 1 가중치가
 * 0 이라 종합점수에 기여하지도 않으므로, 상수를 저장해 있는 정보인 척하지 않는다.
 *
 * <p><b>Phase 2 결측</b> — 사회교류(Q11)와 WHO-5(Q12)는 목업 {@code 02_INITIAL_DIAGNOSIS} 에
 * 없어 비어 있을 수 있다. 그러면 정신은 2소스로 줄고, 감정은 생활리듬 하나, 사회는 결측이 된다.
 * 0 으로 채우지 않는다 — 답하지 않은 것을 최악으로 채점하는 셈이 된다.
 */
public final class BaselineCalculator {

    private BaselineCalculator() {}

    public static AreaScores of(DnaInfo dna) {
        Double sleepQuality = ItemScores.sleepSelfCheck(
                dna.isSleepDaytimeDrowsy(),
                dna.isSleepOnsetDelayed(),
                dna.isSleepNightAwakening(),
                dna.isSleepUnrefreshed());
        Double exercise = anchor(dna.getExerciseLevel());
        Double drinking = anchor(dna.getDrinkFrequency());
        Double smoking = anchor(dna.getSmokingStatus());
        Double lifeRhythm = anchor(dna.getLifeRhythm());
        Double socialContact = anchor(dna.getSocialContactLevel());
        Double wellbeing = ItemScores.who5(
                dna.getWho5Q1(), dna.getWho5Q2(), dna.getWho5Q3(), dna.getWho5Q4(), dna.getWho5Q5());

        return AreaScores.builder()
                .put(Area.PHYSICAL, mean(sleepQuality, exercise, drinking, smoking))
                .put(Area.MENTAL, mean(sleepQuality, lifeRhythm, socialContact, wellbeing))
                .put(Area.EMOTION, mean(lifeRhythm, wellbeing))
                .put(Area.SOCIAL, socialContact)
                .build();
    }
}
