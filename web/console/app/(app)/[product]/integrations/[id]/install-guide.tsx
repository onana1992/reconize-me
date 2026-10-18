"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { StatusBadge } from "@/brand/status-badge";
import { CopyButton } from "../../../../../components/copy-button";
import { useT } from "../../../../../i18n/client";
import { CLIENT_LIBRARIES, clientSnippet, type ClientLibrary, type OpenMode } from "./client-snippets";
import { SERVER_LANGUAGES, serverSnippet, type ServerLanguage } from "./server-snippets";

function NativeButton({ mode }: { mode: OpenMode }) {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const parent = host.current;
    if (!parent) return;
    let cancelled = false;

    async function mount() {
      if (!customElements.get("recogniz-me-button")) {
        await new Promise<void>((resolve, reject) => {
          const script = document.createElement("script");
          script.src = "/recogniz-me-button.js";
          script.async = true;
          script.onload = () => resolve();
          script.onerror = () => reject(new Error("recogniz-me-button"));
          document.head.appendChild(script);
        });
      }
      if (cancelled || !parent) return;
      const element = document.createElement("recogniz-me-button");
      element.setAttribute("mode", mode);
      element.setAttribute("preview", "");
      parent.replaceChildren(element);
    }

    void mount();
    return () => {
      cancelled = true;
    };
  }, [mode]);

  return <div ref={host} />;
}

function Example<T extends string>({
  label,
  tabsLabel,
  tabs,
  active,
  onSelect,
  value,
}: {
  label: string;
  tabsLabel: string;
  tabs: { id: T; label: string }[];
  active: T;
  onSelect: (id: T) => void;
  value: string;
}) {
  return (
    <div className="rm-int-snippet">
      <div className="rm-int-snippet-head">
        <span>{label}</span>
        <CopyButton value={value} />
      </div>
      <div className="rm-int-lang" role="tablist" aria-label={tabsLabel}>
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={active === item.id}
            onClick={() => onSelect(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>
      <pre>
        <code>{value}</code>
      </pre>
    </div>
  );
}

export function InstallGuide({
  apiBase,
  keyPrefix,
  keysHref,
}: {
  apiBase: string;
  keyPrefix: string;
  keysHref: string;
}) {
  const t = useT();
  const [mode, setMode] = useState<OpenMode>("redirect");
  const [language, setLanguage] = useState<ServerLanguage>("node");
  const [library, setLibrary] = useState<ClientLibrary>("javascript");
  const required = t("console.integrations.install.required");
  const optional = t("console.integrations.install.optional");
  const server = serverSnippet(
    language,
    apiBase,
    keyPrefix,
    t("console.integrations.install.serverComment"),
    optional,
  );
  const browser = clientSnippet(library, mode);

  const fields: { label: string; name: string; required: boolean }[] = [
    {
      label: t("console.integrations.install.apiKey"),
      name: t("console.integrations.install.apiKeyName"),
      required: true,
    },
    {
      label: t("console.integrations.install.externalId"),
      name: t("console.integrations.install.externalIdName"),
      required: false,
    },
    {
      label: t("console.integrations.install.firstName"),
      name: t("console.integrations.install.firstNameName"),
      required: false,
    },
    {
      label: t("console.integrations.install.lastName"),
      name: t("console.integrations.install.lastNameName"),
      required: false,
    },
    {
      label: t("console.integrations.install.email"),
      name: t("console.integrations.install.emailName"),
      required: false,
    },
  ];

  return (
    <div className="rm-int-install">
      <div className="rm-int-install-main">
        <section className="rm-int-panel">
          <h2>{t("console.integrations.install.title")}</h2>
          <p className="rm-lead">{t("console.integrations.install.lead")}</p>
        </section>

        <section className="rm-int-panel">
          <h2>{t("console.integrations.install.fieldsTitle")}</h2>
          <p className="rm-lead">{t("console.integrations.install.fieldsLead")}</p>
          <ul className="rm-int-fields">
            {fields.map((field) => (
              <li key={field.name}>
                <div>
                  <p>{field.label}</p>
                  <code>{field.name}</code>
                </div>
                <StatusBadge
                  label={field.required ? required : optional}
                  tone={field.required ? "info" : "neutral"}
                />
              </li>
            ))}
          </ul>
        </section>

        <section className="rm-int-panel">
          <h2>{t("console.integrations.install.openTitle")}</h2>
          <p className="rm-lead">{t("console.integrations.install.openLead")}</p>
          <fieldset className="rm-int-open">
            <legend className="rm-sr-only">{t("console.integrations.install.openTitle")}</legend>
            <div className="rm-int-open-list">
              <label className="rm-int-open-option">
                <input
                  type="radio"
                  name="hosted-open"
                  value="redirect"
                  checked={mode === "redirect"}
                  onChange={() => setMode("redirect")}
                />
                <span>
                  <strong>{t("console.integrations.install.redirect")}</strong>
                  <p>{t("console.integrations.install.redirectBody")}</p>
                </span>
              </label>
              <label className="rm-int-open-option">
                <input
                  type="radio"
                  name="hosted-open"
                  value="incontext"
                  checked={mode === "incontext"}
                  onChange={() => setMode("incontext")}
                />
                <span>
                  <strong>{t("console.integrations.install.incontext")}</strong>
                  <p>{t("console.integrations.install.incontextBody")}</p>
                </span>
              </label>
            </div>
          </fieldset>
          <Example
            label={t("console.integrations.install.serverLabel")}
            tabsLabel={t("console.integrations.install.languagesLabel")}
            tabs={SERVER_LANGUAGES}
            active={language}
            onSelect={setLanguage}
            value={server}
          />
          <Example
            label={t("console.integrations.install.browserLabel")}
            tabsLabel={t("console.integrations.install.clientsLabel")}
            tabs={CLIENT_LIBRARIES}
            active={library}
            onSelect={setLibrary}
            value={browser}
          />
          <p className="rm-lead">
            <Link href={keysHref}>{t("console.integrations.install.keysLink")}</Link>
          </p>
        </section>
      </div>

      <aside className="rm-int-panel rm-int-preview">
        <h2>{t("console.integrations.install.previewTitle")}</h2>
        <p className="rm-lead">{t("console.integrations.install.previewLead")}</p>
        <div className="rm-int-preview-stage">
          <NativeButton mode={mode} />
        </div>
        <p className="rm-int-preview-note">
          {mode === "redirect"
            ? t("console.integrations.install.previewRedirect")
            : t("console.integrations.install.previewIncontext")}
        </p>
      </aside>
    </div>
  );
}
