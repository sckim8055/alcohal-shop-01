'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { AUDIENCE_LABEL, when } from '@/lib/format';
import type { Notice } from '@/lib/types';
import { Loading } from '@/components/Loading';

// 판매자 대상 공지는 로그인한 판매자에게만 보이므로 브라우저에서(로그인 정보를 실어) 불러온다.
export default function NoticePage() {
  const { id } = useParams<{ id: string }>();
  const { me, loading } = useMe();
  const [n, setN] = useState<Notice | null>(null);
  const [missing, setMissing] = useState(false);
  const userId = me?.user?.id;
  useEffect(() => {
    if (loading) return;
    setMissing(false);
    api<Notice>('/notices/' + encodeURIComponent(id)).then(setN).catch(() => setMissing(true));
  }, [id, loading, userId]);
  const back = me?.seller?.status === 'approved' && n?.audience === 'seller' ? '/seller/notices' : '/notices';

  if (missing) {
    return (
      <div className="sheet narrow">
        <h3>공지를 찾을 수 없습니다</h3>
        <p>지워졌거나 볼 수 없는 공지입니다. 판매자 공지는 판매자로 로그인해야 볼 수 있습니다.</p>
        <Link className="btn" href="/notices">공지 목록으로</Link>
      </div>
    );
  }
  if (!n) return <Loading />;
  return (
    <article className="sheet" style={{ maxWidth: 760 }}>
      <div className="row" style={{ gap: 8, marginBottom: 6 }}>
        {n.pinned && <span className="stamp">고정</span>}
        {n.audience === 'seller' && <span className="stamp blue">{AUDIENCE_LABEL.seller}</span>}
      </div>
      <h1 style={{ fontSize: '2rem' }}>{n.title}</h1>
      <p className="muted small">{when(n.createdAt)}</p>
      <p className="text">{n.body}</p>
      <Link className="btn plain small" href={back}>목록으로</Link>
    </article>
  );
}
