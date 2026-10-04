import path from "node:path";
import type { NextConfig } from "next";

const repoRoot = path.join(__dirname, "../..");

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand"],
  allowedDevOrigins: ["10.0.0.133"],
  // Sans ça, Next remonte jusqu'au home de l'utilisateur (lockfile parasite) et trace tout le disque.
  outputFileTracingRoot: repoRoot,
  webpack: (config, { dev }) => {
    if (dev) {
      config.watchOptions = {
        ...config.watchOptions,
        ignored: [
          "**/node_modules/**",
          "**/.git/**",
          "**/.next/**",
          path.join(repoRoot, "api"),
          path.join(repoRoot, "docs"),
          path.join(repoRoot, "web", "site"),
          path.join(repoRoot, "web", "flow"),
        ],
      };
    }
    return config;
  },
};

export default nextConfig;
