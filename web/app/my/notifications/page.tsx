'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { useMe } from '@/components/Me';
import { Pager } from '@/components/Pager';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Notification, Paged } from '@/lib/types';
import { Loading } from '@/components/Loading';

export default function Notifications() {
  const { refresh } = useMe();
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Notification> | null>(null);
  const load = useCallback(() => {
    api<Paged<Notification>>('/notifications?page=' + page).then(setData).catch(() => setData({ items: [], page: 1, size: 20, total: 0 }));
  }, [page]);
  useEffect(load, [load]);

  async function readAll() {
    await api('/notifications/read-all', { method: 'POST' }).catch(() => null);
    load();
    refresh();
  }
  async function open(n: Notification) {
    if (!n.readAt) {
      await api('/notifications/' + n.id + '/read', { method: 'POST' }).catch(() => null);
      refresh();
    }
  }

  return (
    <>
      <div className="spread title">
        <div><h1>알림</h1></div>
        {data && data.items.some((n) => !n.readAt) && <button className="btn plain" type="button" onClick={readAll}>모두 읽음으로</button>}
      </div>
      {!data ? <Loading /> : data.items.length === 0 ? (
        <div className="sheet"><h3>아직 알림이 없습니다</h3><p>주문, 입금 확인, 발송 같은 소식이 여기에 쌓입니다.</p></div>
      ) : (
        <div className="sheet">
          <ul className="notice-list">
            {data.items.map((n) => (
              <li key={n.id} className={n.readAt ? '' : 'unread'}>
                <strong>{n.link ? <Link href={n.link} onClick={() => open(n)}>{n.title}</Link> : n.title}</strong>
                <div>{n.body}</div>
                <div className="muted small">{when(n.createdAt)}</div>
              </li>
            ))}
          </ul>
          <Pager page={data.page} size={data.size} total={data.total} onPage={setPage} />
        </div>
      )}
      <p className="hint" style={{ marginTop: 12 }}>
        돈과 배송에 관한 알림은 메일로도 보낼 준비가 되어 있습니다. 시범 운영 중에는 메일이 실제로 나가지 않습니다.
      </p>
    </>
  );
}
