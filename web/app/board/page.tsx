'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useMe } from '@/components/Me';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { PostSummary } from '@/lib/types';
import { Loading } from '@/components/Loading';

export default function Board() {
  const { me } = useMe();
  const [posts, setPosts] = useState<PostSummary[] | null>(null);
  useEffect(() => {
    api<PostSummary[]>('/posts').then(setPosts).catch(() => setPosts([]));
  }, []);
  return (
    <>
      <div className="spread title">
        <div><h1>이야기</h1></div>
        {me?.user && <Link className="btn" href="/board/new">글쓰기</Link>}
      </div>
      <p>술을 빚은 기록과 요령을 나누는 곳입니다. 술을 사고파는 글은 올릴 수 없습니다.</p>
      {!posts ? <Loading kind="table" /> : posts.length === 0 ? (
        <div className="sheet"><h3>아직 글이 없습니다</h3><p>첫 이야기를 남겨 보세요.</p></div>
      ) : (
        <div className="sheet">
          <table>
            <thead><tr><th>제목</th><th>글쓴이</th><th>날짜</th></tr></thead>
            <tbody>
              {posts.map((p) => (
                <tr key={p.id}><td><Link href={'/board/' + p.id}>{p.title}</Link></td><td>{p.authorName}</td><td className="muted small">{when(p.createdAt, false)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
