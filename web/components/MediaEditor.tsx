'use client';

import { useRef, useState } from 'react';
import { ApiError, uploadFile } from '@/lib/api';
import type { Media } from '@/lib/types';

const KIND: Record<Media['kind'], string> = { image: '사진', video: '동영상', youtube: '유튜브' };

export function youtubeId(input: string): string | null {
  const m = input.match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/|live\/))([A-Za-z0-9_-]{11})/);
  if (m) return m[1];
  return /^[A-Za-z0-9_-]{11}$/.test(input.trim()) ? input.trim() : null;
}

/** 상품 사진·동영상 묶음을 고친다. 맨 앞 사진이 대표 사진이 된다. */
export function MediaEditor({ value, onChange }: { value: Media[]; onChange: (m: Media[]) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [yt, setYt] = useState('');
  const firstImage = value.findIndex((m) => m.kind === 'image');

  async function pick(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (files.length === 0) return;
    setBusy(true);
    setError('');
    const added: Media[] = [];
    try {
      for (const f of files) {
        const up = await uploadFile(f);
        added.push({ kind: up.kind, url: up.url });
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '파일을 올리지 못했습니다.');
    } finally {
      if (added.length) onChange([...value, ...added]);
      setBusy(false);
    }
  }

  function addYoutube() {
    const id = youtubeId(yt);
    if (!id) {
      setError('유튜브 주소를 확인해 주세요. 예: https://youtu.be/영상번호');
      return;
    }
    setError('');
    setYt('');
    onChange([...value, { kind: 'youtube', url: id }]);
  }

  function move(i: number, by: number) {
    const j = i + by;
    if (j < 0 || j >= value.length) return;
    const next = [...value];
    [next[i], next[j]] = [next[j], next[i]];
    onChange(next);
  }

  return (
    <div>
      {error && <div className="note error">{error}</div>}
      {value.length > 0 && (
        <ul className="media-list">
          {value.map((m, i) => (
            <li key={m.kind + m.url}>
              <div className="thumb">
                {m.kind === 'image' && <img src={m.url} alt="" />}
                {m.kind === 'video' && <video src={m.url} muted preload="metadata" />}
                {m.kind === 'youtube' && <img src={`https://i.ytimg.com/vi/${m.url}/mqdefault.jpg`} alt="" />}
              </div>
              <div className="small">{KIND[m.kind]}{i === firstImage && <strong> (대표)</strong>}</div>
              <div className="row" style={{ gap: 4 }}>
                <button type="button" className="btn plain small" disabled={i === 0} onClick={() => move(i, -1)} aria-label="앞으로">앞</button>
                <button type="button" className="btn plain small" disabled={i === value.length - 1} onClick={() => move(i, 1)} aria-label="뒤로">뒤</button>
                <button type="button" className="btn plain small" onClick={() => onChange(value.filter((_, k) => k !== i))}>빼기</button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <input ref={fileRef} type="file" hidden multiple accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/webm" onChange={pick} />
      <div className="inline-form">
        <button type="button" className="btn plain" disabled={busy} onClick={() => fileRef.current?.click()}>{busy ? '올리는 중' : '사진·동영상 올리기'}</button>
        <input placeholder="유튜브 주소" aria-label="유튜브 주소" value={yt} onChange={(e) => setYt(e.target.value)} />
        <button type="button" className="btn plain" onClick={addYoutube}>유튜브 추가</button>
      </div>
      <p className="hint">사진은 JPG·PNG·GIF·WEBP 5MB 이하, 동영상은 MP4·WEBM 100MB 이하. 모두 합쳐 12개까지. 맨 앞 사진이 대표 사진입니다.</p>
    </div>
  );
}
