import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Gallery } from '@/components/Gallery';
import { RichView } from '@/components/RichView';
import { won } from '@/lib/format';
import { serverGet } from '@/lib/server';
import type { ProductDetail } from '@/lib/types';
import { AddToCart } from './AddToCart';
import { WishButton } from './WishButton';

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
  // 전자상거래법상 판매자 정보. 빈 값은 줄을 뺀다.
  const si = d.sellerInfo;
  const sellerRows: [string, string][] = si
    ? ([['상호', si.shopName], ['대표자', si.ceoName], ['사업자등록번호', si.bizNo], ['통신판매업 신고번호', si.mailOrderNo],
        ['주소', si.bizAddress], ['고객 전화', si.csPhone], ['반품 주소', si.returnAddress]] as [string, string | null | undefined][])
        .filter((r): r is [string, string] => typeof r[1] === 'string' && r[1].trim() !== '')
    : [];

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
          <WishButton productId={p.id} />
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
      {sellerRows.length > 0 && (
        <section className="sheet" id="seller-info" aria-labelledby="seller-info-title">
          <h2 id="seller-info-title" style={{ marginBottom: 14 }}>판매자 정보</h2>
          <div className="scroll">
            <table className="specs">
              <tbody>
                {sellerRows.map(([k, v]) => <tr key={k}><th scope="row">{k}</th><td>{v}</td></tr>)}
              </tbody>
            </table>
          </div>
          <p className="hint" style={{ marginTop: 10 }}>대금은 판매자에게 직접 보내며, 교환·반품·환불은 판매자가 처리합니다.</p>
        </section>
      )}
    </div>
  );
}
