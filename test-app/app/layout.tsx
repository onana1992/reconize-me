import type { Metadata } from "next";
import Script from "next/script";
import "./globals.css";

export const metadata: Metadata = {
  title: "Test Recogniz-Me",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>
        <Script src="/recogniz-me-button.js" strategy="beforeInteractive" />
        {children}
      </body>
    </html>
  );
}
