-- 일지 날씨 자동 기록 — FE backend-backlog.md #12, 2026-08-17 결정
--
-- 위경도로 그때그때 조회하지 않고 일지 저장 시점에 조회해서 저장한다. 나중에 다시 봐도
-- 그날 기록된 날씨가 그대로 보여야 하기 때문이다(캘린더·상세보기). 위경도 자체는 저장하지
-- 않는다 — 개인위치정보라 서버가 들고 있을 이유가 없고, 날씨 값만 있으면 화면에는 충분하다.
alter table diary add column weather_temperature decimal(4, 1) comment '기온(섭씨). 위경도 미제공·조회 실패 시 null';
alter table diary add column weather_humidity int comment '습도(%)';
alter table diary add column weather_condition varchar(32) comment '하늘·강수 상태 (WeatherCondition enum)';
alter table diary add column weather_location_label varchar(64) comment '클라이언트가 보낸 위치 표기(예: 서울). 서버가 좌표로 지어내지 않는다';
