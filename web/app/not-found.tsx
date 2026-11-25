import Link from 'next/link';
import { Kettle } from '@/components/Art';

export default function NotFound() {
  return (
    <div className="lost">
      <div className="kettle"><Kettle /></div>
      <h1>여기에는 좌판이 없습니다</h1>
      <p>주소가 바뀌었거나 없는 화면입니다. 주소를 다시 확인하거나 장터 입구에서 찾아보세요.</p>
      <div className="row" style={{ justifyContent: 'center' }}>
        <Link className="btn" href="/">장터 입구로</Link>
        <Link className="btn plain" href="/my/orders">내 주문 보기</Link>
      </div>
    </div>
  );
}
