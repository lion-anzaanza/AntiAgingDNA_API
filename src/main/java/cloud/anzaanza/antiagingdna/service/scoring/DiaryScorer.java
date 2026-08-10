package cloud.anzaanza.antiagingdna.service.scoring;

import static cloud.anzaanza.antiagingdna.service.scoring.ItemScores.applySensitivity;
import static cloud.anzaanza.antiagingdna.service.scoring.ItemScores.mean;
import static cloud.anzaanza.antiagingdna.service.scoring.ItemScores.anchor;

import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;

/**
 * 하루치 일지 → 당일 영역 점수 (기획 [종합점수 산출 수식] §B-2).
 *
 * <pre>
 * 일지_신체 = avg(수면시간, 잠들기까지, 수면만족도, 패스트푸드×k, 카페인×k, 물, 운동, 좌식)
 * 일지_정신 = avg(수면시간, 잠들기까지, 수면만족도, 카페인×k, 스트레스×k, 사람만남)
 * 일지_감정 = avg(스트레스×k, 기분 회복 활동)
 * 일지_사회 = 사람만남
 * </pre>
 *
 * <p>민감도 계수는 초기 진단에서 온다 — 같은 행동이라도 사람에 따라 감점 폭이 달라지는 것이
 * 이 서비스의 개인화 축이다 (기획 §3).
 *
 * <p>컨디션({@code condition_level})은 넣지 않는다. 점수가 예측해야 할 결과값이라 합산에
 * 넣으면 순환논리가 된다 (기획 일지 §1①).
 */
public final class DiaryScorer {

    private DiaryScorer() {}

    public static AreaScores of(Diary diary, DnaInfo dna) {
        Double sleepDuration = ItemScores.sleepDuration(diary.getSleepStartedAt(), diary.getSleepEndedAt());
        Double sleepLatency = anchor(diary.getSleepLatency());
        Double sleepSatisfaction = ItemScores.fivePointScale(diary.getSleepSatisfaction());

        Double sugar = applySensitivity(anchor(diary.getSugarIntake()), dna.getSugarSensitivity());
        Double caffeine = applySensitivity(caffeineRaw(diary), dna.getCaffeineSensitivity());
        Double water = anchor(diary.getWaterIntake());

        Double exercise = exercise(diary);
        Double sitting = anchor(diary.getSittingHours());

        Double stress = applySensitivity(ItemScores.stress(diary.getStressLevel()), dna.getStressSensitivity());
        Double socialContact = anchor(diary.getSocialContact());

        return AreaScores.builder()
                .put(Area.PHYSICAL, mean(
                        sleepDuration, sleepLatency, sleepSatisfaction,
                        sugar, caffeine, water, exercise, sitting))
                .put(Area.MENTAL, mean(
                        sleepDuration, sleepLatency, sleepSatisfaction,
                        caffeine, stress, socialContact))
                // 기획 §B-2 의 감정은 avg(스트레스, 기분 회복 활동)이지만 '기분 회복 활동'의
                // 앵커가 기획 §8 표에 없다(워크드 예시의 '잠깐=60' 하나뿐). 지어내지 않고
                // 스트레스 단일 소스로 둔다 — MoodRecovery 가 ScoredOption 이 아닌 이유다.
                .put(Area.EMOTION, mean(stress))
                .put(Area.SOCIAL, socialContact)
                .build();
    }

    /**
     * 카페인은 잔 수와 마지막 섭취 시각을 <b>먼저 평균낸 뒤</b> 계수를 곱한다.
     *
     * <p>두 컬럼이지만 기획 §B-2 에서는 {@code 카페인×k} 항목 하나다. 워크드 예시(§B-3)의
     * "잔 1~2·오후 → k1.1 → 78" 이 이 순서에서만 나온다: avg(80, 80)=80 → 100−1.1×20=78.
     * 각각에 계수를 곱해 평균내면 카페인 항목이 다른 항목의 두 배 무게를 갖게 된다.
     */
    private static Double caffeineRaw(Diary diary) {
        return mean(anchor(diary.getCaffeineCups()), anchor(diary.getCaffeineLastTime()));
    }

    /**
     * 운동은 "안 함"이 결측이 아니라 <b>0점</b>이다 (기획 §8 "안함=0").
     *
     * <p>했다고 했는데 시간이 없으면 채점할 수 없으므로 결측으로 둔다.
     */
    private static Double exercise(Diary diary) {
        if (diary.getExercised() == null) {
            return null;
        }
        return diary.getExercised() ? anchor(diary.getExerciseDuration()) : 0.0;
    }
}
