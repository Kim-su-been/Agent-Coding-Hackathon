/* 마감일 표기 규칙(REQ-outputhub-028): 지연은 빨강 배지, D-day·임박은 강조 표기로 "언제까지"를 보여 준다. */

export type DueBadgeKind = "overdue" | "today" | "normal";

export interface DueBadge {
  label: string;
  kind: DueBadgeKind;
}

/** 마감일 표기(yyyy-MM-dd)와 D-day 배지를 계산한다. 마감일 미지정이면 null. */
export function useDueDate(today: Date = new Date()): {
  formatDueDate: (dueDate: string | null) => string;
  dueBadge: (dueDate: string | null, overdue: boolean) => DueBadge | null;
} {
  const DAY_MS = 24 * 60 * 60 * 1000;

  const formatDueDate = (dueDate: string | null): string => dueDate ?? "—";

  const dueBadge = (dueDate: string | null, overdue: boolean): DueBadge | null => {
    if (!dueDate) {
      return null;
    }
    if (overdue) {
      const daysLate = Math.floor((startOfDay(today).getTime() - new Date(dueDate).getTime()) / DAY_MS);
      return { label: daysLate > 0 ? `지연 ${daysLate}일` : "지연", kind: "overdue" };
    }
    const daysLeft = Math.round((new Date(dueDate).getTime() - startOfDay(today).getTime()) / DAY_MS);
    if (daysLeft === 0) {
      return { label: "D-day", kind: "today" };
    }
    return { label: `D-${daysLeft}`, kind: "normal" };
  };

  function startOfDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  return { formatDueDate, dueBadge };
}
