import Link from "next/link";
import { Wordmark } from "@kyc/brand";
import { LanguageMenu } from "../../components/language-menu";
import { getLocale, getT } from "../../i18n";
import { siteContactUrl } from "../../lib/site";

export default async function AuthLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();
  const t = await getT();

  return (
    <div className="rm-auth">
      <header className="rm-auth-brand">
        <span className="rm-auth-brand-slot" aria-hidden="true" />
        <Link href="/login" className="rm-app-brand">
          <Wordmark size={24} />
        </Link>
        <LanguageMenu />
      </header>
      {children}
      <a className="rm-auth-help" href={siteContactUrl(locale)} target="_blank" rel="noreferrer">
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
          <path
            d="M2.5 3.5h11a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8l-3 2.5v-2.5H2.5a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1Z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
        </svg>
        <span>{t("common.help")}</span>
      </a>
    </div>
  );
}
