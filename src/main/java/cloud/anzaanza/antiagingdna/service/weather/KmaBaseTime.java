package cloud.anzaanza.antiagingdna.service.weather;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 조회 시점 기준으로 유효한 {@code base_date}/{@code base_time} 을 고른다.
 *
 * <p>기상청 활용가이드 §2 "예보 발표시각" 표 그대로다 — 초단기실황은 매 정시 생성되어 10분 후
 * 조회 가능, 초단기예보는 매시 30분에 생성되어 45분 후 조회 가능하다. 그 전 시각을 넣으면
 * 아직 안 만들어진 값을 요청하는 것이라 결과가 비거나 직전 값이 온다.
 */
public record KmaBaseTime(String baseDate, String baseTime) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 초단기실황(getUltraSrtNcst) — 정시 발표, {@code HH00} */
    public static KmaBaseTime ultraShortTermObservation(LocalDateTime now) {
        LocalDateTime base = now.getMinute() < 10 ? now.minusHours(1) : now;
        return new KmaBaseTime(base.format(DATE_FORMAT), "%02d00".formatted(base.getHour()));
    }

    /** 초단기예보(getUltraSrtFcst) — 매시 30분 발표, {@code HH30} */
    public static KmaBaseTime ultraShortTermForecast(LocalDateTime now) {
        LocalDateTime base = now.getMinute() < 45 ? now.minusHours(1) : now;
        return new KmaBaseTime(base.format(DATE_FORMAT), "%02d30".formatted(base.getHour()));
    }
}
