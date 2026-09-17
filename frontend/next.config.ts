import type { NextConfig } from "next";

// O navegador chama /api na mesma origem do front, e o Next repassa ao backend:
// sem CORS no caminho e sem a URL do backend dentro do bundle.
// O valor é lido no build; no Docker ele vem do build arg BACKEND_URL.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
