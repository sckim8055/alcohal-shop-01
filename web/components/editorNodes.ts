import { Node, mergeAttributes } from '@tiptap/core';
import { OWN_VIDEO } from '@/lib/richdoc';

// 편집기에 따로 만든 블록 세 가지: 직접 올린 동영상, 사진 여러 장 묶음, 강조 상자.
// 각 블록은 HTML 을 붙여 넣을 때도 알아볼 수 있게 parseHTML 규칙을 둔다.

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    callout: { toggleCallout: () => ReturnType };
  }
}

/** 이 사이트에 올린 동영상. 다른 사이트 동영상 주소는 받지 않는다(유튜브는 따로 있다). */
export const Video = Node.create({
  name: 'video',
  group: 'block',
  atom: true,
  draggable: true,
  addAttributes() {
    return { src: { default: null } };
  },
  parseHTML() {
    const own = (src: string | null) => (src && OWN_VIDEO.test(src) ? { src } : false);
    return [
      { tag: 'video[src]', getAttrs: (el) => own((el as HTMLElement).getAttribute('src')) },
      { tag: 'video', getAttrs: (el) => own((el as HTMLElement).querySelector('source')?.getAttribute('src') ?? null) },
    ];
  },
  renderHTML({ HTMLAttributes }) {
    return ['video', mergeAttributes(HTMLAttributes, { controls: 'true', preload: 'metadata', playsinline: 'true' })];
  },
});

/** 사진 여러 장을 나란히(grid) 또는 옆으로 넘겨 보게(slide) 묶는다. */
export const ImageRow = Node.create({
  name: 'imageRow',
  group: 'block',
  content: 'image+',
  draggable: true,
  addAttributes() {
    return {
      layout: {
        default: 'grid',
        parseHTML: (el) => (el.getAttribute('data-layout') === 'slide' ? 'slide' : 'grid'),
        renderHTML: (a) => ({ 'data-layout': a.layout }),
      },
    };
  },
  parseHTML() {
    return [{ tag: 'div[data-type="image-row"]' }];
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-type': 'image-row', class: 'image-row' }), 0];
  },
});

/** 눈에 띄게 할 안내 글을 감싸는 상자 */
export const Callout = Node.create({
  name: 'callout',
  group: 'block',
  content: 'paragraph+',
  defining: true,
  parseHTML() {
    return [{ tag: 'aside[data-type="callout"]' }];
  },
  renderHTML({ HTMLAttributes }) {
    return ['aside', mergeAttributes(HTMLAttributes, { 'data-type': 'callout', class: 'callout' }), 0];
  },
  addCommands() {
    return {
      toggleCallout:
        () =>
        ({ editor, commands }) =>
          editor.isActive('callout') ? commands.lift('callout') : commands.wrapIn('callout'),
    };
  },
});
