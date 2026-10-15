import type { NextConfig } from "next";

const apiBase = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  // Deux appels vision peuvent dépasser 30 s. Le proxy de dev coupe sinon la requête et la page affiche un échec d'envoi.
  experimental: {
    proxyTimeout: 120_000,
  },
  allowedDevOrigins: ["10.0.0.133", "*.trycloudflare.com", "*.ngrok-free.app", "*.ngrok.io"],
  outputFileTracingRoot: __dirname,
  turbopack: {
    root: __dirname,
  },
  async rewrites() {
    return [{ source: "/v1/:path*", destination: `${apiBase}/v1/:path*` }];
  },
};

export default nextConfig;
