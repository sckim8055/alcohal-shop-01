import Link from 'next/link';
import { Kettle } from '@/components/Art';
import { ProductCard } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { categoryTree } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { Category, Paged, Product } from '@/lib/types';

export const dynamic = 'force-dynamic';

const SORTS = [['', '새로 나온 순'], ['price_asc', '값이 싼 순'], ['price_desc', '값이 비싼 순']] as const;

export default async function Home({ searchParams }: { searchParams: Promise<{ cat?: string; q?: string; sort?: string; page?: string }> }) {
  const { cat, q, sort, page } = await searchParams;
  const link = (over: Record<string, string | undefined>) => {
    const query = new URLSearchParams();
    for (const [k, v] of Object.entries({ cat, q, sort, ...over })) if (v) query.set(k, v);
    return '/' + (query.size ? '?' + query : '');
  };
  const api = new URLSearchParams();
  if (cat) api.set('cat', cat);
  if (q) api.set('q', q);
  if (sort) api.set('sort', sort);
  if (page) api.set('page', page);
  const [categories, products] = await Promise.all([
    serverGet<Category[]>('/categories'),
    serverGet<Paged<Product>>('/products' + (api.size ? '?' + api : '')),
  ]);

  if (!categories || !products) {
    return (
      <div className="note error">
        서버에 연결할 수 없습니다. <code>run.bat</code>으로 서버를 먼저 켠 뒤 새로 고쳐 주세요.
      </div>
    );
  }
  const tree = categoryTree(categories);
  const selected = categories.find((c) => String(c.id) === cat);
  const topId = selected ? selected.parentId ?? selected.id : null;
  const open = tree.find((t) => t.top.id === topId);
  const filtered = Boolean(cat || q || page);

  return (
    <>
      {!filtered && (
        <section className="hero">
          <div>
            <h1>오늘도 장이 섰습니다</h1>
            <p>
              동네 양조장이 빚은 술, 술을 빚는 누룩과 발효조, 술맛을 살리는 잔, 직접 빚어 보는 수업까지.
              술에 관한 것이라면 다 나와 있습니다.
            </p>
          </div>
          <div className="kettle"><Kettle /></div>
        </section>
      )}

      <form className="search" action="/" role="search">
        {cat && <input type="hidden" name="cat" value={cat} />}
        {sort && <input type="hidden" name="sort" value={sort} />}
        <input name="q" defaultValue={q ?? ''} placeholder="막걸리, 누룩, 술잔" aria-label="상품 이름으로 찾기" />
        <button className="btn" type="submit">찾기</button>
      </form>

      <nav className="chips" aria-label="분류">
        <Link className="chip" href={link({ cat: undefined, page: undefined })} aria-current={!cat}>전부</Link>
        {tree.map((t) => (
          <Link key={t.top.id} className="chip" href={link({ cat: String(t.top.id), page: undefined })} aria-current={topId === t.top.id}>
            {t.top.name}
          </Link>
        ))}
      </nav>
      {open && open.children.length > 0 && (
        <nav className="subchips" aria-label={open.top.name + ' 안의 분류'}>
          <Link href={link({ cat: String(open.top.id), page: undefined })} aria-current={cat === String(open.top.id)}>{open.top.name} 전체</Link>
          {open.children.map((c) => (
            <Link key={c.id} href={link({ cat: String(c.id), page: undefined })} aria-current={cat === String(c.id)}>{c.name}</Link>
          ))}
        </nav>
      )}

      <div className="spread" style={{ marginTop: 14 }}>
        <span className="muted small">{q ? `"${q}" 찾은 결과 ` : ''}{products.total.toLocaleString('ko-KR')}개</span>
        <nav className="subchips" aria-label="늘어놓는 순서" style={{ margin: 0 }}>
          {SORTS.map(([value, label]) => (
            <Link key={value} href={link({ sort: value || undefined, page: undefined })} aria-current={(sort ?? '') === value}>{label}</Link>
          ))}
        </nav>
      </div>

      {products.items.length === 0 ? (
        <div className="sheet" style={{ marginTop: 22 }}>
          <h3>찾는 물건이 아직 장에 없습니다</h3>
          <p>다른 이름으로 찾아보거나 <Link href="/">전체 좌판</Link>을 둘러보세요.</p>
        </div>
      ) : (
        <>
          <div className="stalls">
            {products.items.map((p) => <ProductCard key={p.id} p={p} />)}
          </div>
          <Pager page={products.page} size={products.size} total={products.total} href={(p) => link({ page: p > 1 ? String(p) : undefined })} />
        </>
      )}
    </>
  );
}
