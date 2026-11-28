// 상품 설명 문서에서 허용하는 값. 편집기, 화면 표시(RichView), HTML 가져오기가 같은 규칙을 쓴다.
// 서버(ProductDoc.java)도 같은 규칙으로 한 번 더 검사한다. 한쪽을 바꾸면 다른 쪽도 맞춘다.

/** 이 사이트에 올린 사진 */
export const OWN_IMAGE = /^\/api\/files\/[a-f0-9]{32}\.(jpg|png|gif|webp)$/;
/** 이 사이트에 올린 동영상 */
export const OWN_VIDEO = /^\/api\/files\/[a-f0-9]{32}\.(mp4|webm)$/;
/** 다른 사이트의 사진. https 만 받는다(http 사진은 https 화면에서 막힌다). */
export const OUTSIDE_IMAGE = /^https:\/\/[^\s"'<>]{1,2000}$/;

export function safeImage(src: unknown): string | null {
  return typeof src === 'string' && (OWN_IMAGE.test(src) || OUTSIDE_IMAGE.test(src)) ? src : null;
}

export function isOutsideImage(src: unknown): boolean {
  return typeof src === 'string' && !OWN_IMAGE.test(src);
}

export function safeLink(href: unknown): string | null {
  return typeof href === 'string' && /^https?:\/\/[^\s"'<>]{1,2000}$/i.test(href) ? href : null;
}

/** 유튜브 주소에서 영상 번호(11자)만 꺼낸다. 화면에는 이 번호로 만든 주소만 쓴다. */
export function youtubeId(src: unknown): string | null {
  if (typeof src !== 'string') return null;
  const m = src.match(/^https?:\/\/(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?(?:[^#]*&)?v=|embed\/|shorts\/|live\/)|youtube-nocookie\.com\/embed\/|youtu\.be\/)([A-Za-z0-9_-]{11})(?:[?&#/].*)?$/);
  return m ? m[1] : null;
}

/** 글자 색: 편집기는 아래 몇 가지만 고르게 하지만, 다른 몰에서 가져온 글은 원래 색을 유지한다. */
export const SAFE_COLOR = /^(#[0-9a-fA-F]{3,8}|rgba?\(\s*[0-9.]+%?\s*,\s*[0-9.]+%?\s*,\s*[0-9.]+%?\s*(,\s*[0-9.]+\s*)?\))$/;
export const SAFE_SIZE = /^([0-9]{1,2}(\.[0-9]+)?)(px|em|rem)$/;
export const ALIGNS = ['left', 'center', 'right', 'justify'];

export const COLORS: { label: string; value: string | null }[] = [
  { label: '기본 색', value: null },
  { label: '빨강', value: '#d62b1f' },
  { label: '초록', value: '#17603e' },
  { label: '파랑', value: '#1e4fa3' },
  { label: '밤색', value: '#6b5a50' },
  { label: '주황', value: '#c26a00' },
];

export const SIZES: { label: string; value: string | null }[] = [
  { label: '보통 크기', value: null },
  { label: '작게', value: '14px' },
  { label: '크게', value: '20px' },
  { label: '아주 크게', value: '28px' },
];
