// 브라우저가 보내는 /api 요청을 Java 서버로 그대로 전달한다.
// 브라우저 입장에서는 한 사이트이므로 로그인 쿠키와 위조 방지 토큰이 그대로 동작한다.
const API_BASE = process.env.API_BASE || 'http://localhost:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${API_BASE}/api/:path*` }];
  },
};

export default nextConfig;
