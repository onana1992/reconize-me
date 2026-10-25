"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useT } from "../../../i18n/client";
import { qualifyHitAction } from "./aml-actions";

const FALSE_REASONS = [
  ["different_person", "console.product.amlReasonDifferent"],
  ["date_of_birth_conflict", "console.product.amlReasonDob"],
  ["nationality_conflict", "console.product.amlReasonNationality"],
  ["name_only", "console.product.amlReasonNameOnly"],
] as const;

const TRUE_REASONS = [
  ["same_person", "console.product.amlReasonSame"],
  ["listed_alias", "console.product.amlReasonAlias"],
] as const;

export function AmlQualifyForm({ screeningId, hitId }: { screeningId: string; hitId: string }) {
  const t = useT();
  const router = useRouter();
  const [disposition, setDisposition] = useState("false_positive");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const reasons = disposition === "true_positive" ? TRUE_REASONS : FALSE_REASONS;

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await qualifyHitAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(result.message || t("console.integrations.createError"));
      return;
    }
    router.refresh();
  }

  return (
    <form action={onSubmit} className="rm-form">
      <input type="hidden" name="screening_id" value={screeningId} />
      <input type="hidden" name="hit_id" value={hitId} />
      <label>
        <span>{t("console.product.amlQualify")}</span>
        <select name="disposition" value={disposition} onChange={(event) => setDisposition(event.target.value)}>
          <option value="false_positive">{t("console.product.amlFalsePositive")}</option>
          <option value="true_positive">{t("console.product.amlTruePositive")}</option>
        </select>
      </label>
      <label>
        <span>{t("console.product.amlReason")}</span>
        <select name="reason" defaultValue={reasons[0][0]} key={disposition}>
          {reasons.map(([value, label]) => (
            <option key={value} value={value}>
              {t(label)}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>{t("console.product.amlNote")}</span>
        <input name="note" type="text" maxLength={500} />
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <div className="rm-actions">
        <button type="submit" disabled={pending}>
          {pending ? t("console.rules.publishing") : t("console.product.amlQualify")}
        </button>
      </div>
    </form>
  );
}
