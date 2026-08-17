package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.exception.DiaryNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import cloud.anzaanza.antiagingdna.service.weather.WeatherService;
import cloud.anzaanza.antiagingdna.service.weather.WeatherSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 일지 — 작성·조회·삭제.
 *
 * <p><b>초안이다.</b> 일지 도메인은 다른 담당자의 몫이고, 이 구현은 채점 파이프라인이 실제
 * 일지 데이터로 동작하는지 검증하기 위한 참조 구현이다. API 형태는 확정된 것이 아니다.
 *
 * <p>쓰기 경로마다 {@link ScoringService#recalculateFrom(String, LocalDate)} 를 <b>같은
 * 트랜잭션에서</b> 호출한다. 일지와 점수가 따로 커밋되면 화면에 어제 점수가 남는다.
 * {@code recalculate} 가 아니라 {@code recalculateFrom} 인 이유는, 과거 날짜 일지를 고치면
 * 그 뒤 날짜들의 이동평균도 함께 바뀌기 때문이다.
 */
@Service
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final WeatherService weatherService;

    public DiaryService(
            DiaryRepository diaryRepository,
            UserRepository userRepository,
            ScoringService scoringService,
            WeatherService weatherService) {
        this.diaryRepository = diaryRepository;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
        this.weatherService = weatherService;
    }

    /**
     * 그 날짜의 일지를 저장한다. 이미 있으면 <b>통째로 교체</b>한다.
     *
     * <p>작성과 수정을 나누지 않는 이유 — 하루 1건 제약({@code uk_diary_author_log_date})이
     * 이미 있고, 사용자는 하루 종일 같은 일지를 고쳐 쓴다. 나누면 클라이언트가 먼저 존재 여부를
     * 조회해야 하고, 그 사이에 눌린 두 번째 저장이 409 로 튕긴다.
     */
    @Transactional
    public Diary save(String userId, LocalDate date, DiaryRequest request) {
        User author = userRepository
                .findById(userId)
                .orElseThrow(() -> new BadCredentialsException("존재하지 않는 계정입니다"));

        Optional<Diary> existing = diaryRepository.findByAuthorIdAndLogDate(userId, date);

        // 날씨는 하루 한 번만 기록한다 — 이미 있으면 그 뒤의 수정이 지우거나 다시 조회하지
        // 않는다(V3 마이그레이션 의도: 그날 기록된 날씨는 나중에 다시 봐도 그대로다). 위경도가
        // 없거나 조회에 실패하면 기존 값을 그대로 둔다 — "이번에 없으면 지운다"는 폼 필드
        // 규칙(replaceWith)과 달리, 날씨는 한 번 붙으면 그 날의 기록으로 고정된다.
        boolean alreadyRecorded = existing.map(Diary::hasWeather).orElse(false);
        WeatherSnapshot weather = (!alreadyRecorded && request.lat() != null && request.lon() != null)
                ? weatherService.lookup(request.lat(), request.lon())
                : null;

        Diary saved = existing
                .map(diary -> {
                    diary.replaceWith(request.toEntity(author, date));
                    if (weather != null) {
                        diary.recordWeather(weather, request.weatherLocationLabel());
                    }
                    return diary; // 영속 상태라 변경 감지로 UPDATE 된다
                })
                .orElseGet(() -> {
                    Diary diary = request.toEntity(author, date);
                    if (weather != null) {
                        diary.recordWeather(weather, request.weatherLocationLabel());
                    }
                    return diaryRepository.save(diary);
                });

        // 재채점은 일지를 다시 조회한다. 같은 트랜잭션이고 JPA 가 쿼리 전에 flush 하므로
        // 방금 쓴 내용이 반영된 상태로 채점된다.
        scoringService.recalculateFrom(userId, date);
        return saved;
    }

    @Transactional(readOnly = true)
    public Diary get(String userId, LocalDate date) {
        return diaryRepository
                .findByAuthorIdAndLogDate(userId, date)
                .orElseThrow(() -> new DiaryNotFoundException(date));
    }

    @Transactional(readOnly = true)
    public List<Diary> between(String userId, LocalDate from, LocalDate to) {
        return diaryRepository.findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(userId, from, to);
    }

    /** 삭제해도 그날의 점수 행은 남는다 — 일지가 없는 날의 표시 점수는 baseline 으로 산출된다 */
    @Transactional
    public void delete(String userId, LocalDate date) {
        Diary diary = diaryRepository
                .findByAuthorIdAndLogDate(userId, date)
                .orElseThrow(() -> new DiaryNotFoundException(date));

        diaryRepository.delete(diary);
        diaryRepository.flush(); // 아래 재채점이 삭제 전 일지를 읽지 않도록
        scoringService.recalculateFrom(userId, date);
    }
}
