'use client';

import { useEffect, useRef, useState } from 'react';
import { day, won } from '@/lib/format';

type Day = { date: string; orders: number; sales: number };

/** 축 눈금: 1·2·2.5·5 × 10^k 단위로 3~5칸이 되게 나눈다. */
function niceStep(max: number, want = 4): number {
  if (max <= 0) return 1;
  const raw = max / want;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 2.5, 5, 10]) if (raw <= m * mag) return m * mag;
  return 10 * mag;
}

/** 눈금 글자: 1만 원 이상은 '만원' 단위로 줄인다. */
function shortWon(n: number): string {
  if (n === 0) return '0';
  if (n >= 100000000) return (n / 100000000).toLocaleString('ko-KR', { maximumFractionDigits: 2 }) + '억원';
  if (n >= 10000) return (n / 10000).toLocaleString('ko-KR', { maximumFractionDigits: 1 }) + '만원';
  return n.toLocaleString('ko-KR') + '원';
}

const md = (d: string) => { const [, m, dd] = d.slice(0, 10).split('-').map(Number); return `${m}/${dd}`; };

/** 날짜별 매출 막대 그림. 차트 꾸러미 없이 SVG 로 직접 그리고, 실제 화면 너비에 맞춰 다시 그린다. */
export function SalesChart({ daily }: { daily: Day[] }) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const measure = () => setWidth(Math.max(280, Math.floor(el.clientWidth)));
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const height = 260;
  const pad = { top: 14, right: 10, bottom: 30, left: 64 };
  const plotW = width - pad.left - pad.right;
  const plotH = height - pad.top - pad.bottom;
  const max = Math.max(0, ...daily.map((d) => d.sales));
  const step = niceStep(max);
  const top = Math.max(step, Math.ceil(max / step) * step);
  const ticks: number[] = [];
  for (let v = 0; v <= top + step / 2; v += step) ticks.push(v);
  const n = daily.length;
  const slot = n > 0 ? plotW / n : plotW;
  // 막대 사이에 2px 이상 틈을 둔다. 90일이면 막대가 가늘어진다.
  const gap = Math.max(2, slot * 0.25);
  const barW = Math.max(1, slot - gap);
  const y = (v: number) => pad.top + plotH - (v / top) * plotH;
  // 날짜 글자가 겹치지 않게 대략 56px 마다 하나씩, 마지막 날은 늘 쓴다.
  const every = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(plotW / 56))));
  const total = daily.reduce((s, d) => s + d.sales, 0);

  return (
    <div ref={box} className="chart">
      <svg width={width} height={height} role="img"
        aria-label={`${day(daily[0]?.date)}부터 ${day(daily[n - 1]?.date)}까지 날짜별 매출 막대 그림. 합계 ${won(total)}, 가장 많은 날 ${won(max)}. 같은 자료를 아래 표로 볼 수 있습니다.`}>
        {ticks.map((t) => (
          <g key={t}>
            <line className="grid" x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} />
            <text className="axis" x={pad.left - 8} y={y(t)} dy="0.35em" textAnchor="end">{shortWon(t)}</text>
          </g>
        ))}
        {daily.map((d, i) => {
          const x = pad.left + i * slot + gap / 2;
          const h = pad.top + plotH - y(d.sales);
          return (
            <g key={d.date} className="col">
              <title>{`${day(d.date)}: ${won(d.sales)}, 주문 ${d.orders.toLocaleString('ko-KR')}건`}</title>
              {/* 매출이 없는 날도 마우스를 올리면 날짜가 보이도록 칸 전체를 받는 투명한 칸을 둔다. */}
              <rect className="hit" x={pad.left + i * slot} y={pad.top} width={slot} height={plotH} />
              {d.sales > 0 && <rect className="bar" x={x} y={y(d.sales)} width={barW} height={Math.max(1, h)} />}
              {((n - 1 - i) % every === 0) && (
                <text className="axis" x={pad.left + i * slot + slot / 2} y={height - pad.bottom + 18} textAnchor="middle">{md(d.date)}</text>
              )}
            </g>
          );
        })}
        <line className="base" x1={pad.left} x2={width - pad.right} y1={pad.top + plotH} y2={pad.top + plotH} />
      </svg>
    </div>
  );
}
