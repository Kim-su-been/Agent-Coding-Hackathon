# Agent-Coding-Hackathon

산출물 접수·검토 보드(outputhub) MVP. 백엔드: Spring Boot + H2(`backend/`), 프론트: Vue 3 + TS(`frontend/`), 검증 래퍼·문서 참조 검사기(`tools/`).

## 데모 실행(파일 하나)

1. 최초 1회 빌드: `tools\build-demo.cmd` — 프론트 vite build 후 백엔드 bootJar 를 만든다(jar 에 프론트 dist 가 정적 자원으로 포함된다).
2. 작업 폴더 루트의 `데모실행.bat` 실행 — 서버(8080) 기동 후 브라우저에서 http://localhost:8080 을 연다. 서버는 최소화된 "outputhub-demo" 콘솔 창에서 돌고, 그 창을 닫으면 데모가 종료된다.
3. 이미 서버가 떠 있으면(프론트 페이지 200 응답) 런처는 서버를 다시 띄우지 않고 브라우저만 연다.

데이터는 `backend` 기준 `../data`(H2 파일), 업로드 원본은 `../storage` 에 저장된다.

## 검증 명령 관련 참고

QC 검사기가 `cmd /c Agent-Coding-Hackathon/tools/(래퍼).cmd`(슬래시 상대 경로) 형태로 호출하는데, cmd.exe는 이 경로를 명령 이름 + 스위치로 해석해 직접 실행에 실패한다. 이를 받아 실제 래퍼로 포워딩하는 어댑터가 작업 폴더 루트의 `Agent-Coding-Hackathon.cmd`에 있다. 래퍼 스크립트·검사기 자체는 바꾸지 않았다.