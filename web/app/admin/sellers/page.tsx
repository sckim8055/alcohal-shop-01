'use client';

import { RateInput, useAdminData } from '@/components/AdminBits';
import { Note } from '@/components/Bits';

const SELLER_STATUS: Record<string, string> = { pending: '심사 중', approved: '승인', rejected: '반려' };

export default function Sellers() {
  const { d, act, call } = useAdminData();
  return (
    <>
      <div className="title"><h1>판매자</h1></div>
      <Note kind="error">{act.error}</Note>
      <Note kind="ok">{act.done}</Note>
      {!d ? <p className="muted">불러오는 중입니다.</p> : (
        <div className="sheet scroll">
          <table>
            <thead><tr><th>가게</th><th>입점</th><th>주류 면허</th><th>이 가게만의 수수료율</th></tr></thead>
            <tbody>
              {d.sellers.map((s) => (
                <tr key={s.id}>
                  <td>{s.shopName}<div className="muted small">{s.email}, {s.bizNo}</div></td>
                  <td>
                    <select aria-label={s.shopName + ' 입점 상태'} value={s.status} style={{ width: 'auto' }}
                      onChange={(e) => call('/admin/sellers/' + s.id + '/status', 'POST', { status: e.target.value }, '입점 상태를 바꾸고 판매자에게 알렸습니다.')}>
                      {Object.entries(SELLER_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </select>
                  </td>
                  <td>
                    {s.licenseType ? <>{s.licenseType} {s.licenseNo}</> : <span className="muted">없음</span>}
                    <div>
                      <label style={{ fontWeight: 400 }}>
                        <input type="checkbox" checked={s.licenseVerified}
                          onChange={(e) => call('/admin/sellers/' + s.id + '/license', 'POST', { verified: e.target.checked },
                            e.target.checked ? '면허를 확인 처리했습니다.' : '면허 확인을 취소하고 그 가게의 술을 판매 중지로 바꿨습니다.')} /> 확인함
                      </label>
                    </div>
                  </td>
                  <td><RateInput label={s.shopName + ' 수수료율'} value={s.commissionRateBp} allowEmpty
                    onSave={(v) => call('/admin/sellers/' + s.id + '/commission', 'PUT', { ratePercent: v })} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
