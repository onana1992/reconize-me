import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: ["10.0.0.133"],
  // Racine = ce projet. Sans ça, Next remonte au lockfile du profil Windows.
  outputFileTracingRoot: __dirname,
  turbopack: {
    root: __dirname,
  },
  // Next 15 remet le cache client des pages dynamiques à 0 : chaque clic
  // attend un rendu serveur complet. 30 s suffit pour naviguer sans données périmées longtemps.
  experimental: {
    staleTimes: {
      dynamic: 30,
      static: 180,
    },
  },
};

export default nextConfig;
