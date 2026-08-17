package cloud.anzaanza.antiagingdna.service.weather;

import java.util.List;

/** 기상청 단기예보 조회서비스 응답 봉투 — 활용가이드 §1 응답 메시지 명세 그대로. */
record KmaResponse(Envelope response) {

    record Envelope(Header header, Body body) {}

    record Header(String resultCode, String resultMsg) {}

    record Body(Items items) {}

    record Items(List<Item> item) {}

    /**
     * {@code obsrValue} 는 초단기실황(getUltraSrtNcst), {@code fcstValue} 는 초단기예보
     * (getUltraSrtFcst) 응답에만 채워진다 — 두 오퍼레이션이 응답 필드 이름만 다르고 구조가
     * 같아서 하나의 레코드로 받는다.
     */
    record Item(
            String baseDate,
            String baseTime,
            String category,
            Integer nx,
            Integer ny,
            String obsrValue,
            String fcstDate,
            String fcstTime,
            String fcstValue) {}
}
