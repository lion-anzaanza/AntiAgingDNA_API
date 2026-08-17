package cloud.anzaanza.antiagingdna.service.weather;

/** 기상청 격자 좌표(5km 해상도). {@link WeatherGridConverter} 의 결과값. */
public record GridCoordinate(int nx, int ny) {}
