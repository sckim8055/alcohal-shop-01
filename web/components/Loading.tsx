// 자료를 불러오는 동안 들어올 내용의 모양대로 빈 자리를 보여 준다.
// 화면이 비어 있다가 갑자기 바뀌지 않게 하려는 것이다. 빨리 끝나면(0.15초 안) 깜빡이지 않도록 CSS 에서 늦게 나타나게 했다.
// 화면 읽기 프로그램에는 "불러오는 중입니다" 한 마디만 읽히고 빈 모양은 건너뛴다.

type Kind = 'list' | 'stalls' | 'table' | 'detail';

export function Loading({ kind = 'list', rows = 3, label = '불러오는 중입니다' }: { kind?: Kind; rows?: number; label?: string }) {
  return (
    <div className={'loading loading-' + kind} role="status" aria-live="polite">
      <span className="sr-only">{label}</span>
      <div aria-hidden="true">{shape(kind, rows)}</div>
    </div>
  );
}

function Line({ w, big }: { w: number; big?: boolean }) {
  return <span className={big ? 'skel skel-big' : 'skel'} style={{ width: w + '%' }} />;
}

function shape(kind: Kind, rows: number) {
  // 줄 길이를 조금씩 달리해야 실제 글처럼 보인다.
  const widths = [72, 54, 86, 63, 47, 78];
  switch (kind) {
    case 'stalls':
      return (
        <div className="stalls">
          {Array.from({ length: Math.max(rows, 4) }, (_, i) => (
            <div className="skel-stall" key={i}>
              <span className="skel-art" />
              <span className="skel-info"><Line w={widths[i % 6]} big /><Line w={40} /></span>
            </div>
          ))}
        </div>
      );
    case 'table':
      return (
        <div className="skel-sheet">
          <Line w={30} big />
          {Array.from({ length: Math.max(rows, 5) }, (_, i) => (
            <span className="skel-row" key={i}><Line w={18} /><Line w={widths[i % 6] / 2} /><Line w={14} /></span>
          ))}
        </div>
      );
    case 'detail':
      return (
        <div className="detail">
          <span className="skel-art skel-photo" />
          <div>
            <Line w={80} big /><Line w={45} /><br /><Line w={35} big /><br /><Line w={90} /><Line w={70} /><Line w={84} />
          </div>
        </div>
      );
    default:
      return (
        <div className="stack">
          {Array.from({ length: rows }, (_, i) => (
            <div className="skel-sheet" key={i}><Line w={widths[i % 6] / 1.6} big /><Line w={widths[(i + 2) % 6]} /><Line w={widths[(i + 4) % 6]} /></div>
          ))}
        </div>
      );
  }
}
