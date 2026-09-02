import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand", "@kyc/capture-sdk"],
  // Sans ça, Next remonte jusqu'au home de l'utilisateur (lockfile parasite) et trace tout le disque.
  outputFileTracingRoot: path.join(__dirname, "../.."),
  async headers() {
    return [
      {
        source: "/flow/:path*",
        headers: [{ key: "Cache-Control", value: "no-store" }],
      },
    ];
  },
};

export default nextConfig;
