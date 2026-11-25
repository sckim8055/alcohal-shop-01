// 상품 사진 대신 쓰는 분류별 그림. 장터 가격표처럼 단순한 선과 면으로 그린다.

const INK = '#2a1b14';

function Bottle() {
  return (
    <g stroke={INK} strokeWidth="3" strokeLinejoin="round">
      <path d="M92 20h16v22c0 8 14 14 14 34v52a6 6 0 0 1-6 6H84a6 6 0 0 1-6-6V76c0-20 14-26 14-34z" fill="#17603e" />
      <rect x="90" y="14" width="20" height="9" fill="#f2b705" />
      <rect x="78" y="84" width="44" height="30" fill="#fff" />
      <path d="M86 94h28M86 103h18" strokeWidth="2.5" />
      <path d="M150 104h34l-5 28h-24z" fill="#fff" />
      <path d="M152 112h30" strokeWidth="2.5" />
    </g>
  );
}

function Grain() {
  return (
    <g stroke={INK} strokeWidth="3" strokeLinejoin="round">
      <path d="M62 54c10-8 66-8 76 0l10 74a8 8 0 0 1-8 8H60a8 8 0 0 1-8-8z" fill="#e6cb97" />
      <path d="M84 50c-4-10 0-18 6-22 3 6 14 6 20 0 6 4 10 12 6 22" fill="#e6cb97" />
      <path d="M80 50h40" />
      <circle cx="100" cy="94" r="20" fill="#fff" />
      <path d="M92 94h16M100 86v16" strokeWidth="2.5" />
      <g fill="#fff"><ellipse cx="166" cy="124" rx="7" ry="4" /><ellipse cx="180" cy="132" rx="7" ry="4" /><ellipse cx="160" cy="136" rx="7" ry="4" /></g>
    </g>
  );
}

function Jar() {
  return (
    <g stroke={INK} strokeWidth="3" strokeLinejoin="round">
      <path d="M72 46h56c22 14 26 62 6 88H66c-20-26-16-74 6-88z" fill="#8a4b2a" />
      <rect x="68" y="36" width="64" height="12" fill="#6b3a20" />
      <path d="M100 36V20h14v-8" fill="none" />
      <circle cx="114" cy="12" r="4" fill="#fff" />
      <path d="M70 78c18 8 42 8 60 0" fill="none" strokeWidth="2.5" />
      <path d="M160 70v64M152 82h16M152 96h16M152 110h16" strokeWidth="2.5" />
    </g>
  );
}

function Cups() {
  return (
    <g stroke={INK} strokeWidth="3" strokeLinejoin="round">
      <path d="M40 86h54c0 26-10 40-27 40S40 112 40 86z" fill="#fff" />
      <ellipse cx="67" cy="86" rx="27" ry="7" fill="#f3f4ea" />
      <path d="M57 126h20v8H57z" fill="#fff" />
      <path d="M128 60c0-10 4-16 4-26h16c0 10 4 16 4 26 14 10 18 44 6 74h-36c-12-30-8-64 6-74z" fill="#c9d6c4" />
      <path d="M122 96c12 6 24 6 36 0" fill="none" strokeWidth="2.5" />
    </g>
  );
}

function Lesson() {
  return (
    <g stroke={INK} strokeWidth="3" strokeLinejoin="round">
      <path d="M46 84h108c0 30-22 50-54 50S46 114 46 84z" fill="#f2b705" />
      <ellipse cx="100" cy="84" rx="54" ry="12" fill="#fff" />
      <path d="M118 30l10 6-26 48-8-4z" fill="#e6cb97" />
      <path d="M62 134h76" />
      <path d="M160 40l4 10 10 2-8 7 2 10-8-5-9 5 3-10-8-7 10-2z" fill="#fff" strokeWidth="2.5" />
    </g>
  );
}

const BY_CATEGORY: Record<string, { bg: string; el: () => React.JSX.Element }> = {
  전통주: { bg: '#fbe9a9', el: Bottle },
  원재료: { bg: '#dcefe3', el: Grain },
  '양조 기구': { bg: '#f6d9c9', el: Jar },
  '술잔·주기': { bg: '#dbe5f6', el: Cups },
  '강의·체험': { bg: '#fde0dc', el: Lesson },
};

export function Art({ category }: { category: string }) {
  const a = BY_CATEGORY[category] ?? BY_CATEGORY['원재료'];
  const El = a.el;
  return (
    <svg viewBox="0 0 220 150" preserveAspectRatio="xMidYMid slice" role="img" aria-label={category + ' 그림'}>
      <rect width="220" height="150" fill={a.bg} />
      <path d="M0 136h220v14H0z" fill="#2a1b14" opacity="0.12" />
      <El />
    </svg>
  );
}

/** 첫 화면의 양은 주전자와 사발 */
export function Kettle() {
  return (
    <svg viewBox="0 0 300 230" role="img" aria-label="양은 주전자와 막걸리 사발">
      <g stroke={INK} strokeWidth="4" strokeLinejoin="round" strokeLinecap="round">
        <path d="M70 64c10-34 110-34 120 0" fill="none" strokeWidth="7" />
        <path d="M52 88c0-20 156-20 156 0l12 98c0 18-180 18-180 0z" fill="#f2b705" />
        <ellipse cx="130" cy="82" rx="78" ry="13" fill="#ffd54a" />
        <path d="M118 70h24v-10h-24z" fill="#2a1b14" />
        <path d="M212 112c26-4 46-22 58-46l14 8c-12 34-36 64-66 74z" fill="#f2b705" />
        <path d="M66 110c6 22 6 48 2 70" fill="none" stroke="#fff" strokeWidth="6" opacity="0.7" />
        <path d="M196 196h84c0 20-14 30-42 30s-42-10-42-30z" fill="#fff" />
        <ellipse cx="238" cy="196" rx="42" ry="9" fill="#f3f4ea" />
      </g>
    </svg>
  );
}
