package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.DrinkFrequency;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseLevel;
import cloud.anzaanza.antiagingdna.entity.enums.LifeRhythm;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import cloud.anzaanza.antiagingdna.entity.enums.SleepType;
import cloud.anzaanza.antiagingdna.entity.enums.SmokingStatus;
import cloud.anzaanza.antiagingdna.entity.enums.SocialContactLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 회원가입 STEP 3 — 초기 진단(목업 {@code 02_INITIAL_DIAGNOSIS}) 응답.
 *
 * <p>{@link DnaInfo} 의 원본 답변 컬럼과 1:1 이다. 점수는 여기서 계산하지 않는다 —
 * 파생값을 원본과 같이 저장하지 않는 것이 {@code dna_info} 의 설계 전제다.
 *
 * <p>Q11(사회 교류)·Q12(WHO-5) 는 목업에 없어 {@code null} 을 허용한다. 화면이 확정되면
 * 엔티티의 {@code nullable} 과 함께 조인다.
 *
 * <p>체크박스 항목이 {@code boolean} 이 아니라 {@code Boolean} + {@code @NotNull} 인 이유 —
 * 원시 타입이면 필드가 빠졌을 때 Jackson 3 이 역직렬화 단계에서 먼저 터진다
 * ({@code FAIL_ON_NULL_FOR_PRIMITIVES} 가 기본 활성, Jackson 2 와 반대다). 그러면 어느
 * 항목이 문제인지 알려주지 못하는 "Failed to read request" 만 남는다. 래퍼로 받으면 검증
 * 단계까지 도달해 항목별 오류를 돌려줄 수 있고, "체크 안 함"과 "답 안 함"도 구분된다.
 */
public record DiagnosisRequest(
        @NotNull SleepType sleepType,
        @NotNull Boolean sleepDaytimeDrowsy,
        @NotNull Boolean sleepOnsetDelayed,
        @NotNull Boolean sleepNightAwakening,
        @NotNull Boolean sleepUnrefreshed,
        @NotNull SensitivityLevel sugarSensitivity,
        @NotNull SensitivityLevel caffeineSensitivity,
        @NotNull SensitivityLevel stressSensitivity,
        @NotNull ExerciseLevel exerciseLevel,
        @NotNull Boolean shiftWorker,
        @NotNull Boolean frequentTraveler,
        @NotNull DrinkFrequency drinkFrequency,
        @NotNull SmokingStatus smokingStatus,
        @NotNull LifeRhythm lifeRhythm,
        SocialContactLevel socialContactLevel,
        @Min(0) @Max(5) Integer who5Q1,
        @Min(0) @Max(5) Integer who5Q2,
        @Min(0) @Max(5) Integer who5Q3,
        @Min(0) @Max(5) Integer who5Q4,
        @Min(0) @Max(5) Integer who5Q5) {

    public DnaInfo toEntity(User user) {
        return DnaInfo.builder()
                .user(user)
                .sleepType(sleepType)
                .sleepDaytimeDrowsy(sleepDaytimeDrowsy)
                .sleepOnsetDelayed(sleepOnsetDelayed)
                .sleepNightAwakening(sleepNightAwakening)
                .sleepUnrefreshed(sleepUnrefreshed)
                .sugarSensitivity(sugarSensitivity)
                .caffeineSensitivity(caffeineSensitivity)
                .stressSensitivity(stressSensitivity)
                .exerciseLevel(exerciseLevel)
                .shiftWorker(shiftWorker)
                .frequentTraveler(frequentTraveler)
                .drinkFrequency(drinkFrequency)
                .smokingStatus(smokingStatus)
                .lifeRhythm(lifeRhythm)
                .socialContactLevel(socialContactLevel)
                .who5Q1(who5Q1)
                .who5Q2(who5Q2)
                .who5Q3(who5Q3)
                .who5Q4(who5Q4)
                .who5Q5(who5Q5)
                .build();
    }
}
