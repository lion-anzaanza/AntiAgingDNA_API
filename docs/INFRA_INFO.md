# Infrastructure Info

> 최종 실측: **2026-08-10**. 아래 "검증" 항목은 그날 실제로 확인한 방법이다 —
> 값이 의심되면 추측하지 말고 같은 명령으로 다시 확인할 것.
>
> **이 파일은 공개 저장소에 커밋된다. 비밀번호·키를 절대 넣지 말 것.**
> 자격증명은 GitHub Secrets 와 로컬 전용 파일(`.gitignore` 처리)에만 둔다.

## Server

| 항목 | 값 |
|---|---|
| Host | 54.210.141.169 |
| OS | Amazon Linux 2023 |
| SSH | port 22, user `ec2-user`, key `antiaging-dna.pem` |

**검증** — 도메인이 이 IP 로 해석되고(`nslookup antiaging-dna.anzaanza.cloud`), 22/80/443 이
열려 있으며, `deploy.yml` 이 이 호스트로 SSH 해 배포에 성공한다.

> **구 서버 주의** — `18.207.116.75` / SSH 포트 `20022` 는 **폐기된 이전 서버**다.
> 커밋 `1d97ee7 chore: migrate to new EC2 server` 에서 이전했고, 현재 그 IP 는 전 포트가
> 무응답이다. 로컬 자격증명 파일에 옛 정보가 남아 있을 수 있으니 이 표를 기준으로 삼을 것.

> **Elastic IP 미사용** — 인스턴스를 중지/시작하면 퍼블릭 IP 가 바뀐다. 실제로 한 번 바뀌었다.
> IP 가 바뀌면 이 문서 · `deploy.yml` · 가비아 A 레코드를 함께 고쳐야 한다.

## Docker Containers

| Name | Image | Port |
|---|---|---|
| antiagingdna | jiseong02/antiagingdna:latest | 8080 |
| mysql | mysql:8.0 | 3306 |

- MySQL 실제 버전: **8.0.46** (`mysql:8.0` 태그가 가리키는 것). 이 값은
  `SchemaGenerationTest.PROD_MYSQL_VERSION` 에 들어 있다 — 애플리케이션은 방언을
  커넥션에서 자동 감지하는데, DB 없는 테스트가 같은 방언을 재현하려면 버전이 필요하다.
  서버 MySQL 을 올리면 그 상수도 같이 올릴 것.
- Docker network: `antiaging-net` (두 컨테이너 연결)
- Spring Boot → MySQL 접속: `jdbc:mysql://mysql:3306/antiagingdna`
- MySQL 3306 은 **외부에 노출되지 않는다** — 컨테이너 네트워크 내부 전용이라
  DB 작업은 SSH 로 들어가 `docker exec` 하는 경로뿐이다.

## Database Schema

- **Flyway 가 스키마의 단일 소스다** — `src/main/resources/db/migration/`.
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate 는 스키마를 만들지도 고치지도 않고,
  엔티티 매핑과 실제 테이블이 어긋나면 **부팅을 거부한다**.
- ⚠️ 이전에는 `ddl-auto=update` 였다. 그때 만들어진 배포 DB 에는 폐기 컬럼(`user.login_id`
  등)이 남아 있어 Flyway 의 `V1__init.sql` 을 적용할 수 없다
  (`Found non-empty schema(s) without schema history table`).
  **최초 1회 스키마 초기화가 필요하다**:

```bash
ssh -i <키> ec2-user@54.210.141.169

docker stop antiagingdna                      # 구 이미지가 스키마를 되살리지 못하게 먼저 정지
docker exec mysql mysqldump -u root -p --databases antiagingdna \
  > ~/antiagingdna-backup-$(date +%Y%m%d-%H%M).sql
docker exec -it mysql mysql -u root -p -e "
  DROP DATABASE antiagingdna;
  CREATE DATABASE antiagingdna DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
# 이어서 main 에 push → 새 이미지 배포 → Flyway 가 V1 적용
docker logs -f antiagingdna | grep -iE "flyway|migrat"
```

  순서를 지키지 않으면(구 컨테이너가 살아 있는 채로 drop) `--restart unless-stopped` 때문에
  크래시 루프에 빠진다.

