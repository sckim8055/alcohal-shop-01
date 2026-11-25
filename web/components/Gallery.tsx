'use client';

import { useState } from 'react';
import type { Media } from '@/lib/types';
import { Art } from './Art';

/** 상품 상세의 사진·동영상 보기. 아래 작은 그림을 누르면 큰 화면이 바뀐다. */
export function Gallery({ media, category, name }: { media: Media[]; category: string; name: string }) {
  const [at, setAt] = useState(0);
  if (media.length === 0) return <div className="art"><Art category={category} /></div>;
  const m = media[Math.min(at, media.length - 1)];
  return (
    <div>
      <div className="art stage">
        {m.kind === 'image' && <img src={m.url} alt={name} />}
        {m.kind === 'video' && <video src={m.url} controls preload="metadata" />}
        {m.kind === 'youtube' && (
          <iframe src={`https://www.youtube-nocookie.com/embed/${m.url}`} title={name + ' 동영상'} loading="lazy"
            allow="accelerometer; encrypted-media; gyroscope; picture-in-picture" allowFullScreen referrerPolicy="strict-origin-when-cross-origin" />
        )}
      </div>
      {media.length > 1 && (
        <div className="thumbs">
          {media.map((x, i) => (
            <button key={x.kind + x.url} type="button" aria-current={i === at} aria-label={(i + 1) + '번째 ' + (x.kind === 'image' ? '사진' : '동영상')} onClick={() => setAt(i)}>
              {x.kind === 'image' && <img src={x.url} alt="" />}
              {x.kind === 'video' && <span className="play">동영상</span>}
              {x.kind === 'youtube' && <img src={`https://i.ytimg.com/vi/${x.url}/default.jpg`} alt="" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
