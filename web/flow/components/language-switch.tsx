"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { LOCALES, setLocaleAction, useLocale, useT } from "../i18n/client";

export function LanguageSwitch() {
  const t = useT();
  const locale = useLocale();
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function choose(next: (typeof LOCALES)[number]) {
    if (next === locale || pending) {
      return;
    }
    setPending(true);
    await setLocaleAction(next);
    router.refresh();
    setPending(false);
  }

  return (
    <div className="rm-flow-lang" role="group" aria-label={t("locale.switch")}>
      {LOCALES.map((code) => (
        <button
          key={code}
          type="button"
          data-variant="secondary"
          aria-pressed={code === locale}
          disabled={pending}
          onClick={() => void choose(code)}
        >
          {t(code === "fr" ? "locale.fr" : "locale.en")}
        </button>
      ))}
    </div>
  );
}
