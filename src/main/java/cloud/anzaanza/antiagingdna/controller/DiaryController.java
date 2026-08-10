package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.dto.DiaryRequest;
import cloud.anzaanza.antiagingdna.dto.DiaryResponse;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 오늘의 일지 — <b>초안</b>. 담당자가 정해지면 API 형태는 바뀔 수 있다.
 *
 * <p>경로에 사용자 식별자가 없다. 남의 일지를 지정할 수 있는 URL 자체를 만들지 않는다 —
 * 토큰의 {@code sub} 가 곧 작성자다.
 */
@RestController
@RequestMapping("/api/diaries")
@SecurityRequirement(name = "bearerAuth")
public class DiaryController {

    /** 구간 조회 상한 — {@code ScoreController} 와 같은 값 */
    private static final int MAX_RANGE_DAYS = 366;

    private final DiaryService diaryService;
    private final Clock clock;

    public DiaryController(DiaryService diaryService, Clock clock) {
        this.diaryService = diaryService;
        this.clock = clock;
    }

    /**
     * 작성·수정 공용. 같은 날짜에 다시 보내면 통째로 교체된다(멱등).
     *
     * <p>{@code POST} 가 아닌 이유는 하루 1건 제약 때문이다. 자원의 주소가
     * {@code /api/diaries/{date}} 로 이미 정해져 있으므로 {@code PUT} 이 맞다.
     */
    @PutMapping("/{date}")
    public DiaryResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody DiaryRequest request) {

        rejectFuture(date);
        return DiaryResponse.from(diaryService.save(jwt.getSubject(), date, request));
    }

    @GetMapping("/{date}")
    public DiaryResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return DiaryResponse.from(diaryService.get(jwt.getSubject(), date));
    }

    @GetMapping
    public List<DiaryResponse> between(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from 이 to 보다 늦습니다");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "한 번에 조회할 수 있는 구간은 " + MAX_RANGE_DAYS + "일까지입니다");
        }
        return diaryService.between(jwt.getSubject(), from, to).stream()
                .map(DiaryResponse::from)
                .toList();
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        diaryService.delete(jwt.getSubject(), date);
    }

    /** 일지는 EMA(당일 기록)다 — 미래를 미리 적는 것은 이 설계의 전제를 깬다 (기획 일지 §1②) */
    private void rejectFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "미래 날짜의 일지는 쓸 수 없습니다");
        }
    }
}
