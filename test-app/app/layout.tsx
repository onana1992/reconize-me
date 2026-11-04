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
        <Script src="https://recogniz-me-api.onrender.com/v1/plugin.js" strategy="beforeInteractive" />
        {children}
      </body>
    </html>
  );
}
