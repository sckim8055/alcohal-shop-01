import Link from 'next/link';
import { Kettle } from '@/components/Art';
import { ProductCard } from '@/components/Bits';
import { serverGet } from '@/lib/server';
import type { Category, Product } from '@/lib/types';

export const dynamic = 'force-dynamic';

export default async function Home({ searchParams }: { searchParams: Promise<{ cat?: string; q?: string }> }) {
  const { cat, q } = await searchParams;
  const query = new URLSearchParams();
  if (cat) query.set('cat', cat);
  if (q) query.set('q', q);
  const [categories, products] = await Promise.all([
    serverGet<Category[]>('/categories'),
    serverGet<Product[]>('/products' + (query.size ? '?' + query : '')),
  ]);

  if (!categories || !products) {
    return (
      <div className="note error">
        서버에 연결할 수 없습니다. <code>run.bat</code>으로 서버를 먼저 켠 뒤 새로 고쳐 주세요.
      </div>
    );
  }
  const filtered = Boolean(cat || q);

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
        <input name="q" defaultValue={q ?? ''} placeholder="막걸리, 누룩, 술잔" aria-label="상품 이름으로 찾기" />
        <button className="btn" type="submit">찾기</button>
      </form>

      <nav className="chips" aria-label="분류">
        <Link className="chip" href="/" aria-current={!cat}>전부</Link>
        {categories.map((c) => (
          <Link key={c.id} className="chip" href={'/?cat=' + c.id} aria-current={cat === String(c.id)}>
            {c.name}
          </Link>
        ))}
      </nav>

      {products.length === 0 ? (
        <div className="sheet" style={{ marginTop: 22 }}>
          <h3>찾는 물건이 아직 장에 없습니다</h3>
          <p>다른 이름으로 찾아보거나 <Link href="/">전체 좌판</Link>을 둘러보세요.</p>
        </div>
      ) : (
        <div className="stalls">
          {products.map((p) => <ProductCard key={p.id} p={p} />)}
        </div>
      )}
    </>
  );
}
