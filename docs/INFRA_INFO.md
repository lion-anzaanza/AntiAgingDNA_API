# Infrastructure Info

## Server

| 항목 | 값 |
|---|---|
| Host | 54.210.141.169 |
| OS | Amazon Linux 2023 |
| SSH | port 22, user `ec2-user`, key `antiaging-dna.pem` |

## Docker Containers

| Name | Image | Port |
|---|---|---|
| antiagingdna | jiseong02/antiagingdna:latest | 8080 |
| mysql | mysql:8.0 | 3306 |

- Docker network: `antiaging-net` (두 컨테이너 연결)
- Spring Boot → MySQL 접속: `jdbc:mysql://mysql:3306/antiagingdna`

## NGINX

- 리버스 프록시: 80/443 → localhost:8080
- 설정 파일: `/etc/nginx/conf.d/antiagingdna.conf`

## SSL

- 발급: Let's Encrypt (certbot)
- 자동 갱신: `certbot-renew.timer` 활성화

## DNS

- 도메인: `antiaging-dna.anzaanza.cloud`
- 관리: 가비아 (A 레코드 → 54.210.141.169)

## CI/CD

- 트리거: `main` 브랜치 push
- 파이프라인: GitHub Actions → Docker Hub push → EC2 SSH deploy
- 워크플로우: `.github/workflows/deploy.yml`

## GitHub Secrets

| Secret | 용도 |
|---|---|
| DOCKERHUB_USERNAME | Docker Hub 사용자명 |
| DOCKERHUB_TOKEN | Docker Hub Access Token |
| EC2_SSH_KEY | EC2 SSH 프라이빗 키 |
| DB_URL | MySQL 접속 URL |
| DB_USERNAME | MySQL 사용자명 |
| DB_PASSWORD | MySQL 비밀번호 |
