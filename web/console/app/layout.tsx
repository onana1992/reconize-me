import type { Metadata } from "next";
import Link from "next/link";
import { Wordmark } from "@kyc/brand";
import "@kyc/brand/tokens.css";
import "@kyc/brand/base.css";
import "@kyc/brand/console.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "Recogniz-Me Console",
  description: "Dashboard SaaS — vérifications d’identité Recogniz-Me",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>
        <header className="rm-app-header">
          <Link href="/" className="rm-app-brand">
            <Wordmark size={26} />
          </Link>
          <nav className="rm-app-nav">
            <Link href="/verifications/new">Nouvelle vérification</Link>
          </nav>
        </header>
        <div className="rm-page">{children}</div>
      </body>
    </html>
  );
}
