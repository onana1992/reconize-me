import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand", "@kyc/capture-sdk"],
  outputFileTracingRoot: path.join(__dirname, "../.."),
};

export default nextConfig;
