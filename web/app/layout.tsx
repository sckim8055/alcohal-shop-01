import type { Metadata } from 'next';
import '@fontsource/do-hyeon';
import '@fontsource/gowun-dodum';
import '@fontsource/gaegu/700.css';
import './globals.css';
import { Footer, Header } from '@/components/Chrome';
import { MeProvider } from '@/components/Me';
import { NavProgress } from '@/components/NavProgress';
import { Suspense } from 'react';

export const metadata: Metadata = {
  title: { default: '술장터 — 술과 술에 관한 모든 것', template: '%s | 술장터' },
  description: '전통주와 누룩, 발효조, 술잔, 술 빚기 수업까지. 술과 술에 관한 모든 것을 사고파는 장터.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        {/* 주소의 검색어를 읽는 부품이라 Suspense 로 감싼다(감싸지 않으면 모든 화면이 서버에서 미리 그려지지 않는다). */}
        <Suspense fallback={null}><NavProgress /></Suspense>
        <MeProvider>
          <Header />
          <main className="wrap">{children}</main>
          <Footer />
        </MeProvider>
      </body>
    </html>
  );
}
