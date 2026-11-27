import { Loading } from '@/components/Loading';

// 왼쪽 메뉴는 그대로 두고 오른쪽 내용만 기다린다.
export default function PageLoading() {
  return <Loading kind="list" />;
}
