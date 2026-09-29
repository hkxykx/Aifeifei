import type { NextConfig } from "next";

// 后端地址（服务端 rewrite 用）：容器内部署时设 API_INTERNAL_URL=http://backend:8000
const API_BACKEND = process.env.API_INTERNAL_URL || "http://127.0.0.1:8000";

const nextConfig: NextConfig = {
  compress: true,
  // Docker 构建（Dockerfile 设 NEXT_OUTPUT=standalone）产出 .next/standalone 精简产物；
  // 裸机/传统部署不设该变量，走默认 next start（Next 16 下 standalone 与 next start 互斥）
  ...(process.env.NEXT_OUTPUT === "standalone" ? { output: "standalone" as const } : {}),

  env: {
    // 构建时间戳：首页"系统已稳定运行"从本次构建部署时刻起算
    NEXT_PUBLIC_BUILD_TS: String(Date.now()),
  },

  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${API_BACKEND}/api/:path*`,
      },
      {
        source: "/uploads/:path*",
        destination: `${API_BACKEND}/uploads/:path*`,
      },
      {
        source: "/reader3/:path*",
        destination: `${API_BACKEND}/reader3/:path*`,
      },
    ];
  },

  experimental: {
    optimizePackageImports: [
      "framer-motion",
      "lucide-react",
      "@dnd-kit/core",
      "@dnd-kit/sortable",
      "@dnd-kit/utilities",
    ],
  },

  images: {
    formats: ["image/avif", "image/webp"],
    remotePatterns: [
      { protocol: "https", hostname: "static.hiromu.top" },
      { protocol: "https", hostname: "hiromu520.oss-cn-beijing.aliyuncs.com" },
      { protocol: "https", hostname: "picsum.photos" },
      { protocol: "https", hostname: "avatars.githubusercontent.com" },
      { protocol: "http", hostname: "wfqqreader-1252317822.image.myqcloud.com" },
    ],
  },
};

export default nextConfig;
