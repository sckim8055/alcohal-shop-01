import type { Metadata } from 'next';
import '@fontsource/do-hyeon';
import '@fontsource/gowun-dodum';
import '@fontsource/gaegu/700.css';
import './globals.css';
import { Footer, Header } from '@/components/Chrome';
import { MeProvider } from '@/components/Me';

export const metadata: Metadata = {
  title: { default: '술장터 — 술과 술에 관한 모든 것', template: '%s | 술장터' },
  description: '전통주와 누룩, 발효조, 술잔, 술 빚기 수업까지. 술과 술에 관한 모든 것을 사고파는 장터.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <MeProvider>
          <Header />
          <main className="wrap">{children}</main>
          <Footer />
        </MeProvider>
      </body>
    </html>
  );
}
