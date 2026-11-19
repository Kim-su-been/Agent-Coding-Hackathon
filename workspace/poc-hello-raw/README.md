# poc-hello-raw — React + Spring Boot + PostgreSQL 설치 번들 (hello world 전용)

설치파일(툴체인)과 hello world 예제만 있는 최소 번들이다. SoT(deepwiki)·에이전트 설정(AGENTS.md)·그 외 앱 코드는 없다.

## 구성

| 경로 | 내용 |
|---|---|
| `jdk/` | Eclipse Temurin 21.0.12.1+1 (LTS) |
| `maven/` | Maven 3.9.16 (오프라인 settings 자동 재기록) |
| `node/` | Node.js 24.22.2 + npm 11.19.1 |
| `pgsql/` | PostgreSQL 18.6 (포터블) |
| `m2/` | Maven 오프라인 저장소 |
| `app/backend/` | Spring Boot 4.1.1 hello — GET /api/hello |
| `app/frontend/` | React 19.3.1 + Vite 8.3.5 hello — node_modules 포함(오프라인용, 삭제 금지) |
| `pgdata/` | INSTALL.bat 실행 때 생성되는 데이터 디렉터리 |

버전은 poc-bootstrap-mini (2026-11-10 실측)와 동일하다. 자세한 표는 VERSIONS.md.

## 설치·실행 (폴더 경로에 공백 없이)

1. `INSTALL.bat` — 최초 1회. 툴 점검 → Maven/npm 오프라인 설정 → PostgreSQL 초기화·기동(app/app 계정 생성). 재실행 안전.
2. `app\run-backend.bat` — Spring Boot http://127.0.0.1:8081/api/hello
3. `app\run-frontend.bat` — Vite http://127.0.0.1:5174 (브라우저에서 Hello, World! 확인)
4. 끝나면 `stop-db.bat` 로 PostgreSQL 중지.

포트: 5432(PG) / 8081(백엔드) / 5174(프런트). hello 앱은 DB를 쓰지 않지만 PG 는 설치·기동까지 검증된 상태로 제공된다.
