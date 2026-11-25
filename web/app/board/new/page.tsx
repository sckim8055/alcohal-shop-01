'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { NeedLogin, Note, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import type { Post } from '@/lib/types';

export default function NewPost() {
  const router = useRouter();
  const act = useAction();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    let post: Post | null = null;
    const ok = await act.run(async () => {
      post = await api<Post>('/posts', { method: 'POST', body: { title, body } });
    });
    if (ok && post) router.push('/board/' + (post as Post).id);
  }

  return (
    <NeedLogin>
      <div className="title"><h1>글쓰기</h1></div>
      <Note kind="error">{act.error}</Note>
      <form className="sheet" onSubmit={submit}>
        <div className="field">
          <label htmlFor="title">제목</label>
          <input id="title" required maxLength={100} value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="body">내용</label>
          <textarea id="body" required maxLength={5000} rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
        </div>
        <button className="btn" disabled={act.busy}>올리기</button>
      </form>
    </NeedLogin>
  );
}