## NGINX

- 리버스 프록시: 80/443 → localhost:8080
- 설정 파일: `/etc/nginx/conf.d/antiagingdna.conf`

## SSL

- 발급: Let's Encrypt (certbot)
- 자동 갱신: `certbot-renew.timer` 활성화
- **검증** — 현재 인증서 `CN=antiaging-dna.anzaanza.cloud`, 2026-08-06 발급 / **2026-11-04 만료**.
  갱신 타이머가 도는지 만료 전에 한 번 확인할 것.

## DNS

- 도메인: `antiaging-dna.anzaanza.cloud`
- 관리: 가비아 (A 레코드 → 54.210.141.169)

## Health Check

```bash
curl https://antiaging-dna.anzaanza.cloud/health     # → {"status":"ok"}
```

**검증** — 2026-08-10 기준 정상 응답. 루트(`/`)는 404 가 정상이다(매핑 없음).

## CI/CD

- 트리거: `main` 브랜치 push (**현재 브랜치 보호 없음 — push 가 곧 배포다**)
- 파이프라인: GitHub Actions → Docker Hub push → EC2 SSH deploy
- 워크플로우: `.github/workflows/deploy.yml`
- 이미지 태그: `latest` 와 커밋 SHA 두 개. 롤백은 SHA 태그로 `docker run` 하면 된다.
- 멀티아키(amd64/arm64) 빌드라 QEMU 에뮬레이션 탓에 **1회 배포에 8~10분** 걸린다.

## Credentials

| 위치 | 내용 |
|---|---|
| GitHub Secrets | `DOCKERHUB_USERNAME` · `DOCKERHUB_TOKEN` · `EC2_SSH_KEY` · `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` · `JWT_SECRET` · `WEATHER_API_KEY` |
| 로컬 전용 파일 | SSH/DB 접속 정보. `.gitignore` 의 `*DO_NOT_CUMMIT*` · `*SSH_ONLY*` · `*.pem` · `*.key` 패턴으로 차단 |

> **`JWT_SECRET`** — 액세스 토큰 서명 키(HS256). **32바이트 이상**이어야 하고 없으면 앱이
> 부팅에 실패한다(`jwt.secret=${JWT_SECRET}`, 기본값 없음). 값을 바꾸면 이미 발급된 토큰이
> 전부 무효가 되어 사용자가 전원 로그아웃된다.
>
> ```bash
> gh secret set JWT_SECRET --body "$(openssl rand -base64 48)"
> ```

> **`WEATHER_API_KEY`** — 공공데이터포털 "기상청_단기예보 조회서비스" 인증키(일지 날씨
> 자동 기록, FE backend-backlog.md #12). `JWT_SECRET` 과 달리 **없어도 부팅은 정상**이다 —
> 날씨는 부가 정보라 키가 없으면 `WeatherService` 가 결측으로 처리할 뿐이다
> (`weather.api-key=${WEATHER_API_KEY:}`, 기본값 빈 문자열). data.go.kr 이 발급하는
> "URL Encoding" 형태 키를 그대로 넣으면 된다 — `WeatherProperties.decodedApiKey()` 가
> 한 번 디코딩한 뒤 HTTP 클라이언트가 다시 인코딩하므로 이중 인코딩 걱정 없다.
>
> ```bash
> gh secret set WEATHER_API_KEY --body "<data.go.kr 에서 발급받은 인증키>"
> ```

> **주의** — `.gitignore` 가 한때 특정 **파일명 하나**(`docs/SSH_ONLY_FOR_LOCAL.txt`)만
> 막고 있었다. 그 파일이 다른 이름으로 바뀌자 자격증명이 무방비 상태가 됐고, 이 저장소는
> **public** 이다. 파일명이 아니라 **패턴**으로 막을 것.
