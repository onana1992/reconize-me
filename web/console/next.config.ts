import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand"],
  // Sans ça, Next remonte jusqu'au home de l'utilisateur (lockfile parasite) et trace tout le disque.
  outputFileTracingRoot: path.join(__dirname, "../.."),
};

export default nextConfig;
