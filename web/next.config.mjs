// 브라우저가 보내는 /api 요청을 Java 서버로 그대로 전달한다.
// 브라우저 입장에서는 한 사이트이므로 로그인 쿠키와 위조 방지 토큰이 그대로 동작한다.
const API_BASE = process.env.API_BASE || 'http://localhost:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  // 개발 모드(next dev)를 Cloudflare 터널 주소로 열 때 필요. 없으면 Next 16 이 외부 주소의 개발용 요청을 막아 화면 스크립트가 멈춘다.
  allowedDevOrigins: ['sool.kimbbob.xyz'],
  experimental: {
    // 동영상(100MB)을 서버로 전달할 수 있게 한다. 기본값은 10MB 라서 그보다 큰 파일은 올라가지 않는다.
    proxyClientMaxBodySize: '110mb',
    // 큰 파일을 올리는 동안 연결이 끊기지 않게 한다(밀리초).
    proxyTimeout: 10 * 60 * 1000,
  },
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${API_BASE}/api/:path*` }];
  },
};

export default nextConfig;
