'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import { categoryTree } from '@/lib/format';
import type { Category } from '@/lib/types';

function Row({ c, child, save }: { c: Category; child: boolean; save: (id: number, body: object, ok: string) => void }) {
  const [name, setName] = useState(c.name);
  const [order, setOrder] = useState(String(c.sortOrder));
  return (
    <tr style={c.active ? undefined : { opacity: 0.55 }}>
      <td style={child ? { paddingLeft: 32 } : undefined}>
        <input aria-label="분류 이름" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} style={{ fontWeight: child ? 400 : 700 }} />
      </td>
      <td><input aria-label="순서" type="number" value={order} onChange={(e) => setOrder(e.target.value)} style={{ width: 76 }} /></td>
      <td>
        {child ? (c.alcohol ? '술' : '') : (
          <label style={{ fontWeight: 400 }}>
            <input type="checkbox" checked={c.alcohol} onChange={(e) => save(c.id, { alcohol: e.target.checked }, '주류 여부를 바꿨습니다. 아래 중분류도 함께 바뀝니다.')} /> 술(면허·성인인증 필요)
          </label>
        )}
      </td>
      <td className="right">
        <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
          <button type="button" className="btn plain small" onClick={() => save(c.id, { name, sortOrder: Number(order) || 0 }, '저장했습니다.')}>저장</button>
          <button type="button" className="btn plain small" onClick={() => save(c.id, { active: !c.active }, c.active ? '쓰지 않는 분류로 바꿨습니다.' : '다시 쓰는 분류로 바꿨습니다.')}>{c.active ? '쓰지 않음' : '다시 쓰기'}</button>
        </span>
      </td>
    </tr>
  );
}

export default function Categories() {
  const act = useAction();
  const [list, setList] = useState<Category[] | null>(null);
  const [add, setAdd] = useState({ parentId: '', name: '', alcohol: false });
  const load = useCallback(() => {
    api<Category[]>('/admin/categories').then(setList).catch((e) => act.setError(e.message));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  const save = (id: number, body: object, ok: string) => act.run(() => api('/admin/categories/' + id, { method: 'PUT', body }), ok).then((done) => done && load());
  async function create(e: React.FormEvent) {
    e.preventDefault();
    const body = { name: add.name, parentId: add.parentId === '' ? null : Number(add.parentId), alcohol: add.alcohol };
    if (await act.run(() => api('/admin/categories', { method: 'POST', body }), `'${add.name}' 분류를 추가했습니다.`)) {
      setAdd({ ...add, name: '' });
      load();
    }
  }
  const tree = list ? categoryTree(list) : [];

  return (
    <>
      <div className="title"><h1>분류</h1></div>
      <p>대분류와 그 아래 중분류 두 단계로 나눕니다. 상품이 걸려 있을 수 있어 분류는 지우지 않고 “쓰지 않음”으로 바꿉니다.</p>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>

      <form className="sheet" onSubmit={create} style={{ marginBottom: 20 }}>
        <h2>분류 추가</h2>
        <div className="inline-form">
          <select aria-label="어디에 추가할지" value={add.parentId} onChange={(e) => setAdd({ ...add, parentId: e.target.value })}>
            <option value="">새 대분류</option>
            {tree.map((t) => <option key={t.top.id} value={t.top.id}>{t.top.name} 아래 중분류</option>)}
          </select>
          <input required maxLength={100} placeholder="분류 이름" aria-label="분류 이름" value={add.name} onChange={(e) => setAdd({ ...add, name: e.target.value })} />
          {add.parentId === '' && (
            <label style={{ fontWeight: 400, margin: 0 }}><input type="checkbox" checked={add.alcohol} onChange={(e) => setAdd({ ...add, alcohol: e.target.checked })} /> 술</label>
          )}
          <button className="btn" disabled={act.busy}>추가</button>
        </div>
      </form>

      {!list ? <p className="muted">불러오는 중입니다.</p> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>이름</th><th>순서</th><th>주류</th><th /></tr></thead>
            <tbody>
              {tree.flatMap((t) => [
                <Row key={t.top.id + ':' + t.top.name + t.top.sortOrder} c={t.top} child={false} save={save} />,
                ...t.children.map((c) => <Row key={c.id + ':' + c.name + c.sortOrder} c={c} child save={save} />),
              ])}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
