'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { Note } from '@/components/Bits';
import { SalesChart } from '@/components/SalesChart';
import { api } from '@/lib/api';
import { day, won } from '@/lib/format';
import type { SellerStats } from '@/lib/types';
import { Loading } from '@/components/Loading';

const PERIODS = [7, 30, 90] as const;

export default function SellerStatsPage() {
  const [days, setDays] = useState<number>(30);
  const [s, setS] = useState<SellerStats | null>(null);
  const [failed, setFailed] = useState('');
  useEffect(() => {
    setS(null);
    setFailed('');
    api<SellerStats>('/seller/stats?days=' + days).then(setS).catch((e) => setFailed(e.message));
  }, [days]);

  const daily = s?.daily ?? [];
  const top = s?.topProducts ?? [];
  const empty = !!s && (s.totals?.orders ?? 0) === 0 && daily.every((d) => d.sales === 0);

  return (
    <>
      <div className="title"><h1>판매 통계</h1></div>
      <div className="tabs" role="group" aria-label="기간">
        {PERIODS.map((p) => <button key={p} type="button" className="chip" aria-current={days === p} onClick={() => setDays(p)}>최근 {p}일</button>)}
      </div>
      <Note kind="error">{failed}</Note>
      {!s ? (!failed && <Loading kind="table" />) : (
        <div className="stack">
          <p className="muted small" style={{ margin: 0 }}>{day(s.from)}부터 {day(s.to)}까지, 입금을 확인한 날 기준. 취소·환불된 주문은 빼고, 매출은 상품 금액만 더했습니다(배송비·쿠폰 할인 제외).</p>
          <div className="figures">
            <div className="figure"><b>{won(s.totals?.sales ?? 0)}</b>매출</div>
            <div className="figure"><b>{(s.totals?.orders ?? 0).toLocaleString('ko-KR')}건</b>주문</div>
            <div className="figure"><b>{(s.totals?.items ?? 0).toLocaleString('ko-KR')}개</b>판 상품 수</div>
          </div>

          {empty ? (
            <div className="sheet">
              <h3>이 기간에는 입금을 확인한 주문이 없습니다</h3>
              <p>구매자가 입금하면 주문 처리에서 “입금 확인”을 눌러 주세요. 확인한 날부터 여기에 매출이 쌓입니다. 더 긴 기간을 골라 볼 수도 있습니다.</p>
              <Link className="btn plain" href="/seller/orders?status=awaiting_deposit">입금을 기다리는 주문 보기</Link>
            </div>
          ) : (
            <>
              <section className="sheet" aria-labelledby="daily-title">
                <h2 id="daily-title">날짜별 매출</h2>
                <p className="hint" style={{ marginTop: 0 }}>막대에 마우스를 올리면 그날 매출과 주문 수가 보입니다.</p>
                <SalesChart daily={daily} />
                <details style={{ marginTop: 12 }}>
                  <summary>같은 자료를 표로 보기</summary>
                  <div className="scroll" style={{ marginTop: 8 }}>
                    <table>
                      <thead><tr><th scope="col">날짜</th><th scope="col" className="num">주문</th><th scope="col" className="num">매출</th></tr></thead>
                      <tbody>
                        {daily.map((d) => (
                          <tr key={d.date}><td>{day(d.date)}</td><td className="num">{d.orders.toLocaleString('ko-KR')}건</td><td className="num">{won(d.sales)}</td></tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              </section>

              <section className="sheet" aria-labelledby="top-title">
                <h2 id="top-title">많이 판 상품</h2>
                {top.length === 0 ? <p className="muted">이 기간에 팔린 상품이 없습니다.</p> : (
                  <div className="scroll">
                    <table>
                      <thead><tr><th scope="col" className="num">순위</th><th scope="col">상품</th><th scope="col" className="num">판 수량</th><th scope="col" className="num">매출</th></tr></thead>
                      <tbody>
                        {top.map((p, i) => (
                          <tr key={p.productId}>
                            <td className="num">{i + 1}</td>
                            <td><Link href={'/products/' + p.productId}>{p.name}</Link></td>
                            <td className="num">{p.qty.toLocaleString('ko-KR')}개</td>
                            <td className="num">{won(p.sales)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </div>
      )}
    </>
  );
}
