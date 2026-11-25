// 편집기 문서(JSON)를 화면에 그린다. 허용한 요소만 골라 React 요소로 만들기 때문에
// 문서에 무엇이 들어 있어도 스크립트가 실행되지 않는다(HTML 을 그대로 꽂지 않는다).

type Node = { type?: string; text?: string; attrs?: Record<string, unknown>; marks?: { type: string; attrs?: Record<string, unknown> }[]; content?: Node[] };

const SAFE_IMAGE = /^\/api\/files\/[a-f0-9]{32}\.(jpg|png|gif|webp)$/;

function safeLink(href: unknown): string | null {
  return typeof href === 'string' && /^https?:\/\//i.test(href) ? href : null;
}

function text(node: Node, key: number): React.ReactNode {
  let el: React.ReactNode = node.text ?? '';
  for (const m of node.marks ?? []) {
    if (m.type === 'bold') el = <strong>{el}</strong>;
    else if (m.type === 'italic') el = <em>{el}</em>;
    else if (m.type === 'underline') el = <u>{el}</u>;
    else if (m.type === 'strike') el = <s>{el}</s>;
    else if (m.type === 'link') {
      const href = safeLink(m.attrs?.href);
      if (href) el = <a href={href} target="_blank" rel="noopener noreferrer nofollow">{el}</a>;
    }
  }
  return <span key={key}>{el}</span>;
}

function render(node: Node, key: number): React.ReactNode {
  const kids = (node.content ?? []).map((c, i) => render(c, i));
  switch (node.type) {
    case 'text': return text(node, key);
    case 'paragraph': return <p key={key}>{kids}</p>;
    case 'heading': return Number(node.attrs?.level) === 3 ? <h3 key={key}>{kids}</h3> : <h2 key={key}>{kids}</h2>;
    case 'bulletList': return <ul key={key}>{kids}</ul>;
    case 'orderedList': return <ol key={key}>{kids}</ol>;
    case 'listItem': return <li key={key}>{kids}</li>;
    case 'blockquote': return <blockquote key={key}>{kids}</blockquote>;
    case 'horizontalRule': return <hr key={key} />;
    case 'hardBreak': return <br key={key} />;
    case 'image': {
      const src = node.attrs?.src;
      // 사진은 이 사이트에 올린 파일만 보여 준다.
      return typeof src === 'string' && SAFE_IMAGE.test(src) ? <img key={key} src={src} alt="" loading="lazy" /> : null;
    }
    default: return kids.length ? <div key={key}>{kids}</div> : null;
  }
}

export function RichView({ doc, fallback }: { doc: string | null; fallback: string }) {
  let parsed: Node | null = null;
  if (doc) {
    try {
      parsed = JSON.parse(doc) as Node;
    } catch {
      parsed = null;
    }
  }
  if (!parsed || parsed.type !== 'doc') return <p style={{ whiteSpace: 'pre-wrap' }}>{fallback}</p>;
  return <div className="rich">{(parsed.content ?? []).map((c, i) => render(c, i))}</div>;
}
