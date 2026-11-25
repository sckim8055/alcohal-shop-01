'use client';

import { InquiryDesk } from '@/components/InquiryDesk';

export default function AdminInquiries() {
  return (
    <>
      <div className="title"><h1>1:1 문의</h1></div>
      <InquiryDesk base="/admin/inquiries" admin />
      <p className="hint" style={{ marginTop: 16 }}>구매자와 판매자가 운영자에게 남긴 문의입니다. 상품·주문 문의는 각 판매자가 답합니다.</p>
    </>
  );
}
