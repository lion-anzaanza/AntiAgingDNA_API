package cloud.anzaanza.antiagingdna.service.weather;

/** 기상청 API 호출 실패 — {@link WeatherService} 가 잡아서 결측으로 처리한다. */
class WeatherLookupException extends RuntimeException {

    WeatherLookupException(String message) {
        super(message);
    }
}
