import type { Metadata } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import "@kyc/brand/tokens.css";
import "@kyc/brand/base.css";
import "@kyc/brand/flow.css";
import "./globals.css";

const sans = Plus_Jakarta_Sans({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--rm-font-brand",
});

export const metadata: Metadata = {
  title: "Recogniz-Me",
  description: "Parcours de vérification d’identité",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr" className={sans.variable}>
      <body>{children}</body>
    </html>
  );
}
