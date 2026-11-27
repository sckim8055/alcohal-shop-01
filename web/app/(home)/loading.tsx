import { Loading } from '@/components/Loading';

// 첫 화면(분류·검색·정렬을 바꿀 때 포함)은 상품 좌판 모양으로 기다린다. 첫 화면만 이 모양을 쓰려고 (home) 묶음 폴더에 넣었다.
export default function PageLoading() {
  return <Loading kind="stalls" />;
}
