import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Gallery } from '@/components/Gallery';
import { RichView } from '@/components/RichView';
import { won } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { ProductDetail } from '@/lib/types';
import { AddToCart } from './AddToCart';

export const dynamic = 'force-dynamic';

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const d = await serverGet<ProductDetail>('/products/' + encodeURIComponent(id));
  return d ? { title: d.product.name, description: d.product.description.slice(0, 150) } : { title: '상품을 찾을 수 없습니다' };
}

export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const d = await serverGet<ProductDetail>('/products/' + encodeURIComponent(id));
  if (!d) notFound();
  const p = d.product;
  const onSale = p.currentPrice < p.price;
  const specs: [string, string][] = [];
  specs.push(['분류', p.topCategoryName === p.categoryName ? p.categoryName : `${p.topCategoryName} > ${p.categoryName}`]);
  if (p.abv != null) specs.push(['도수', p.abv + '%']);
  if (p.volumeMl != null) specs.push(['용량', p.volumeMl >= 1000 && p.volumeMl % 100 === 0 ? p.volumeMl / 1000 + 'L' : p.volumeMl + 'ml']);
  if (p.origin) specs.push(['원재료·원산지', p.origin]);

  return (
    <div className="stack">
      <div className="detail">
        <Gallery media={d.media} category={p.topCategoryName} name={p.name} />
        <div className="stack">
          <div>
            <p className="muted small">{p.shopName}</p>
            <h1 style={{ fontSize: '2.4rem' }}>{p.name}</h1>
          </div>
          <div>
            <span className="tag big">
              {onSale && <s>{won(p.price)}</s>}
              {won(p.currentPrice)}
            </span>
          </div>
          <table className="specs">
            <tbody>
              {specs.map(([k, v]) => <tr key={k}><th scope="row">{k}</th><td>{v}</td></tr>)}
            </tbody>
          </table>
          {p.alcohol && (
            <div className="note">
              술은 19세 이상만 살 수 있습니다. 주문할 때 성인인증을 확인합니다.
            </div>
          )}
          <AddToCart productId={p.id} stock={p.stock} />
          <div className="sheet">
            <h3>{p.shopName}</h3>
            <p>{p.sellerIntro}</p>
            {p.productType === 'ticket' ? (
              <p className="small">배송 없이 판매자가 일정과 장소를 따로 안내합니다.</p>
            ) : (
              <p className="small">
                배송비 {won(p.baseShipFee)}
                {p.freeShipMin != null && <>, 이 가게에서 {won(p.freeShipMin)} 이상 사면 무료</>}
              </p>
            )}
          </div>
        </div>
      </div>
      <section className="sheet">
        <h2 style={{ marginBottom: 14 }}>상품 이야기</h2>
        <RichView doc={d.descriptionDoc} fallback={p.description} />
      </section>
    </div>
  );
}
