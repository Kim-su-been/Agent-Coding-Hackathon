---
okf_version: "0.2"
type: standard-rule
title: AI 코딩 에이전트 규칙(클린 코드·아키텍처)
description: 이 프로젝트의 모든 코드는 클린 코드 원칙과 계층형 아키텍처 규칙을 따른다.
resource: 표준/STD-001-agent-코딩-규칙.md
tags:
  - standard
  - coding
question: 이 프로젝트의 코드는 어떤 규칙으로 쓰고 어떻게 검증하나?
sources:
  - resource: repo://.deepwork/attachments/Agent_Instructions-8b78b746e0c0.md
rule:
  id: STD-001
  grade: required
  intent: 모든 코드는 클린 코드 원칙과 계층형 아키텍처(STD-001)를 따른다
  verify:
    level: L3
    evidence: review
  owner: dev-team
generated:
  by: agent:deepwork:sot-init
  at: 2026-11-19T02:45:30.222Z
---


# AI 코딩 에이전트 규칙(클린 코드·아키텍처)

질문: 이 프로젝트의 코드는 어떤 규칙으로 쓰고 어떻게 검증하나? 답: 클린 코드와 유지보수성을 최우선으로 하며, 아래 기술 스택·아키텍처·테스트 규칙을 엄격히 준수한다(원천: 첨부 Agent Instructions 전문 전달).

## 1. 기술 스택

- Backend: Java 17+, Spring Boot 3.x, Spring Data JPA
- Frontend: Vue 3(Composition API, `<script setup>`), TypeScript 권장

## 2. 공통 클린 코드 원칙

- 단일 책임 원칙(SRP): 하나의 함수(메서드)와 클래스는 오직 하나의 기능만 담당한다.
- 함수 길이 제한: 모든 함수는 가급적 20줄 이내로 쓴다. 길어지면 의미 있는 단위의 헬퍼 함수로 분리한다.
- 깊은 중첩 금지(Early Return): if-else 중첩을 피하고, 조건이 맞지 않으면 빠르게 반환해 들여쓰기를 최대 2단계 이내로 유지한다.
- 명확한 네이밍: 축약어를 쓰지 않고, 변수명·함수명만 읽어도 의도와 역할을 파악할 수 있게 서술적으로 쓴다(예: getUserData 대신 findActiveUserByEmail).
- 매직 넘버/문자열 금지: 하드코딩된 숫자·문자열은 의미 있는 이름의 상수(static final 또는 const)로 추출한다.
- 주석 원칙: 코드를 설명하는 주석(What)은 달지 않는다. 코드가 스스로 설명하게 하고, 주석은 비즈니스 맥락이나 설계 의도(Why)가 필요할 때만 최소한으로 쓴다.

## 3. Backend 규칙(Java / Spring Boot)

- 계층형 아키텍처 엄수: Controller는 HTTP 요청/응답 처리와 입력값 검증(`@Valid`)만 하고 비즈니스 로직을 절대 포함하지 않는다. Service는 핵심 비즈니스 로직과 트랜잭션(`@Transactional`) 경계를 담당한다. Repository는 데이터베이스 접근 로직만 담당한다.
- DTO 사용 강제: Entity 클래스를 API 요청/응답에 직접 노출하지 않는다. 계층 간 데이터 이동에는 전용 DTO(Request, Response)를 쓴다.
- 불변성 유지: 엔티티에 무분별한 `@Setter`를 쓰지 않는다. 상태 변경이 필요하면 의도가 드러나는 비즈니스 메서드(예: order.cancel())를 작성한다.
- 생성자 주입: 필드 주입(`@Autowired`) 대신 생성자 주입(`@RequiredArgsConstructor` 활용)을 쓴다.
- 예외 처리: try-catch로 예외를 남발해 흐름을 제어하지 않는다. 커스텀 예외를 정의하고 `@RestControllerAdvice`로 전역적으로 일관된 에러 응답을 반환한다.

## 4. Frontend 규칙(Vue 3)

- Composition API 사용: 모든 컴포넌트는 `<script setup>` 문법으로 작성한다.
- 컴포넌트 분리: 하나의 컴포넌트 파일(.vue)은 200줄을 넘지 않게 UI 요소·기능 단위로 작게 분리한다.
- 비즈니스 로직 분리(Composables): API 호출, 복잡한 데이터 가공, 상태 관리 로직은 컴포넌트 내부에 두지 않고 composables/ 디렉터리(예: useUserAuth.ts)로 분리해 재사용성을 높인다.
- 단방향 데이터 흐름: props를 직접 수정(Mutate)하지 않는다. 상태 변경은 emit 이벤트나 상태 관리 라이브러리(Pinia)로 처리한다.
- API 통신 분리: 컴포넌트에서 axios·fetch를 직접 호출하지 않는다. 별도의 api/ 계층 모듈로 호출한다.

## 5. 작업 수행 방식

1. 설계 우선: 코드를 쓰기 전에 어떤 파일과 함수로 책임을 분리할지 구조를 먼저 간단히 설명한다.
2. 점진적 구현: 한 번에 거대한 코드를 만들지 않고 백엔드·프론트엔드의 핵심 단위(도메인별)로 나누어 명확하게 구현한다.
3. 자가 검증: 코드 작성 후 위 규칙(함수 길이, 단일 책임, 계층 분리 등) 위반이 없는지 스스로 점검하고 리팩토링 결과를 제시한다.

## 6. 테스트 및 데이터 접근 규칙

- 단위 테스트 필수: Service 계층 비즈니스 로직 구현 시 JUnit 5 기반 단위 테스트를 Given-When-Then 패턴으로 함께 작성한다. 정상 케이스와 예외 케이스를 모두 포함한다.
- JPA 최적화: 모든 연관관계는 FetchType.LAZY를 기본으로 하고, 단순 조회 메서드에는 `@Transactional(readOnly = true)`를 적용한다.

## 7. 패키지 및 폴더 구조

- Backend(도메인형 구조): com.project.domain.[도메인명] 하위에 controller, service, repository, entity, dto 패키지를 배치한다.
- Frontend(역할별 구조): src/ 하위에 views(페이지), components(공통 UI), composables(로직), api(통신), types(타입 정의) 폴더를 엄격히 구분해 파일을 만든다.
- Vue UI 상태 관리: `<template>` 안 복잡한 연산을 금지한다(computed 활용). API 호출 시 로딩(isLoading)과 에러(error) 상태를 반드시 처리한다.

## 8. AI 에이전트 행동 제약(Guardrails)

- 최소 변경 원칙: 기존 파일 수정 시 요청된 기능과 무관한 코드를 임의로 리팩토링하거나 삭제하지 않는다.
- 패키지 설치 제한: 새 라이브러리(build.gradle, package.json) 추가가 필요하면 코드를 수정하기 전에 먼저 승인을 요청한다.
- 모호함 해결: 기획·스펙이 불명확하면 임의로 가정해 코딩하지 말고 구현 전 질문으로 확인받는다.

## 9. 이 프로젝트 적용 사항

- 매칭 판정(REQ-outputhub-004)·버전 판정 로직은 Service 계층 도메인 로직으로 분리하고 Controller에 두지 않는다.
- 기본 산출물 목록과 매칭 키워드 규칙은 설정/템플릿 파일로 둔다(REQ-outputhub-014, 매직 넘버 금지의 적용).
- 기준대장·접수·버전·감사 도메인은 7장 도메인형 구조를 따라 domain/round, domain/deliverable, domain/submission, domain/version, domain/audit으로 나눈다.