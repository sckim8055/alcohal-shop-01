import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ProductCard } from '@/components/Bits';
import { Pager } from '@/components/Pager';
import { Stars } from '@/components/Stars';
import { rating1 } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { ShopPage } from '@/lib/types';

export const dynamic = 'force-dynamic';

type Props = { params: Promise<{ id: string }>; searchParams: Promise<{ page?: string }> };

const load = (id: string, page?: string) =>
  serverGet<ShopPage>('/shops/' + encodeURIComponent(id) + (page && /^\d+$/.test(page) ? '?page=' + page : ''));

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const d = await load(id);
  return d?.shop ? { title: d.shop.shopName, description: (d.shop.intro ?? '').slice(0, 150) } : { title: '가게를 찾을 수 없습니다' };
}

export default async function ShopScreen({ params, searchParams }: Props) {
  const { id } = await params;
  const { page } = await searchParams;
  const d = await load(id, page);
  if (!d?.shop) notFound();
  const s = d.shop;
  const products = d.products ?? { items: [], page: 1, size: 24, total: 0 };
  // 전자상거래법상 판매자 정보. 빈 값은 줄을 뺀다. 계좌 정보는 서버가 보내지 않는다.
  const rows = ([['상호', s.shopName], ['대표자', s.ceoName], ['사업자등록번호', s.bizNo], ['통신판매업 신고번호', s.mailOrderNo], ['고객 전화', s.csPhone]] as [string, string | null | undefined][])
    .filter((r): r is [string, string] => typeof r[1] === 'string' && r[1].trim() !== '');

  return (
    <div className="stack">
      <header className="shop-head">
        <h1 className="sign">{s.shopName}</h1>
        {s.intro && <p className="text" style={{ marginTop: 18, maxWidth: '40em' }}>{s.intro}</p>}
        <p className="row" style={{ gap: 16 }}>
          {s.reviewCount > 0 && s.ratingAvg != null
            ? <span className="rate"><Stars value={s.ratingAvg} /> {rating1(s.ratingAvg)} <span className="muted">(리뷰 {s.reviewCount.toLocaleString('ko-KR')}개)</span></span>
            : <span className="muted">아직 리뷰가 없습니다</span>}
          <span>판매 중인 상품 {s.productCount.toLocaleString('ko-KR')}개</span>
        </p>
      </header>

      {products.items.length === 0 ? (
        <div className="sheet">
          <h3>지금 판매 중인 상품이 없습니다</h3>
          <p>다른 가게의 좌판을 둘러보세요.</p>
          <Link className="btn" href="/">장 보러 가기</Link>
        </div>
      ) : (
        <section aria-label={s.shopName + ' 상품'}>
          <div className="stalls">
            {products.items.map((p) => <ProductCard key={p.id} p={p} />)}
          </div>
          <Pager page={products.page} size={products.size} total={products.total}
            href={(p) => '/shops/' + s.id + (p > 1 ? '?page=' + p : '')} />
        </section>
      )}

      {rows.length > 0 && (
        <section className="sheet" aria-labelledby="shop-info-title">
          <h2 id="shop-info-title" style={{ marginBottom: 14 }}>판매자 정보</h2>
          <div className="scroll">
            <table className="specs">
              <tbody>
                {rows.map(([k, v]) => <tr key={k}><th scope="row">{k}</th><td>{v}</td></tr>)}
              </tbody>
            </table>
          </div>
          <p className="hint" style={{ marginTop: 10 }}>대금은 판매자에게 직접 보내며, 교환·반품·환불은 판매자가 처리합니다.</p>
        </section>
      )}
    </div>
  );
}
