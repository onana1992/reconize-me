"use client";

import Link from "next/link";
import { StatusBadge } from "@/brand/status-badge";
import { CopyButton } from "../../../components/copy-button";
import { useT } from "../../../i18n/client";

export function AmlInstall({
  apiBase,
  keyPrefix,
  keysHref,
}: {
  apiBase: string;
  keyPrefix: string;
  keysHref: string;
}) {
  const t = useT();
  const create = `curl -s -X POST ${apiBase}/v1/screenings \\
  -H "Authorization: Bearer ${keyPrefix}" \\
  -H "Content-Type: application/json" \\
  -d '{"first_name":"Claire","last_name":"Martin","nationality":"FR"}'`;
  const read = `curl -s ${apiBase}/v1/screenings/{id} \\
  -H "Authorization: Bearer ${keyPrefix}"`;
  const disposition = `curl -s -X POST ${apiBase}/v1/screenings/{id}/hits/{hitId}/disposition \\
  -H "Authorization: Bearer ${keyPrefix}" \\
  -H "Content-Type: application/json" \\
  -d '{"disposition":"false_positive","reason":"different_person"}'`;
  const fields = [
    { label: t("console.verifications.firstName"), name: "first_name", required: true },
    { label: t("console.verifications.lastName"), name: "last_name", required: true },
    { label: t("console.product.amlBirth"), name: "date_of_birth", required: false },
    { label: t("console.product.amlNationality"), name: "nationality", required: false },
  ];

  return (
    <div className="rm-int-install">
      <div className="rm-int-install-main">
        <section className="rm-int-panel">
          <h2>{t("console.product.amlInstallTitle")}</h2>
          <p className="rm-lead">{t("console.product.amlInstallLead")}</p>
        </section>
        <section className="rm-int-panel">
          <h2>{t("console.product.amlInstallFieldsTitle")}</h2>
          <ul className="rm-int-fields">
            {fields.map((field) => (
              <li key={field.name}>
                <div>
                  <p>{field.label}</p>
                  <code>{field.name}</code>
                </div>
                <StatusBadge
                  label={field.required ? t("console.integrations.install.required") : t("console.integrations.install.optional")}
                  tone={field.required ? "info" : "neutral"}
                />
              </li>
            ))}
          </ul>
        </section>
        <section className="rm-int-panel">
          <Snippet label={t("console.product.amlInstallCreate")} value={create} />
          <Snippet label={t("console.product.amlInstallRead")} value={read} />
          <Snippet label={t("console.product.amlInstallDisposition")} value={disposition} />
          <p className="rm-lead">
            <Link href={keysHref}>{t("console.integrations.install.keysLink")}</Link>
          </p>
        </section>
      </div>
    </div>
  );
}

function Snippet({ label, value }: { label: string; value: string }) {
  return (
    <div className="rm-int-snippet">
      <div className="rm-int-snippet-head">
        <span>{label}</span>
        <CopyButton value={value} />
      </div>
      <pre>
        <code>{value}</code>
      </pre>
    </div>
  );
}
