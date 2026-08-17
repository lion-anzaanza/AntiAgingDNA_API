package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.dto.DailyScoreResponse;
import cloud.anzaanza.antiagingdna.dto.ItemTrendResponse;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import cloud.anzaanza.antiagingdna.service.ScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 종합점수 조회. */
@RestController
@RequestMapping("/api/scores")
@SecurityRequirement(name = "bearerAuth")
public class ScoreController {

    /** 구간 조회 상한 — 한 번에 1년치까지 */
    private static final int MAX_RANGE_DAYS = 366;

    private final ScoringService scoringService;
    private final DiaryService diaryService;
    private final ScoringProperties properties;
    private final Clock clock;

    public ScoreController(
            ScoringService scoringService, DiaryService diaryService, ScoringProperties properties, Clock clock) {
        this.scoringService = scoringService;
        this.diaryService = diaryService;
        this.properties = properties;
        this.clock = clock;
    }

    /** 오늘의 종합점수. 일지를 쓰지 않았어도 baseline 으로 산출된다 */
    @Operation(summary = "오늘의 종합점수", description = "일지를 쓰지 않은 날도 baseline 으로 산출된다(null 없음).")
    @GetMapping("/today")
    public DailyScoreResponse today(@AuthenticationPrincipal Jwt jwt) {
        return DailyScoreResponse.from(
                scoringService.scoreOn(jwt.getSubject(), LocalDate.now(clock)), properties.grade());
    }

    @Operation(summary = "특정 날짜 종합점수", description = "미래 날짜는 400.")
    @GetMapping("/{date}")
    public DailyScoreResponse on(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date.isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "미래 날짜의 점수는 없습니다");
        }
        return DailyScoreResponse.from(scoringService.scoreOn(jwt.getSubject(), date), properties.grade());
    }

    /** 추이 그래프용 구간 조회. 기록이 없는 날은 채우지 않는다 */
    @Operation(
            summary = "종합점수 구간 조회",
            description = "추이 그래프용. 기록이 없는 날은 배열에서 채우지 않는다. 최대 366일.")
    @GetMapping
    public List<DailyScoreResponse> between(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        validateRange(from, to);
        return scoringService.scoresBetween(jwt.getSubject(), from, to).stream()
                .map(score -> DailyScoreResponse.from(score, properties.grade()))
                .toList();
    }

    /** 홈 "나의 LifeDNA 정보" 주간 추이 카드용 — 수면·수분 항목별 원자값(FE backend-backlog #11/#27) */
    @Operation(
            summary = "수면·수분 항목별 구간 조회",
            description = "홈 주간 추이 카드용 원자값(막대·진행바·등급) — 문장은 프론트가 만든다"
                    + "(PLANNING_OPEN_ITEMS.md B-5 결정). 기록이 없는 날은 배열에서 채우지 않는다. 최대 366일.")
    @GetMapping("/items")
    public List<ItemTrendResponse> itemTrend(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        validateRange(from, to);
        return diaryService.between(jwt.getSubject(), from, to).stream()
                .map(diary -> ItemTrendResponse.from(diary, properties.grade()))
                .toList();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from 이 to 보다 늦습니다");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "한 번에 조회할 수 있는 구간은 " + MAX_RANGE_DAYS + "일까지입니다");
        }
    }
}
