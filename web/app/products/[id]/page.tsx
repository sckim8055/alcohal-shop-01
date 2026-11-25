import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Art } from '@/components/Art';
import { won } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { Product } from '@/lib/types';
import { AddToCart } from './AddToCart';

export const dynamic = 'force-dynamic';

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const p = await serverGet<Product>('/products/' + encodeURIComponent(id));
  return p ? { title: p.name, description: p.description } : { title: '상품을 찾을 수 없습니다' };
}

export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const p = await serverGet<Product>('/products/' + encodeURIComponent(id));
  if (!p) notFound();
  const onSale = p.currentPrice < p.price;

  return (
    <div className="detail">
      <div className="art"><Art category={p.categoryName} /></div>
      <div className="stack">
        <div>
          <p className="muted small">{p.categoryName}</p>
          <h1 style={{ fontSize: '2.4rem' }}>{p.name}</h1>
        </div>
        <div>
          <span className="tag big">
            {onSale && <s>{won(p.price)}</s>}
            {won(p.currentPrice)}
          </span>
        </div>
        <p>{p.description}</p>
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
  );
}
