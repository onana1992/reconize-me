import type { Metadata } from "next";
import "@/brand/tokens.css";
import "@/brand/base.css";
import "@/brand/density.css";
import "@/brand/console.css";
import "./globals.css";
import { getLocale, I18nProvider } from "../i18n";

export const metadata: Metadata = {
  title: "Recogniz-Me Console",
  description: "Dashboard SaaS — vérifications d’identité Recogniz-Me",
  icons: { icon: "/icon.svg" },
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();

  return (
    <html lang={locale} suppressHydrationWarning>
      <body suppressHydrationWarning>
        <I18nProvider key={locale} locale={locale}>
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
