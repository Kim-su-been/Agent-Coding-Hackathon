---
okf_version: "0.2"
type: glossary
title: 산출물 접수·검토 보드 용어집
description: 산출물 접수·검토 보드 업무와 코드에서 쓰는 용어.
resource: 업무/outputhub/GLO-OUTPUTHUB-001.md
tags:
  - glossary
question: 이 프로젝트의 용어는 무엇을 뜻하나?
sources:
  - resource: repo://기획 논의(계획 v3 A~E절)
  - resource: repo://.deepwork/attachments/Agent_Instructions-8b78b746e0c0.md
glossary:
  terms:
    - term: 차수
      meaning: 계약 품의 등 산출물 접수의 관리 회차 단위.
    - term: 기준대장
      meaning: 차수별로 받아야 할 산출물 항목 목록. PMO가 미리 등록한다.
    - term: 산출물 항목
      meaning: 대장의 한 행. 집계·상태의 관리 단위(파일명이 아님).
    - term: 접수
      meaning: 파일 업로드가 대장 행과 연결된 상태.
    - term: 확정본
      meaning: PMO가 확정한 최종 버전. 새 업로드로 덮어쓰지 않는 보호 대상.
    - term: 보완 필요
      meaning: 접수 후 PMO가 보완을 요청한 상태. 재접수로 복귀한다.
    - term: 신규(자동등록)
      meaning: 매칭 실패 파일이 자동 등록된 대장 행. 기본 필수=아니오.
    - term: 중복
      meaning: 동일 해시 파일. 접수 종수에 계산하지 않는다.
    - term: 매칭 근거
      meaning: 파일이 행에 연결된 판단 근거(키워드·문서 위치).
    - term: 체크 보드
      meaning: 기준대장 전체 상태(진행률 %·제출/미제출)를 보는 메인 화면.
    - term: DTO
      meaning: 계층 간 데이터 전송 객체. Entity를 API에 노출하지 않기 위해 쓴다.
    - term: Composable
      meaning: Vue 3에서 재사용 가능한 상태·로직 함수. composables/ 디렉터리에 둔다.
generated:
  by: agent:deepwork:sot-init
  at: 2026-11-19T02:45:30.222Z
---


# 산출물 접수·검토 보드 용어집

질문: 이 프로젝트의 용어는 무엇을 뜻하나? 답: 아래 표의 정의를 문서·코드·화면에서 같은 뜻으로 쓴다.

## 1. 업무 용어

| 용어 | 영문/ID | 정의 | 근거 |
|---|---|---|---|
| 차수 | Round | 계약 품의 등 산출물 접수의 관리 회차 단위 | 기획 A-6 |
| 기준대장 | Checklist / Deliverable Ledger | 차수별로 받아야 할 산출물 항목 목록. 미리 등록한다 | 기획 A-1 |
| 산출물 항목 | Deliverable | 대장의 한 행. 집계·상태의 관리 단위(파일명이 아님) | 기획 A-1 |
| 접수 | Submission | 파일 업로드가 대장 행과 연결된 상태 | 기획 A-2 |
| 확정본 | Confirmed Version | PMO가 확정한 최종 버전. 새 업로드로 덮어쓰지 않는 보호 대상 | 기획 A-2 |
| 보완 필요 | Needs Revision | 접수 후 PMO가 보완을 요청한 상태. 재접수로 복귀 | 기획 A-2 |
| 신규(자동등록) | Auto-registered | 매칭 실패 파일이 자동 등록된 대장 행. 기본 필수=아니오 | 기획 D-3 |
| 중복 | Duplicate | 동일 해시 파일. 접수 종수에 계산하지 않는다 | 기획 D-3 |
| 매칭 근거 | Match Evidence | 파일이 행에 연결된 판단 근거(키워드·문서 위치). 상세 화면으로 표시 | 기획 C-3 |
| 체크 보드 | Check Board | 기준대장 전체 상태(진행률 %·제출/미제출)를 보는 메인 화면 | 기획 C-1 |

## 2. 기술 용어

| 용어 | 영문 | 정의 | 근거 |
|---|---|---|---|
| DTO | Data Transfer Object | 계층 간 데이터 전송 객체. Entity를 API에 노출하지 않기 위해 쓴다 | STD-001 3장 |
| Composable | Composable | Vue 3에서 재사용 가능한 상태·로직 함수. composables/ 디렉터리에 둔다 | STD-001 4장 |
| 계층형 아키텍처 | Layered Architecture | Controller/Service/Repository로 책임을 나눈 구조 | STD-001 3장 |
| Given-When-Then | Given-When-Then | 단위 테스트 작성 패턴. 사전 상태-행동-기대 결과 | STD-001 6장 |
| Early Return | Early Return | 조건 불일치 시 빠르게 반환해 중첩을 2단계 이내로 유지하는 기법 | STD-001 2장 |

## 3. 남은 것

- [확인 필요: 화면·API 용어 확정 시 추가 · 확인자: 개발 담당 · 기한: 미정]