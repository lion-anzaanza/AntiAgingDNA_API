-- 로그인 식별자를 이메일에서 아이디로 전환
--
-- 배경: FE backend-backlog.md #2/#18 — 2026-08-16 기획 결정 "로그인은 아이디로 한다".
-- 이메일은 계정에 남긴다. 비밀번호 찾기 등 복구 수단으로 필요하고(백로그 #14),
-- 그러면 아이디를 새로 추가하고 이메일은 유지하는 안(백로그 #18 의 B안)이 사실상 강제된다.
--
-- not null 로 바로 걸지 않는 이유: 기존 행에는 소급해 채울 아이디 값이 없다. 신규 가입은
-- 애플리케이션 레이어(SignUpRequest @NotBlank)가 필수로 강제한다. 기존 계정을 백필한 뒤
-- not null 로 조이는 것은 별도 마이그레이션 과제다.
alter table user add column login_id varchar(32) comment '로그인 식별자 (아이디). 신규 가입 필수, 기존 행은 백필 전까지 null 허용';

-- unique 인덱스에서 MySQL 은 NULL 을 서로 다른 값으로 취급해 여러 개를 허용한다 —
-- 백필 전 기존 행이 전부 null 이어도 이 제약을 먼저 걸 수 있다.
alter table user add constraint uk_user_login_id unique (login_id);
