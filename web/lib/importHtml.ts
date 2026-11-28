import { OWN_IMAGE, OUTSIDE_IMAGE, youtubeId } from './richdoc';

// 다른 몰(쿠팡, 스마트스토어 등)에 쓰던 상세설명 HTML 을 편집기에 넣기 전에 정리한다.
// 여기서는 위험한 것을 지우고 주소를 고치기만 한다. 실제로 어떤 블록이 될지는 편집기가 정하며,
// 편집기가 모르는 모양(겹친 배치, 글꼴, 테두리 등)은 그때 빠진다. 서버도 결과를 한 번 더 검사한다.
// 브라우저에서만 쓴다(DOMParser).

export type ImportReport = { images: number; droppedImages: number; youtube: number; droppedFrames: number; droppedVideos: number };

export function cleanHtml(html: string): { html: string; report: ImportReport } {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const report: ImportReport = { images: 0, droppedImages: 0, youtube: 0, droppedFrames: 0, droppedVideos: 0 };

  doc.querySelectorAll('script, style, noscript, template, form, input, button, select, textarea, object, embed, link, meta, base, svg, canvas, map, area').forEach((el) => el.remove());

  // 편집기 제목은 큰 제목(h2)과 작은 제목(h3) 두 가지뿐이라, 나머지 제목은 가까운 쪽으로 바꾼다(그대로 두면 일반 글이 된다).
  doc.querySelectorAll('h1, h4, h5, h6').forEach((h) => {
    const to = doc.createElement(h.tagName === 'H1' ? 'h2' : 'h3');
    to.innerHTML = h.innerHTML;
    h.replaceWith(to);
  });

  doc.querySelectorAll('img').forEach((img) => {
    // 상세페이지는 화면에 보일 때 불러오려고 진짜 주소를 data-src 같은 곳에 두는 경우가 많다.
    let src = img.getAttribute('data-src') || img.getAttribute('data-original') || img.getAttribute('data-lazy-src') || img.getAttribute('src') || '';
    src = src.trim();
    if (src.startsWith('//')) src = 'https:' + src;
    else if (src.startsWith('http://')) src = 'https://' + src.slice(7);
    if (OWN_IMAGE.test(src) || OUTSIDE_IMAGE.test(src)) {
      // 편집기가 알아보는 속성만 남긴다.
      const alt = img.getAttribute('alt') ?? '';
      for (const a of Array.from(img.attributes)) img.removeAttribute(a.name);
      img.setAttribute('src', src);
      if (alt) img.setAttribute('alt', alt.slice(0, 200));
      report.images++;
    } else {
      // data: 로 박아 넣은 사진, 주소가 없는 사진은 가져오지 않는다.
      img.remove();
      report.droppedImages++;
    }
  });

  doc.querySelectorAll('iframe').forEach((f) => {
    const id = youtubeId(f.getAttribute('src') ?? '') ?? youtubeId('https:' + (f.getAttribute('src') ?? ''));
    if (id) {
      // 편집기의 유튜브 블록은 <div data-youtube-video><iframe> 모양만 알아본다.
      const box = doc.createElement('div');
      box.setAttribute('data-youtube-video', '');
      const keep = doc.createElement('iframe');
      keep.setAttribute('src', 'https://www.youtube.com/embed/' + id);
      box.appendChild(keep);
      f.replaceWith(box);
      report.youtube++;
    } else {
      f.remove();
      report.droppedFrames++;
    }
  });

  doc.querySelectorAll('video').forEach((v) => {
    // 다른 사이트 동영상은 넣을 수 없다. 링크로 바꿔 두어 판매자가 직접 올리게 한다.
    const src = v.getAttribute('src') || v.querySelector('source')?.getAttribute('src') || '';
    if (/^https?:\/\//.test(src)) {
      const p = doc.createElement('p');
      const a = doc.createElement('a');
      a.setAttribute('href', src);
      a.textContent = '동영상 보기 (원래 사이트)';
      p.appendChild(a);
      v.replaceWith(p);
    } else {
      v.remove();
    }
    report.droppedVideos++;
  });

  doc.querySelectorAll('a[href]').forEach((a) => {
    const href = (a.getAttribute('href') ?? '').trim();
    if (!/^https?:\/\//i.test(href)) a.removeAttribute('href');
  });

  return { html: doc.body.innerHTML, report };
}
