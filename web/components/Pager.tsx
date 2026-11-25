import Link from 'next/link';

type Props = { page: number; size: number; total: number; onPage?: (p: number) => void; href?: (p: number) => string };

/** 쪽 번호. onPage 를 주면 단추로, href 를 주면 링크로 그린다(서버에서 그리는 목록은 링크). */
export function Pager({ page, size, total, onPage, href }: Props) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  const from = Math.max(1, Math.min(page - 2, pages - 4));
  const nums: number[] = [];
  for (let p = from; p <= Math.min(pages, from + 4); p++) nums.push(p);
  const item = (p: number, label: string, key: string, disabled = false) => {
    const current = p === page && label === String(p);
    if (disabled) return <span key={key} className="pg off">{label}</span>;
    if (href) return <Link key={key} className="pg" href={href(p)} aria-current={current ? 'page' : undefined}>{label}</Link>;
    return <button key={key} type="button" className="pg" aria-current={current ? 'page' : undefined} onClick={() => onPage?.(p)}>{label}</button>;
  };
  return (
    <nav className="pager" aria-label="쪽 이동">
      {item(page - 1, '이전', 'prev', page <= 1)}
      {nums.map((p) => item(p, String(p), 'p' + p))}
      {item(page + 1, '다음', 'next', page >= pages)}
      <span className="muted small">모두 {total.toLocaleString('ko-KR')}개</span>
    </nav>
  );
}
