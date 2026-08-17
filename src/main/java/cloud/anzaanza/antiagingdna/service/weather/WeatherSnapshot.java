package cloud.anzaanza.antiagingdna.service.weather;

import java.math.BigDecimal;

/** 조회 시점의 날씨 한 장 — 일지에 그대로 저장되어 이후에도 값이 바뀌지 않는다. */
public record WeatherSnapshot(BigDecimal temperature, Integer humidity, WeatherCondition condition) {}
