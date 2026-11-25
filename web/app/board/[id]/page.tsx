'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { when } from '@/lib/format';
import type { Post } from '@/lib/types';

export default function PostPage() {
  const { id } = useParams<{ id: string }>();
  const [post, setPost] = useState<Post | null>(null);
  const [missing, setMissing] = useState(false);
  useEffect(() => {
    api<Post>('/posts/' + id).then(setPost).catch(() => setMissing(true));
  }, [id]);
  if (missing) return <div className="sheet"><h3>글을 찾을 수 없습니다</h3><Link className="btn" href="/board">이야기로</Link></div>;
  if (!post) return <p className="muted">불러오는 중입니다.</p>;
  return (
    <article className="sheet" style={{ maxWidth: 760 }}>
      <h1 style={{ fontSize: '2rem' }}>{post.title}</h1>
      <p className="muted small">{post.authorName}, {when(post.createdAt)}</p>
      <p style={{ whiteSpace: 'pre-wrap' }}>{post.body}</p>
      <Link className="btn plain small" href="/board">목록으로</Link>
    </article>
  );
}
