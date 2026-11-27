'use client';

import { useCallback, useEffect, useState } from 'react';
import { Note, useAction } from '@/components/Bits';
import { api } from '@/lib/api';
import type { Address, AddressInput } from '@/lib/types';
import { Loading } from '@/components/Loading';

const MAX = 10;
const BLANK: AddressInput = { label: '', receiver: '', phone: '', address: '', isDefault: false };

function AddressForm({ editing, first, onSaved, onCancel }: { editing: Address | null; first: boolean; onSaved: (msg: string) => void; onCancel: () => void }) {
  const act = useAction();
  const [f, setF] = useState<AddressInput>(editing ? { label: editing.label, receiver: editing.receiver, phone: editing.phone, address: editing.address, isDefault: editing.isDefault } : BLANK);
  const set = (k: 'label' | 'receiver' | 'phone' | 'address') => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const body = { ...f, label: f.label.trim(), receiver: f.receiver.trim(), phone: f.phone.trim(), address: f.address.trim() };
    const ok = await act.run(() => editing
      ? api<Address>('/my/addresses/' + editing.id, { method: 'PUT', body })
      : api<Address>('/my/addresses', { method: 'POST', body }));
    if (ok) onSaved(editing ? '배송지를 고쳤습니다.' : '배송지를 추가했습니다.');
  }

  return (
    <form className="sheet" id="address-form" onSubmit={submit}>
      <h2>{editing ? '배송지 고치기' : '새 배송지 추가'}</h2>
      <Note kind="error">{act.error}</Note>
      <div className="fields">
        <div className="field"><label htmlFor="ad-label">이름표</label><input id="ad-label" maxLength={30} placeholder="예: 집, 회사" value={f.label} onChange={set('label')} /></div>
        <div className="field"><label htmlFor="ad-receiver">받는 분</label><input id="ad-receiver" required maxLength={40} value={f.receiver} onChange={set('receiver')} /></div>
        <div className="field"><label htmlFor="ad-phone">연락처</label><input id="ad-phone" required maxLength={30} inputMode="tel" value={f.phone} onChange={set('phone')} /></div>
      </div>
      <div className="field"><label htmlFor="ad-address">주소</label><input id="ad-address" required maxLength={300} value={f.address} onChange={set('address')} /></div>
      <div className="field">
        <label className="row" style={{ fontWeight: 400, gap: 8 }}>
          <input type="checkbox" checked={f.isDefault || first} disabled={first || (editing?.isDefault ?? false)} onChange={(e) => setF({ ...f, isDefault: e.target.checked })} />
          기본 배송지로 쓰기
        </label>
        {first && <p className="hint">첫 배송지는 자동으로 기본 배송지가 됩니다.</p>}
      </div>
      <div className="row">
        <button className="btn" disabled={act.busy}>{editing ? '고친 내용 저장' : '배송지 추가'}</button>
        {editing && <button className="btn plain" type="button" onClick={onCancel}>고치기 그만두기</button>}
      </div>
    </form>
  );
}

export default function MyAddresses() {
  const [list, setList] = useState<Address[] | null>(null);
  const [editing, setEditing] = useState<Address | null>(null);
  const [formKey, setFormKey] = useState(0);
  const act = useAction();

  const load = useCallback(() => {
    api<Address[]>('/my/addresses').then((l) => setList(Array.isArray(l) ? l : [])).catch((e) => { act.setError(e.message); setList([]); });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  async function change(a: Address, action: 'default' | 'delete') {
    if (action === 'delete' && !window.confirm(`'${a.label || a.address}' 배송지를 지울까요?`)) return;
    let next: Address[] = [];
    const ok = await act.run(async () => { next = await api<Address[]>('/my/addresses/' + a.id + '/' + action, { method: 'POST' }); },
      action === 'default' ? '기본 배송지를 바꿨습니다.' : '배송지를 지웠습니다.');
    if (ok) {
      setList(Array.isArray(next) ? next : []);
      if (editing?.id === a.id) { setEditing(null); setFormKey((k) => k + 1); }
    }
  }
  function saved(msg: string) {
    setEditing(null);
    setFormKey((k) => k + 1);
    act.run(async () => load(), msg);
  }
  function startEdit(a: Address) {
    setEditing(a);
    setFormKey((k) => k + 1);
    setTimeout(() => document.getElementById('ad-receiver')?.focus(), 0);
  }

  const full = (list?.length ?? 0) >= MAX;
  return (
    <div className="stack" style={{ maxWidth: 720 }}>
      <div className="title"><h1>배송지 관리</h1></div>
      <div>
        <Note kind="error">{act.error}</Note>
        <Note kind="ok">{act.done}</Note>
      </div>
      {!list ? <Loading kind="table" /> : list.length === 0 ? (
        <div className="sheet">
          <h3>저장한 배송지가 없습니다</h3>
          <p>아래에서 배송지를 추가하면 주문할 때 골라 쓸 수 있습니다.</p>
        </div>
      ) : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>배송지</th><th>받는 분</th><th /></tr></thead>
            <tbody>
              {list.map((a) => (
                <tr key={a.id}>
                  <td>
                    <strong>{a.label || '이름표 없음'}</strong> {a.isDefault && <span className="stamp good">기본</span>}
                    <div className="small">{a.address}</div>
                  </td>
                  <td>{a.receiver}<div className="muted small">{a.phone}</div></td>
                  <td className="right">
                    <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                      {!a.isDefault && <button type="button" className="btn plain small" disabled={act.busy} onClick={() => change(a, 'default')}>기본으로</button>}
                      <button type="button" className="btn plain small" disabled={act.busy} onClick={() => startEdit(a)}>고치기</button>
                      <button type="button" className="btn plain small" disabled={act.busy} onClick={() => change(a, 'delete')}>지우기</button>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="hint" style={{ marginTop: 8 }}>배송지는 {MAX}개까지 저장할 수 있습니다. 기본 배송지는 주문할 때 처음부터 채워 둡니다.</p>
        </div>
      )}
      {list && (full && !editing ? (
        <Note>배송지를 {MAX}개까지 저장했습니다. 새로 추가하려면 쓰지 않는 배송지를 지워 주세요.</Note>
      ) : (
        <AddressForm key={formKey} editing={editing} first={!editing && list.length === 0} onSaved={saved} onCancel={() => { setEditing(null); setFormKey((k) => k + 1); }} />
      ))}
    </div>
  );
}
