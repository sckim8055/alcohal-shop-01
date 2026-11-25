'use client';

import { InquiryDesk } from '@/components/InquiryDesk';

export default function SellerInquiries() {
  return (
    <>
      <div className="title"><h1>문의</h1></div>
      <InquiryDesk base="/seller/inquiries" />
      <p className="hint" style={{ marginTop: 16 }}>상품 문의 중 공개 문의의 답변은 상품 화면에 모두에게 보입니다. 비밀글과 주문 문의는 쓴 사람만 봅니다.</p>
    </>
  );
}
