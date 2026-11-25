import type { Coupon, Invoice, SubRow, SubStatus } from './types';

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

/** 날짜만 있는 값('2026-11-25')을 '2026년 11월 25일'로. 시간대를 바꾸지 않는다. */
export function day(d: string | null | undefined): string {
  if (!d) return '';
  const [y, m, dd] = d.slice(0, 10).split('-').map(Number);
  return `${y}년 ${m}월 ${dd}일`;
}

/** 한국 날짜 'YYYY-MM-DD'. offsetDays 만큼 앞뒤로 옮길 수 있다. */
export function todayKr(offsetDays = 0): string {
  return new Date(Date.now() + offsetDays * 86400000).toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });
}

// ---- 쿠폰 ----
type CouponLike = Pick<Coupon, 'discountType' | 'discountValue' | 'maxDiscount'>;

/** 쿠폰의 큰 글씨 부분. ['10% 할인', '최대 5,000원'] 처럼 둘째 값은 없을 수 있다. */
export function couponHeadlineParts(c: CouponLike): [string, string | null] {
  if (c.discountType === 'free_shipping') return ['배송비 무료', null];
  if (c.discountType === 'percent') return [c.discountValue + '% 할인', c.maxDiscount ? '최대 ' + won(c.maxDiscount) : null];
  return [won(c.discountValue) + ' 할인', null];
}

/** '3,000원 할인', '10% 할인 (최대 5,000원)', '배송비 무료' */
export function couponHeadline(c: CouponLike): string {
  const [main, sub] = couponHeadlineParts(c);
  return sub ? `${main} (${sub})` : main;
}

/** 쿠폰의 조건 몇 줄: 어디서, 얼마 이상, 무엇에, 언제까지 */
export function couponTerms(c: Coupon, expiresAt?: string | null): string[] {
  return [
    c.issuer === 'platform' ? '술장터 쿠폰, 어느 가게에서나' : `${c.shopName ?? '이 가게'}에서만`,
    c.minOrder > 0 ? `${won(c.minOrder)} 이상 살 때` : '금액 조건 없음',
    [c.categoryName ? `${c.categoryName} 상품만` : '모든 상품', c.excludeAlcohol ? '술 제외' : '술 포함'].join(', '),
    expiresAt ? `${when(expiresAt)}까지`
      : c.validDays ? `받은 날부터 ${c.validDays}일 (늦어도 ${day(c.validTo)}까지)`
      : `${day(c.validTo)}까지`,
  ];
}

export const COUPON_STATUS_LABEL: Record<string, string> = { active: '쓰는 중', paused: '멈춤', ended: '끝남' };
export const ISSUE_TYPE_LABEL: Record<string, string> = { signup: '가입하면 자동 지급', download: '쿠폰 받기 화면에서 받기', code: '코드 입력', grant: '운영자가 직접 지급' };

/** 판매자별 주문에 쓴 쿠폰 할인(상품 + 배송). 값이 없으면 0 */
export const couponOff = (s: SubRow) => (s.itemDiscount ?? 0) + (s.shipDiscount ?? 0);

/** 청구서에서 실제로 낼 금액. 서버가 net 을 보내지 않으면 직접 계산한다. */
export const invoiceNet = (i: Invoice) => i.net ?? i.amount - (i.platformCouponCredit ?? 0);
