import { Loading } from '@/components/Loading';

// 화면을 옮기는 동안 보여 준다. 더 알맞은 모양이 있는 곳은 그 폴더에 따로 둔다.
export default function PageLoading() {
  return <Loading kind="list" />;
}
