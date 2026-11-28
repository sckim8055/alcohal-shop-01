// 편집기 문서(JSON)를 화면에 그린다. 허용한 요소만 골라 React 요소로 만들기 때문에
// 문서에 무엇이 들어 있어도 스크립트가 실행되지 않는다(HTML 을 그대로 꽂지 않는다).
// 허용 규칙은 lib/richdoc.ts 에 있고 서버(ProductDoc.java)도 같은 규칙으로 검사한다.

import type { CSSProperties, ReactNode } from 'react';
import { ALIGNS, OWN_VIDEO, SAFE_COLOR, SAFE_SIZE, safeImage, safeLink, youtubeId } from '@/lib/richdoc';

type Mark = { type: string; attrs?: Record<string, unknown> };
type Node = { type?: string; text?: string; attrs?: Record<string, unknown>; marks?: Mark[]; content?: Node[] };

function text(node: Node, key: number): ReactNode {
  let el: ReactNode = node.text ?? '';
  for (const m of node.marks ?? []) {
    if (m.type === 'bold') el = <strong>{el}</strong>;
    else if (m.type === 'italic') el = <em>{el}</em>;
    else if (m.type === 'underline') el = <u>{el}</u>;
    else if (m.type === 'strike') el = <s>{el}</s>;
    else if (m.type === 'textStyle') {
      const style: CSSProperties = {};
      const c = m.attrs?.color;
      const s = m.attrs?.fontSize;
      if (typeof c === 'string' && SAFE_COLOR.test(c)) style.color = c;
      if (typeof s === 'string' && SAFE_SIZE.test(s)) style.fontSize = s;
      if (style.color || style.fontSize) el = <span style={style}>{el}</span>;
    } else if (m.type === 'link') {
      const href = safeLink(m.attrs?.href);
      if (href) el = <a href={href} target="_blank" rel="noopener noreferrer nofollow ugc">{el}</a>;
    }
  }
  return <span key={key}>{el}</span>;
}

function alignOf(node: Node): CSSProperties | undefined {
  const a = node.attrs?.textAlign;
  return typeof a === 'string' && a !== 'left' && ALIGNS.includes(a) ? { textAlign: a as CSSProperties['textAlign'] } : undefined;
}

function span(v: unknown): number | undefined {
  const n = Number(v);
  return Number.isInteger(n) && n > 1 && n <= 20 ? n : undefined;
}

function image(node: Node, key: number): ReactNode {
  const src = safeImage(node.attrs?.src);
  if (!src) return null;
  const alt = typeof node.attrs?.alt === 'string' ? node.attrs.alt : '';
  // 다른 사이트 사진에 이 사이트 주소를 넘기지 않는다(넘기면 막는 사이트가 많고, 방문 기록이 새지 않게).
  return <img key={key} src={src} alt={alt} loading="lazy" decoding="async" referrerPolicy="no-referrer" />;
}

function render(node: Node, key: number): ReactNode {
  const kids = () => (node.content ?? []).map((c, i) => render(c, i));
  switch (node.type) {
    case 'text': return text(node, key);
    case 'paragraph': return <p key={key} style={alignOf(node)}>{kids()}</p>;
    case 'heading': return Number(node.attrs?.level) === 3 ? <h3 key={key} style={alignOf(node)}>{kids()}</h3> : <h2 key={key} style={alignOf(node)}>{kids()}</h2>;
    case 'bulletList': return <ul key={key}>{kids()}</ul>;
    case 'orderedList': return <ol key={key}>{kids()}</ol>;
    case 'listItem': return <li key={key}>{kids()}</li>;
    case 'blockquote': return <blockquote key={key}>{kids()}</blockquote>;
    case 'callout': return <aside key={key} className="callout">{kids()}</aside>;
    case 'horizontalRule': return <hr key={key} />;
    case 'hardBreak': return <br key={key} />;
    case 'image': return image(node, key);
    case 'imageRow': {
      const imgs = (node.content ?? []).map((c, i) => image(c, i)).filter(Boolean);
      return <div key={key} className={'image-row ' + (node.attrs?.layout === 'slide' ? 'slide' : 'grid')}>{imgs}</div>;
    }
    case 'video': {
      const src = node.attrs?.src;
      return typeof src === 'string' && OWN_VIDEO.test(src) ? <video key={key} src={src} controls preload="metadata" playsInline /> : null;
    }
    case 'youtube': {
      const id = youtubeId(node.attrs?.src);
      if (!id) return null;
      return (
        <div key={key} className="embed">
          <iframe src={`https://www.youtube-nocookie.com/embed/${id}`} title="유튜브 동영상" loading="lazy"
            allow="accelerometer; encrypted-media; gyroscope; picture-in-picture" allowFullScreen referrerPolicy="strict-origin-when-cross-origin" />
        </div>
      );
    }
    case 'table': return <div key={key} className="scroll"><table className="rich-table"><tbody>{kids()}</tbody></table></div>;
    case 'tableRow': return <tr key={key}>{kids()}</tr>;
    case 'tableHeader': return <th key={key} colSpan={span(node.attrs?.colspan)} rowSpan={span(node.attrs?.rowspan)}>{kids()}</th>;
    case 'tableCell': return <td key={key} colSpan={span(node.attrs?.colspan)} rowSpan={span(node.attrs?.rowspan)}>{kids()}</td>;
    default: {
      const k = kids();
      return k.length ? <div key={key}>{k}</div> : null;
    }
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
