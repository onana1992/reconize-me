import type { Metadata } from "next";
import "@kyc/brand/tokens.css";
import "@kyc/brand/base.css";
import "@kyc/brand/flow.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Recogniz-Me",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
