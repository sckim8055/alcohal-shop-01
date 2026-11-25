import type { SubStatus } from './types';

export const won = (n: number) => n.toLocaleString('ko-KR') + '원';

/** 서버 시각은 UTC이고 시간대 표시가 없다. 한국 시간으로 바꿔 보여 준다. */
export function when(utc: string | null | undefined, withTime = true): string {
  if (!utc) return '';
  const d = new Date(utc.endsWith('Z') ? utc : utc + 'Z');
  return d.toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric',
    ...(withTime ? { hour: '2-digit', minute: '2-digit', hour12: false } : {}),
  });
}

export const percent = (bp: number | null | undefined) => (bp == null ? '' : String(bp / 100));

export const STATUS_LABEL: Record<SubStatus, string> = {
  awaiting_deposit: '입금 대기',
  paid: '입금 확인',
  shipped: '배송 중',
  delivered: '배송 완료',
  completed: '구매 확정',
  cancelled: '취소',
  refunded: '환불',
};

export const PRODUCT_STATUS_LABEL: Record<string, string> = {
  draft: '임시저장',
  on_sale: '판매 중',
  paused: '판매 중지',
  ended: '판매 종료',
  blocked: '운영자 차단',
};

/** 대분류와 그 아래 중분류로 묶는다. */
export function categoryTree<T extends { id: number; parentId: number | null }>(list: T[]): { top: T; children: T[] }[] {
  return list.filter((c) => c.parentId == null).map((top) => ({ top, children: list.filter((c) => c.parentId === top.id) }));
}
