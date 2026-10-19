"use client";

import { useState } from "react";
import { CopyButton } from "../../../../../../components/copy-button";
import { useT } from "../../../../../../i18n/client";
import { SERVER_LANGUAGES, type ServerLanguage } from "../server-snippets";
import { webhookSnippet } from "./webhook-snippets";

export function WebhookHandlerSnippet() {
  const t = useT();
  const [language, setLanguage] = useState<ServerLanguage>("node");
  const value = webhookSnippet(language, t("console.webhooks.handlerNote"));

  return (
    <section className="rm-set-handler">
      <div>
        <h3>{t("console.webhooks.handlerTitle")}</h3>
        <p className="rm-lead">{t("console.webhooks.handlerLead")}</p>
      </div>
      <div className="rm-int-snippet">
        <div className="rm-int-snippet-head">
          <span>{t("console.webhooks.handlerLabel")}</span>
          <CopyButton value={value} />
        </div>
        <div className="rm-int-lang" role="tablist" aria-label={t("console.webhooks.languagesLabel")}>
          {SERVER_LANGUAGES.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={language === item.id}
              onClick={() => setLanguage(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <pre>
          <code>{value}</code>
        </pre>
      </div>
    </section>
  );
}
