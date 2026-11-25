'use client';

import Image from '@tiptap/extension-image';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useRef, useState } from 'react';
import { ApiError, uploadFile } from '@/lib/api';

/** 상품 설명 편집기. 결과는 HTML 이 아니라 문서(JSON)로 내보낸다. */
export function RichEditor({ initial, onChange }: { initial: string | null; onChange: (doc: object | null) => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const [, tick] = useState(0);
  let start: object | undefined;
  try {
    start = initial ? JSON.parse(initial) : undefined;
  } catch {
    start = undefined;
  }
  const editor = useEditor({
    immediatelyRender: false,
    extensions: [StarterKit.configure({ heading: { levels: [2, 3] }, code: false, codeBlock: false, link: { openOnClick: false } }), Image],
    content: start,
    editorProps: { attributes: { class: 'rich editor-area', 'aria-label': '상품 설명' } },
    onUpdate: ({ editor: e }) => onChange(e.isEmpty ? null : e.getJSON()),
    onSelectionUpdate: () => tick((n) => n + 1),
  });
  if (!editor) return <div className="editor"><div className="editor-area muted">편집기를 여는 중입니다.</div></div>;

  const btn = (label: string, active: boolean, run: () => void, title?: string) => (
    <button type="button" className="tool" aria-pressed={active} title={title ?? label} onClick={() => { run(); tick((n) => n + 1); }}>{label}</button>
  );
  const chain = () => editor.chain().focus();

  function link() {
    const prev = editor!.getAttributes('link').href as string | undefined;
    const url = window.prompt('연결할 주소 (https:// 로 시작). 비우면 링크를 지웁니다.', prev ?? 'https://');
    if (url === null) return;
    if (url.trim() === '' || url.trim() === 'https://') chain().unsetLink().run();
    else if (/^https?:\/\//i.test(url.trim())) chain().extendMarkRange('link').setLink({ href: url.trim() }).run();
    else setError('주소는 https:// 로 시작해야 합니다.');
  }

  async function addImage(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    try {
      const up = await uploadFile(file);
      if (up.kind !== 'image') {
        setError('설명 안에는 사진만 넣을 수 있습니다. 동영상은 위의 사진·동영상 칸에 올려 주세요.');
        return;
      }
      chain().setImage({ src: up.url }).run();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '사진을 올리지 못했습니다.');
    }
  }

  return (
    <div className="editor">
      <div className="toolbar" role="toolbar" aria-label="글 꾸미기">
        {btn('큰 제목', editor.isActive('heading', { level: 2 }), () => chain().toggleHeading({ level: 2 }).run())}
        {btn('작은 제목', editor.isActive('heading', { level: 3 }), () => chain().toggleHeading({ level: 3 }).run())}
        <span className="sep" />
        {btn('굵게', editor.isActive('bold'), () => chain().toggleBold().run())}
        {btn('기울임', editor.isActive('italic'), () => chain().toggleItalic().run())}
        {btn('밑줄', editor.isActive('underline'), () => chain().toggleUnderline().run())}
        {btn('취소선', editor.isActive('strike'), () => chain().toggleStrike().run())}
        <span className="sep" />
        {btn('점 목록', editor.isActive('bulletList'), () => chain().toggleBulletList().run())}
        {btn('번호 목록', editor.isActive('orderedList'), () => chain().toggleOrderedList().run())}
        {btn('인용', editor.isActive('blockquote'), () => chain().toggleBlockquote().run())}
        {btn('가로줄', false, () => chain().setHorizontalRule().run())}
        <span className="sep" />
        {btn('링크', editor.isActive('link'), link)}
        {btn('사진 넣기', false, () => fileRef.current?.click())}
        <span className="sep" />
        {btn('되돌리기', false, () => chain().undo().run())}
        {btn('다시 하기', false, () => chain().redo().run())}
      </div>
      <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={addImage} />
      {error && <div className="note error" style={{ margin: 0, borderLeft: 0, borderRight: 0 }}>{error}</div>}
      <EditorContent editor={editor} />
    </div>
  );
}
