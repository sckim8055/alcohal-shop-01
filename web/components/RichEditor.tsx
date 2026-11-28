'use client';

import type { Editor } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import { TableKit } from '@tiptap/extension-table';
import TextAlign from '@tiptap/extension-text-align';
import { Color, FontSize, TextStyle } from '@tiptap/extension-text-style';
import Youtube from '@tiptap/extension-youtube';
import { Selection } from '@tiptap/pm/state';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useRef, useState } from 'react';
import { ApiError, uploadFile } from '@/lib/api';
import { cleanHtml } from '@/lib/importHtml';
import { COLORS, SIZES, isOutsideImage, youtubeId } from '@/lib/richdoc';
import { Callout, ImageRow, Video } from './editorNodes';
import { RichView } from './RichView';

/** 상품 설명 편집기. 결과는 HTML 이 아니라 문서(JSON)로 내보낸다. */
export function RichEditor({ initial, onChange }: { initial: string | null; onChange: (doc: object | null) => void }) {
  const imageRef = useRef<HTMLInputElement>(null);
  const imagesRef = useRef<HTMLInputElement>(null);
  const videoRef = useRef<HTMLInputElement>(null);
  const editorRef = useRef<Editor | null>(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState('');
  const [preview, setPreview] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importText, setImportText] = useState('');
  const [, tick] = useState(0);
  let start: object | undefined;
  try {
    start = initial ? JSON.parse(initial) : undefined;
  } catch {
    start = undefined;
  }

  // 사진 파일을 끌어 놓거나 붙여 넣으면 올린 뒤 그 자리에 넣는다.
  function takeFiles(files: FileList | null | undefined): boolean {
    const list = Array.from(files ?? []).filter((f) => f.type.startsWith('image/'));
    if (list.length === 0) return false;
    void insertImages(list);
    return true;
  }

  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3] }, code: false, codeBlock: false, link: { openOnClick: false } }),
      // 다른 사이트 사진을 불러올 때 이 사이트 주소를 넘기지 않는다(넘기면 막는 사이트가 많다).
      Image.configure({ HTMLAttributes: { referrerpolicy: 'no-referrer' } }),
      TextStyle,
      Color,
      FontSize,
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      TableKit.configure({ table: { resizable: false } }),
      Youtube.configure({ nocookie: true, width: 640, height: 360 }),
      Video,
      ImageRow,
      Callout,
    ],
    content: start,
    editorProps: {
      attributes: { class: 'rich editor-area', 'aria-label': '상품 설명' },
      handlePaste: (_view, event) => takeFiles(event.clipboardData?.files),
      handleDrop: (_view, event) => takeFiles((event as DragEvent).dataTransfer?.files),
      // 다른 곳에서 복사한 HTML 도 HTML 붙여 넣기와 같은 정리를 거친다.
      transformPastedHTML: (html) => cleanHtml(html).html,
    },
    onCreate: ({ editor: e }) => { editorRef.current = e as unknown as Editor; },
    onUpdate: ({ editor: e }) => { onChange(e.isEmpty ? null : trimEmpty(e.getJSON())); tick((n) => n + 1); },
    onSelectionUpdate: () => tick((n) => n + 1),
  });
  if (!editor) return <div className="editor"><div className="editor-area muted">편집기를 여는 중입니다.</div></div>;

  const chain = () => editor.chain().focus();
  const btn = (label: string, active: boolean, run: () => void, title?: string, disabled?: boolean) => (
    // 누를 때 편집기에서 포커스가 빠지지 않게 한다(빠졌다 돌아오면 고른 글의 선택이 꼬인다).
    <button type="button" className="tool" aria-pressed={active} title={title ?? label} disabled={disabled}
      onMouseDown={(e) => e.preventDefault()} onClick={() => { run(); tick((n) => n + 1); }}>{label}</button>
  );

  async function upload(file: File): Promise<{ url: string; kind: string; compressing?: boolean } | null> {
    try {
      return await uploadFile(file);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '파일을 올리지 못했습니다.');
      return null;
    }
  }

  async function insertImages(files: File[]) {
    const ed = editorRef.current ?? editor;
    if (!ed) return;
    setError('');
    setBusy(`사진 ${files.length}장을 올리는 중입니다.`);
    const srcs: string[] = [];
    for (const f of files) {
      const up = await upload(f);
      if (up && up.kind === 'image') srcs.push(up.url);
    }
    setBusy('');
    if (srcs.length === 0) return;
    const images = srcs.map((src) => ({ type: 'image', attrs: { src } }));
    insertBlock(ed, images.length === 1 ? images[0] : { type: 'imageRow', attrs: { layout: 'grid' }, content: images });
  }

  async function pickImages(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (files.length > 12) { setError('한 번에 12장까지 넣을 수 있습니다.'); return; }
    if (files.length) await insertImages(files);
  }

  async function pickVideo(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    setBusy('동영상을 올리는 중입니다. 큰 파일은 몇 분 걸릴 수 있습니다.');
    const up = await upload(file);
    setBusy('');
    if (!up) return;
    if (up.kind !== 'video') { setError('동영상 파일(mp4, webm, mov)을 골라 주세요.'); return; }
    insertBlock(editor!, { type: 'video', attrs: { src: up.url } });
    if (up.compressing) setInfo('동영상 크기를 줄이는 중입니다. 저장은 지금 해도 됩니다.');
  }

  function link() {
    const prev = editor!.getAttributes('link').href as string | undefined;
    const url = window.prompt('연결할 주소 (https:// 로 시작). 비우면 링크를 지웁니다.', prev ?? 'https://');
    if (url === null) return;
    if (url.trim() === '' || url.trim() === 'https://') chain().unsetLink().run();
    else if (/^https?:\/\//i.test(url.trim())) chain().extendMarkRange('link').setLink({ href: url.trim() }).run();
    else setError('주소는 https:// 로 시작해야 합니다.');
  }

  function youtube() {
    const url = window.prompt('유튜브 동영상 주소를 붙여 넣어 주세요.', 'https://www.youtube.com/watch?v=');
    if (url === null) return;
    const id = youtubeId(url.trim());
    if (!id) { setError('유튜브 동영상 주소가 아닙니다. 예: https://www.youtube.com/watch?v=...'); return; }
    insertBlock(editor!, { type: 'youtube', attrs: { src: 'https://www.youtube.com/watch?v=' + id } });
  }

  // 지금 커서가 있는 맨 바깥 블록을 위나 아래 블록과 자리를 바꾼다.
  function move(dir: -1 | 1) {
    const { state, view } = editor!;
    const idx = state.selection.$from.index(0);
    const doc = state.doc;
    const other = idx + dir;
    if (other < 0 || other >= doc.childCount) return;
    let pos = 0;
    for (let i = 0; i < idx; i++) pos += doc.child(i).nodeSize;
    const node = doc.child(idx);
    const tr = state.tr.delete(pos, pos + node.nodeSize);
    const at = dir < 0 ? pos - doc.child(other).nodeSize : pos + doc.child(other).nodeSize;
    tr.insert(at, node);
    tr.setSelection(Selection.near(tr.doc.resolve(Math.min(at + 1, tr.doc.content.size))));
    view.dispatch(tr.scrollIntoView());
    view.focus();
  }

  function runImport() {
    const { html, report } = cleanHtml(importText);
    if (!html.trim()) { setError('가져올 내용이 없습니다.'); return; }
    const before = countImages(editor!.getJSON());
    chain().insertContentAt(topSlot(editor!), html, { parseOptions: { preserveWhitespace: false } }).run();
    const got = countImages(editor!.getJSON()) - before;
    const notes = [`사진 ${got}장${report.youtube ? `, 유튜브 ${report.youtube}개` : ''}를 가져왔습니다.`];
    if (report.droppedImages) notes.push(`주소가 없거나 파일 안에 박힌 사진 ${report.droppedImages}장은 빠졌습니다.`);
    if (report.droppedVideos) notes.push(`다른 사이트 동영상 ${report.droppedVideos}개는 넣을 수 없어 링크로 바꿨습니다. 직접 올려 주세요.`);
    if (report.droppedFrames) notes.push(`유튜브가 아닌 끼워 넣기 ${report.droppedFrames}개는 빠졌습니다.`);
    notes.push('글꼴, 겹친 배치, 테두리 같은 꾸밈은 빠질 수 있으니 휴대폰으로 보기로 확인해 주세요.');
    setInfo(notes.join(' '));
    setImportText('');
    setImportOpen(false);
  }

  const inTable = editor.isActive('table');
  const inRow = editor.isActive('imageRow');
  const align = (['left', 'center', 'right'] as const).find((a) => editor.isActive({ textAlign: a })) ?? 'left';
  const color = (editor.getAttributes('textStyle').color as string | undefined) ?? '';
  const size = (editor.getAttributes('textStyle').fontSize as string | undefined) ?? '';
  const outside = countImages(editor.getJSON(), true);

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
        <select className="tool" aria-label="글자 색" value={COLORS.some((c) => c.value === color) ? color : ''} onChange={(e) => (e.target.value ? chain().setColor(e.target.value).run() : chain().unsetColor().run())}>
          {COLORS.map((c) => <option key={c.label} value={c.value ?? ''}>{c.label}</option>)}
        </select>
        <select className="tool" aria-label="글자 크기" value={SIZES.some((s) => s.value === size) ? size : ''} onChange={(e) => (e.target.value ? chain().setFontSize(e.target.value).run() : chain().unsetFontSize().run())}>
          {SIZES.map((s) => <option key={s.label} value={s.value ?? ''}>{s.label}</option>)}
        </select>
        <span className="sep" />
        {btn('왼쪽', align === 'left', () => chain().setTextAlign('left').run(), '왼쪽 맞춤')}
        {btn('가운데', align === 'center', () => chain().setTextAlign('center').run(), '가운데 맞춤')}
        {btn('오른쪽', align === 'right', () => chain().setTextAlign('right').run(), '오른쪽 맞춤')}
        <span className="sep" />
        {btn('점 목록', editor.isActive('bulletList'), () => chain().toggleBulletList().run())}
        {btn('번호 목록', editor.isActive('orderedList'), () => chain().toggleOrderedList().run())}
        {btn('인용', editor.isActive('blockquote'), () => chain().toggleBlockquote().run())}
        {btn('강조 상자', editor.isActive('callout'), () => chain().toggleCallout().run())}
        {btn('가로줄', false, () => chain().setHorizontalRule().run())}
        {btn('링크', editor.isActive('link'), link)}
      </div>
      <div className="toolbar" role="toolbar" aria-label="넣기와 옮기기">
        {btn('사진 넣기', false, () => imageRef.current?.click())}
        {btn('사진 여러 장', false, () => imagesRef.current?.click(), '여러 장을 골라 나란히 넣기')}
        {btn('동영상 올리기', false, () => videoRef.current?.click(), '100MB 까지')}
        {btn('유튜브', false, youtube, '유튜브 동영상 넣기')}
        {btn('표 넣기', false, () => chain().insertTable({ rows: 3, cols: 2, withHeaderRow: false }).run())}
        <span className="sep" />
        {btn('위로', false, () => move(-1), '지금 블록을 위로 옮기기')}
        {btn('아래로', false, () => move(1), '지금 블록을 아래로 옮기기')}
        <span className="sep" />
        {btn('되돌리기', false, () => chain().undo().run())}
        {btn('다시 하기', false, () => chain().redo().run())}
        <span className="sep" />
        {btn('HTML 붙여 넣기', importOpen, () => setImportOpen((o) => !o), '다른 몰의 상세설명 HTML 가져오기')}
        {btn('휴대폰으로 보기', preview, () => setPreview((p) => !p))}
      </div>
      {(inTable || inRow) && (
        <div className="toolbar sub" role="toolbar" aria-label={inTable ? '표 고치기' : '사진 묶음 고치기'}>
          {inTable && <>
            {btn('줄 추가', false, () => chain().addRowAfter().run())}
            {btn('칸 추가', false, () => chain().addColumnAfter().run())}
            {btn('줄 지우기', false, () => chain().deleteRow().run())}
            {btn('칸 지우기', false, () => chain().deleteColumn().run())}
            {btn('첫 줄 제목으로', false, () => chain().toggleHeaderRow().run())}
            {btn('표 지우기', false, () => chain().deleteTable().run())}
          </>}
          {inRow && <>
            {btn('나란히', editor.getAttributes('imageRow').layout !== 'slide', () => chain().updateAttributes('imageRow', { layout: 'grid' }).run())}
            {btn('옆으로 넘겨 보기', editor.getAttributes('imageRow').layout === 'slide', () => chain().updateAttributes('imageRow', { layout: 'slide' }).run())}
          </>}
        </div>
      )}
      <input ref={imageRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden onChange={pickImages} />
      <input ref={imagesRef} type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple hidden onChange={pickImages} />
      <input ref={videoRef} type="file" accept="video/mp4,video/webm,video/quicktime" hidden onChange={pickVideo} />
      {importOpen && (
        <div className="import-box">
          <label htmlFor="html-import">쿠팡, 스마트스토어 등에 쓰던 상세설명 HTML 을 붙여 넣어 주세요. 사진, 글, 표, 유튜브는 그대로 가져오고 스크립트는 지웁니다.</label>
          <textarea id="html-import" rows={6} value={importText} onChange={(e) => setImportText(e.target.value)} placeholder="<div><img src=&quot;https://...&quot;></div>" />
          <div className="actions">
            <button type="button" className="btn small" onClick={runImport} disabled={!importText.trim()}>지금 블록 아래에 가져오기</button>
            <button type="button" className="btn small ghost" onClick={() => setImportOpen(false)}>닫기</button>
          </div>
        </div>
      )}
      {busy && <div className="note" role="status" style={{ margin: 0, borderLeft: 0, borderRight: 0 }}>{busy}</div>}
      {error && <div className="note error" role="alert" style={{ margin: 0, borderLeft: 0, borderRight: 0 }}>{error} <button type="button" className="tool" onClick={() => setError('')}>닫기</button></div>}
      {info && <div className="note" style={{ margin: 0, borderLeft: 0, borderRight: 0 }}>{info} <button type="button" className="tool" onClick={() => setInfo('')}>닫기</button></div>}
      {outside > 0 && <p className="hint editor-hint">다른 사이트의 사진이 {outside}장 있습니다. 그 사이트가 사진을 지우거나 막으면 여기서도 보이지 않으니, 중요한 사진은 직접 올려 주세요.</p>}
      <div className={preview ? 'editor-split' : undefined}>
        <EditorContent editor={editor} />
        {preview && (
          <div className="phone-preview" aria-label="휴대폰 화면 미리보기">
            <div className="phone-screen"><RichView doc={editor.isEmpty ? null : JSON.stringify(editor.getJSON())} fallback="아직 쓴 내용이 없습니다." /></div>
          </div>
        )}
      </div>
    </div>
  );
}

