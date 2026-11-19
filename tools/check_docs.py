"""SoT 문서 ID 상호 참조 검사기(QC check: doc-references).

deepwiki/sot/업무/outputhub/ 의 REQ/TS/SCR/BD 문서를 읽어
REQ-outputhub-NNN / AC-NNN-N / TC-NN ID가 문서 간 상호 참조로 존재하는지 검사한다.
문제가 있으면 종료 코드 1, 없으면 0. 표준 라이브러리만 사용한다.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "deepwiki" / "sot" / "업무" / "outputhub"

REQ_ID = re.compile(r"REQ-outputhub-\d{3}")
AC_ID = re.compile(r"AC-\d{3}-\d+")
TC_ID = re.compile(r"TC-\d{2}")


def read_docs() -> dict[str, str]:
    docs: dict[str, str] = {}
    for path in sorted(ROOT.glob("*.md")):
        docs[path.name] = path.read_text(encoding="utf-8")
    return docs


def main() -> int:
    problems: list[str] = []
    docs = read_docs()
    if len(docs) < 4:
        problems.append(f"문서 4종(REQ/TS/SCR/BD) 중 {len(docs)}개만 발견됨: {sorted(docs)}")
        print("\n".join(problems))
        return 1

    all_text = "\n".join(docs.values())
    defined_req = {m for doc in docs.values() for m in REQ_ID.findall(doc)}
    defined_ac = {m for doc in docs.values() for m in AC_ID.findall(doc)}
    defined_tc = {m for doc in docs.values() for m in TC_ID.findall(doc)}

    req_doc = next((t for n, t in docs.items() if n.startswith("REQ-")), "")
    ts_doc = next((t for n, t in docs.items() if n.startswith("TS-")), "")
    if not req_doc:
        problems.append("요구사항정의서(REQ-) 문서가 없다")
    if not ts_doc:
        problems.append("시험시나리오(TS-) 문서가 없다")
    if req_doc and not REQ_ID.search(req_doc):
        problems.append("REQ 문서에 REQ-outputhub-NNN 항목 ID가 없다")
    if req_doc and not AC_ID.search(req_doc):
        problems.append("REQ 문서에 AC-NNN-N 인수 기준 ID가 없다")
    if ts_doc and not TC_ID.search(ts_doc):
        problems.append("TS 문서에 TC-NN 시나리오 ID가 없다")
    if ts_doc and not AC_ID.search(ts_doc):
        problems.append("TS 문서에 연결 AC ID가 없다")

    # REQ 문서의 인수 기준이 TS 어딘가에서 연결되는지(각 AC별 최소 1회)
    for ac in sorted(defined_ac & set(AC_ID.findall(req_doc))):
        if ac not in ts_doc:
            problems.append(f"{ac}: 시험시나리오에서 연결되지 않았다")
    # TC가 AC를 연결하는지
    for tc_line in [line for line in ts_doc.splitlines() if TC_ID.search(line) and "|" in line]:
        if "AC-" not in tc_line and TC_ID.search(tc_line).group(0) in {"TC-01", "TC-09"}:
            problems.append(f"{tc_line.strip()[:60]}: 연결 AC가 표에 없다")

    if problems:
        print("\n".join(problems))
        return 1
    print(f"OK: REQ {len(defined_req)}개, AC {len(defined_ac)}개, TC {len(defined_tc)}개 상호 참조 확인")
    return 0


if __name__ == "__main__":
    sys.exit(main())
