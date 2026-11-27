'use client';

import { RateInput, useAdminData } from '@/components/AdminBits';
import { Note } from '@/components/Bits';
import { categoryTree, percent, when } from '@/lib/format';
import { Loading } from '@/components/Loading';

const SCOPE: Record<string, string> = { default: '기본', category: '분류', seller: '판매자' };

export default function Commission() {
  const { d, act, call } = useAdminData();
  return (
    <>
      <div className="title"><h1>수수료율</h1></div>
      <p>판매자에게 따로 정한 값이 있으면 그 값을, 없으면 중분류, 대분류, 기본값 순서로 찾습니다. 바꾼 값은 이후 들어오는 주문부터 적용됩니다.</p>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      {!d ? <Loading kind="table" /> : (
        <div className="stack">
          <div className="sheet">
            <table>
              <tbody>
                <tr><td><strong>기본</strong></td><td><RateInput label="기본 수수료율" value={d.defaultRateBp} allowEmpty={false}
                  onSave={(v) => call('/admin/commission/default', 'PUT', { ratePercent: v })} /></td></tr>
                {categoryTree(d.categories.filter((c) => c.active)).flatMap((t) => [t.top, ...t.children]).map((c) => (
                  <tr key={c.id}>
                    <td style={c.parentId ? { paddingLeft: 32 } : { fontWeight: 700 }}>{c.name}</td>
                    <td><RateInput label={c.name + ' 수수료율'} value={c.commissionRateBp} allowEmpty
                      onSave={(v) => call('/admin/categories/' + c.id + '/commission', 'PUT', { ratePercent: v })} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {d.rateLog.length > 0 && (
            <div className="sheet">
              <h2>최근 바꾼 기록</h2>
              <table>
                <tbody>
                  {d.rateLog.map((l) => (
                    <tr key={l.id}><td>{SCOPE[l.scope]}</td><td>{l.rateBp == null ? '따로 정하지 않음' : percent(l.rateBp) + '%'}</td><td>{l.changedByName}</td><td className="muted small">{when(l.changedAt)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  );
}
