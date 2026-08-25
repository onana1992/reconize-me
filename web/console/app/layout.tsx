import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Recogniz-Me Console",
  description: "Dashboard SaaS — vérifications d’identité Recogniz-Me",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
