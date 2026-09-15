import type { Metadata } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import "@kyc/brand/tokens.css";
import "@kyc/brand/base.css";
import "@kyc/brand/flow.css";
import "./globals.css";
import { getLocale, getT, I18nProvider } from "../i18n";

const sans = Plus_Jakarta_Sans({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--rm-font-brand",
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getT();
  return {
    title: t("meta.title"),
    description: t("meta.description"),
  };
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();

  return (
    <html lang={locale} className={sans.variable} suppressHydrationWarning>
      <body suppressHydrationWarning>
        <I18nProvider key={locale} locale={locale}>
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
