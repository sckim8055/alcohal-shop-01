// 별점은 글자(★☆)로 그린다. 서버 화면과 브라우저 화면 모두에서 쓰도록 'use client' 를 붙이지 않는다.

/** value 는 0~5. 평균처럼 소수가 오면 별은 반올림해 그리고, 읽어 주는 글에는 소수 한 자리까지 쓴다. */
export function Stars({ value, className }: { value: number; className?: string }) {
  const v = Math.max(0, Math.min(5, Number.isFinite(value) ? value : 0));
  const full = Math.round(v);
  const label = Number.isInteger(v) ? String(v) : v.toFixed(1);
  return (
    <span className={'stars' + (className ? ' ' + className : '')} role="img" aria-label={`별 5개 중 ${label}개`}>
      <span aria-hidden="true">{'★'.repeat(full)}{'☆'.repeat(5 - full)}</span>
    </span>
  );
}
