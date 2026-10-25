import type { Translate } from "../../../i18n";

export function outcomeLabel(t: Translate, outcome: string): string {
  if (outcome === "clear") return t("console.product.amlOutcomeClear");
  if (outcome === "potential_match") return t("console.product.amlOutcomePotential");
  if (outcome === "confirmed_match") return t("console.product.amlOutcomeConfirmed");
  if (outcome === "failed") return t("console.product.amlOutcomeFailed");
  return outcome;
}

export function outcomeTone(outcome: string): "success" | "warning" | "danger" | "neutral" {
  if (outcome === "clear") return "success";
  if (outcome === "potential_match") return "warning";
  if (outcome === "confirmed_match" || outcome === "failed") return "danger";
  return "neutral";
}

export function categoryLabel(t: Translate, category: string): string {
  if (category === "sanction") return t("console.product.amlCategorySanction");
  if (category === "pep") return t("console.product.amlCategoryPep");
  if (category === "rca") return t("console.product.amlCategoryRca");
  if (category === "debarment") return t("console.product.amlCategoryDebarment");
  if (category === "crime") return t("console.product.amlCategoryCrime");
  return category;
}

export function dispositionLabel(t: Translate, value: string): string {
  if (value === "false_positive") return t("console.product.amlFalsePositive");
  if (value === "true_positive") return t("console.product.amlTruePositive");
  return t("console.product.amlUnreviewed");
}

export function dispositionTone(value: string): "success" | "danger" | "neutral" {
  if (value === "false_positive") return "success";
  if (value === "true_positive") return "danger";
  return "neutral";
}

export function reasonLabel(t: Translate, reason: string): string {
  if (reason === "different_person") return t("console.product.amlReasonDifferent");
  if (reason === "date_of_birth_conflict") return t("console.product.amlReasonDob");
  if (reason === "nationality_conflict") return t("console.product.amlReasonNationality");
  if (reason === "name_only") return t("console.product.amlReasonNameOnly");
  if (reason === "same_person") return t("console.product.amlReasonSame");
  if (reason === "listed_alias") return t("console.product.amlReasonAlias");
  return reason;
}
