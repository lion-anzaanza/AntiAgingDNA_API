package cloud.anzaanza.antiagingdna.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseDuration;
import cloud.anzaanza.antiagingdna.entity.enums.ExerciseType;
import cloud.anzaanza.antiagingdna.entity.enums.SleepLatency;
import cloud.anzaanza.antiagingdna.entity.enums.WaterIntake;
import cloud.anzaanza.antiagingdna.exception.DiaryNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 저장 경로와 채점 재계산의 연결을 본다. 산식 자체는 {@code service/scoring} 테스트가 담당. */
@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final String USER_ID = "user-1";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);

    private static final User USER = User.builder()
            .id(USER_ID)
            .email("nosleep@gmail.com")
            .password("hash")
            .nickname("안자안자")
            .birthYear(2002)
            .build();

    @Mock private DiaryRepository diaryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScoringService scoringService;

    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        diaryService = new DiaryService(diaryRepository, userRepository, scoringService);
    }

    private void givenUser() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(USER));
    }

    // ── 저장 ─────────────────────────────────────────────────────

    @Test
    void 처음_쓰면_새_일지가_저장된다() {
        givenUser();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(diaryRepository.save(any(Diary.class))).willAnswer(call -> call.getArgument(0));

        Diary saved = diaryService.save(USER_ID, TODAY, request(6));

        assertThat(saved.getLogDate()).isEqualTo(TODAY);
        assertThat(saved.getStressLevel()).isEqualTo(6);
        verify(diaryRepository).save(any(Diary.class));
    }

    /** 하루 1건 제약이 있으므로 같은 날짜 재저장은 새 행이 아니라 교체다 */
    @Test
    void 같은_날짜에_다시_쓰면_기존_행을_교체한다() {
        givenUser();
        Diary existing = existingDiary();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.of(existing));

        Diary saved = diaryService.save(USER_ID, TODAY, request(9));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getId()).isEqualTo("diary-1");
        assertThat(saved.getStressLevel()).isEqualTo(9);
        verify(diaryRepository, never()).save(any(Diary.class)); // 영속 상태 → 변경 감지
    }

    // ── 채점 연결 ────────────────────────────────────────────────

    @Test
    void 저장하면_그_날짜의_점수가_다시_계산된다() {
        givenUser();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(diaryRepository.save(any(Diary.class))).willAnswer(call -> call.getArgument(0));

        diaryService.save(USER_ID, TODAY, request(6));

        verify(scoringService).recalculateFrom(USER_ID, TODAY);
    }

    /** 재채점이 일지를 다시 읽으므로 순서가 뒤집히면 직전 값으로 점수가 나온다 */
    @Test
    void 재계산은_일지_저장_뒤에_일어난다() {
        givenUser();
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());
        given(diaryRepository.save(any(Diary.class))).willAnswer(call -> call.getArgument(0));

        diaryService.save(USER_ID, TODAY, request(6));

        InOrder order = inOrder(diaryRepository, scoringService);
        order.verify(diaryRepository).save(any(Diary.class));
        order.verify(scoringService).recalculateFrom(USER_ID, TODAY);
    }

    @Test
    void 삭제하면_지운_뒤_점수가_다시_계산된다() {
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY))
                .willReturn(Optional.of(existingDiary()));

        diaryService.delete(USER_ID, TODAY);

        InOrder order = inOrder(diaryRepository, scoringService);
        order.verify(diaryRepository).delete(any(Diary.class));
        order.verify(diaryRepository).flush(); // 재채점이 삭제 전 일지를 읽지 않도록
        order.verify(scoringService).recalculateFrom(USER_ID, TODAY);
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    void 없는_날짜를_조회하면_404_로_떨어진다() {
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diaryService.get(USER_ID, TODAY))
                .isInstanceOf(DiaryNotFoundException.class);
    }

    @Test
    void 없는_날짜를_삭제하면_404_이고_재계산도_하지_않는다() {
        given(diaryRepository.findByAuthorIdAndLogDate(USER_ID, TODAY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> diaryService.delete(USER_ID, TODAY))
                .isInstanceOf(DiaryNotFoundException.class);

        verify(scoringService, never()).recalculateFrom(any(String.class), any());
    }

    // ── 입력 정규화 ──────────────────────────────────────────────

    /** 화면 토글을 껐을 때 이전 선택이 남아 함께 오는 상황 — 모순된 값을 저장하지 않는다 */
    @Test
    void 운동_안_함이면_시간과_종류는_버려진다() {
        DiaryRequest contradictory = new DiaryRequest(
                3, null, null, null, null, null, null, null, null,
                false, ExerciseDuration.ABOUT_60, ExerciseType.STRENGTH,
                null, null, null, null, null, null, null);

        assertThat(contradictory.exerciseDuration()).isNull();
        assertThat(contradictory.exerciseType()).isNull();
        assertThat(contradictory.exercised()).isFalse();
    }

    @Test
    void 운동_했으면_시간과_종류가_유지된다() {
        DiaryRequest exercised = new DiaryRequest(
                3, null, null, null, null, null, null, null, null,
                true, ExerciseDuration.ABOUT_60, ExerciseType.STRENGTH,
                null, null, null, null, null, null, null);

        assertThat(exercised.exerciseDuration()).isEqualTo(ExerciseDuration.ABOUT_60);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private static DiaryRequest request(int stressLevel) {
        return new DiaryRequest(
                3,
                LocalTime.of(23, 0),
                LocalTime.of(7, 0),
                SleepLatency.WITHIN_15,
                4,
                null,
                null,
                null,
                WaterIntake.SIX_TO_SEVEN,
                null,
                null,
                null,
                null,
                stressLevel,
                null,
                null,
                3,
                null,
                null);
    }

    private static Diary existingDiary() {
        return Diary.builder()
                .id("diary-1")
                .author(USER)
                .logDate(TODAY)
                .conditionLevel(1)
                .stressLevel(2)
                .build();
    }
}
