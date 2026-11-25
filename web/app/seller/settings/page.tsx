'use client';

import { useMe } from '@/components/Me';
import { Settings } from '@/components/SellerForms';

export default function SellerSettings() {
  const { me, refresh } = useMe();
  if (!me?.seller) return null;
  return (
    <>
      <div className="title"><h1>가게 설정</h1></div>
      <Settings seller={me.seller} onSaved={refresh} />
    </>
  );
}
