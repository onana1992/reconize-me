import path from "node:path";
import type { NextConfig } from "next";

const RETIRED_PATHS = [
  "/products/identity-verification",
  "/products/biometric-authentication",
  "/products/aml-screening",
  "/pricing",
  "/services",
  "/company",
  "/security",
  "/docs",
  "/contact",
  "/legal/terms",
  "/legal/privacy",
  "/legal/dpa",
];

const nextConfig: NextConfig = {
  transpilePackages: ["@kyc/brand"],
  allowedDevOrigins: ["10.0.0.133"],
  async redirects() {
    return RETIRED_PATHS.map((path) => ({
      source: `/:locale(fr|en)${path}`,
      destination: "/:locale",
      permanent: false,
    }));
  },
  // Sans ça, Next remonte jusqu'au home de l'utilisateur (lockfile parasite) et trace tout le disque.
  outputFileTracingRoot: path.join(__dirname, "../.."),
  images: {
    /* AVIF d'abord : sur les photographies du hero et des cartes d'enjeu, il
       tient le budget de la charte (§9.3) là où WebP le dépasse. WebP reste en
       second pour les navigateurs qui ne prennent pas AVIF. */
    formats: ["image/avif", "image/webp"],
    /* Les largeurs servies suivent les points de rupture du site, pas la liste
       par défaut de Next : sept tailles inutilisées, c'est autant de variantes
       générées au build pour rien. */
    deviceSizes: [420, 640, 828, 1080, 1440, 1920],
    imageSizes: [180, 320],
  },
};

export default nextConfig;
