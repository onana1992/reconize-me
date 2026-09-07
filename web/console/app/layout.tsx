import type { Metadata } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import "@kyc/brand/tokens.css";
import "@kyc/brand/base.css";
import "@kyc/brand/console.css";
import "./globals.css";
import { getLocale, I18nProvider } from "../i18n";

const sans = Plus_Jakarta_Sans({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--rm-font-brand",
});

export const metadata: Metadata = {
  title: "Recogniz-Me Console",
  description: "Dashboard SaaS — vérifications d’identité Recogniz-Me",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();

  return (
    <html lang={locale} className={sans.variable}>
      <body>
        <I18nProvider key={locale} locale={locale}>
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
