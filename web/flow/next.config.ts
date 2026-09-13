import path from "node:path";
import type { NextConfig } from "next";

const apiBase = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand", "@kyc/capture-sdk"],
  allowedDevOrigins: ["10.0.0.133", "*.trycloudflare.com", "*.ngrok-free.app", "*.ngrok.io"],
  outputFileTracingRoot: path.join(__dirname, "../.."),
  async rewrites() {
    return [{ source: "/v1/:path*", destination: `${apiBase}/v1/:path*` }];
  },
};

export default nextConfig;