// 사진·동영상·HTML 같은 큰 블록을 넣을 자리.
// 커서가 표 칸, 강조 상자, 목록 안에 있으면 그 안에 들어가 표 전체가 상세페이지를 감싸 버린다.
// 그래서 항상 맨 바깥 블록 단위로 넣는다: 커서가 빈 줄에 있으면 그 줄 자리에, 아니면 그 블록 바로 아래에.
function topSlot(ed: Editor): number | { from: number; to: number } {
  const { $from } = ed.state.selection;
  if ($from.depth === 0) return ed.state.selection.to;
  const top = $from.node(1);
  if (top.type.name === 'paragraph' && top.content.size === 0) return { from: $from.before(1), to: $from.after(1) };
  return $from.after(1);
}

// 넣은 블록이 선택된 채로 남으면 다음에 넣는 것이 그 블록을 바꿔 버리므로, 아래에 빈 줄을 하나 두어 커서를 옮긴다.
function insertBlock(ed: Editor, node: object) {
  ed.chain().focus().insertContentAt(topSlot(ed), [node, { type: 'paragraph' }]).run();
}

// 앞뒤의 빈 줄은 상품 화면에서 쓸데없는 여백이 되므로 저장할 때 잘라 낸다.
function trimEmpty<T extends { content?: unknown[] }>(doc: T): T {
  const blank = (n: unknown) => { const j = n as J; return j.type === 'paragraph' && !(j.content && j.content.length); };
  const c = [...(doc.content ?? [])];
  while (c.length > 1 && blank(c[0])) c.shift();
  while (c.length > 1 && blank(c[c.length - 1])) c.pop();
  return { ...doc, content: c };
}

type J = { type?: string; attrs?: Record<string, unknown>; content?: J[] };
function countImages(node: J, outsideOnly = false): number {
  let n = node.type === 'image' && (!outsideOnly || isOutsideImage(node.attrs?.src)) ? 1 : 0;
  for (const c of node.content ?? []) n += countImages(c, outsideOnly);
  return n;
}
