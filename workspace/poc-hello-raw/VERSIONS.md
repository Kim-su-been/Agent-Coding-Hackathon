# 버전 현황

poc-bootstrap-mini의 VERSIONS.md (2026-11-10 실측)와 동일한 버전으로 구성했다.

## 툴체인

| 구성요소 | 버전 | 위치 |
|---|---|---|
| Node.js | 24.22.2 | `node/` |
| npm | 11.19.1 | Node 동봉판 |
| OpenJDK | Temurin 21.0.12.1+1 (LTS) | `jdk/` |
| Maven | 3.9.16 | `maven/` (오프라인 `-s maven\conf\settings.xml`) |
| PostgreSQL | 18.6 (서버 메이저 18) | `pgsql/` + `pgdata/`, 포트 5432 |

## hello 예제 앱

| 구성요소 | 버전 | 확인 방법 |
|---|---|---|
| Spring Boot | 4.1.1 | `app/backend/pom.xml` (parent) |
| hello-backend | 0.0.1, Java 21 | `app/backend/pom.xml` |
| React / React DOM | 19.3.1 | `app/frontend/package.json` + node_modules |
| Vite | 8.3.5 | `app/frontend/package.json` + node_modules |
| @vitejs/plugin-react | 6.1.2 | `app/frontend/package.json` |
