"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent, type PointerEvent } from "react";
import { createPortal } from "react-dom";
import { RequiredMark } from "../../../components/required-mark";
import { useT } from "../../../i18n/client";
import { tIntegrationMode } from "../../../lib/environment";
import { createScreeningAction } from "./aml-actions";
import type { DrawerIntegration } from "./new-verification-drawer";

const SANDBOX_PEOPLE = [
  {
    id: "claire",
    first: "Claire",
    last: "Martin",
    dob: "",
    nationality: "FR",
    scenario: "console.product.amlScenarioClear",
  },
  {
    id: "julien",
    first: "Julien",
    last: "Mercier",
    dob: "1971-04-12",
    nationality: "FR",
    scenario: "console.product.amlScenarioSanction",
  },
  {
    id: "camille",
    first: "Camille",
    last: "Bernard",
    dob: "1982-11-03",
    nationality: "FR",
    scenario: "console.product.amlScenarioPep",
  },
  {
    id: "lucas",
    first: "Lucas",
    last: "Bernard",
    dob: "2005-01-20",
    nationality: "FR",
    scenario: "console.product.amlScenarioRca",
  },
  {
    id: "sophie",
    first: "Sophie",
    last: "Tremblay",
    dob: "1990-01-01",
    nationality: "CA",
    scenario: "console.product.amlScenarioDebarment",
  },
] as const;

type SandboxPerson = (typeof SANDBOX_PEOPLE)[number];

export function NewScreeningDrawer({
  integrations,
  defaultIntegrationId,
}: {
  integrations: DrawerIntegration[];
  defaultIntegrationId?: string;
}) {
  const t = useT();
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [mounted, setMounted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const initialId = defaultIntegrationId ?? integrations[0]?.id ?? "";
  const [selectedId, setSelectedId] = useState(initialId);
  const [personId, setPersonId] = useState<SandboxPerson["id"]>(SANDBOX_PEOPLE[0].id);
  const [firstName, setFirstName] = useState(SANDBOX_PEOPLE[0].first);
  const [lastName, setLastName] = useState(SANDBOX_PEOPLE[0].last);
  const [birth, setBirth] = useState(SANDBOX_PEOPLE[0].dob);
  const [nationality, setNationality] = useState(SANDBOX_PEOPLE[0].nationality);

  useEffect(() => {
    setMounted(true);
  }, []);

  const selected = integrations.find((item) => item.id === selectedId) ?? integrations[0];

  function applyPerson(person: SandboxPerson) {
    setPersonId(person.id);
    setFirstName(person.first);
    setLastName(person.last);
    setBirth(person.dob);
    setNationality(person.nationality);
  }

  function openDrawer() {
    setError(null);
    setPending(false);
    setSelectedId(defaultIntegrationId ?? integrations[0]?.id ?? "");
    applyPerson(SANDBOX_PEOPLE[0]);
    dialogRef.current?.showModal();
    window.requestAnimationFrame(() => {
      formRef.current?.querySelector<HTMLInputElement>("input[name='first_name']")?.focus();
    });
  }

  function closeDrawer() {
    dialogRef.current?.close();
  }

  function onBackdropPointerDown(event: PointerEvent<HTMLDialogElement>) {
    if (event.target !== event.currentTarget) {
      return;
    }
    if (event.clientX === 0 && event.clientY === 0) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const inside =
      event.clientX >= rect.left &&
      event.clientX <= rect.right &&
      event.clientY >= rect.top &&
      event.clientY <= rect.bottom;
    if (!inside) {
      closeDrawer();
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const result = await createScreeningAction(new FormData(event.currentTarget));
      if (!result.ok) {
        setError(result.code === "forbidden" ? t("console.product.sessionsLocked") : t("console.product.amlCreateError"));
        return;
      }
      closeDrawer();
      router.push(`/aml/verifications/${result.data.id}`);
    } finally {
      setPending(false);
    }
  }

  const drawer = (
    <dialog
      ref={dialogRef}
      className="rm-drawer rm-drawer-wide"
      aria-labelledby="create-screening-title"
      onPointerDown={onBackdropPointerDown}
      onClose={() => {
        setError(null);
        setPending(false);
      }}
    >
      <div className="rm-drawer-head">
        <div>
          <p className="rm-drawer-kicker">{t("console.nav.aml")}</p>
          <h2 id="create-screening-title">{t("console.product.newVerification")}</h2>
          <p className="rm-lead">{t("console.product.amlCreateLead")}</p>
        </div>
        <button type="button" className="rm-dialog-close" aria-label={t("common.close")} onClick={closeDrawer}>
          <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true">
            <path
              d="M3 3l8 8M11 3l-8 8"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
          </svg>
        </button>
      </div>
      <form ref={formRef} onSubmit={onSubmit} className="rm-form rm-drawer-form">
        {integrations.length <= 1 ? <input type="hidden" name="integration_id" value={selected?.id ?? ""} /> : null}
        <div className="rm-drawer-body">
          {error ? (
            <p role="alert" className="rm-alert">
              {error}
            </p>
          ) : null}
          {integrations.length > 1 ? (
            <section className="rm-drawer-section">
              <label>
                {t("console.verifications.pickIntegration")}
                <span className="rm-hint">{t("console.verifications.pickIntegrationHint")}</span>
                <select
                  name="integration_id"
                  value={selectedId}
                  disabled={pending}
                  onPointerDown={(event) => event.stopPropagation()}
                  onClick={(event) => event.stopPropagation()}
                  onChange={(event) => setSelectedId(event.target.value)}
                >
                  {integrations.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} ({tIntegrationMode(t, item.mode)})
                    </option>
                  ))}
                </select>
              </label>
            </section>
          ) : null}
          <fieldset className="rm-drawer-section" disabled={pending}>
            <legend className="rm-drawer-section-title">{t("console.verifications.scenario")}</legend>
            <p className="rm-hint">{t("console.product.amlScenarioHint")}</p>
            <div className="rm-choice-list">
              {SANDBOX_PEOPLE.map((person) => (
                <label key={person.id} className="rm-choice">
                  <input
                    type="radio"
                    name="sandbox_person"
                    value={person.id}
                    checked={personId === person.id}
                    onChange={() => applyPerson(person)}
                  />
                  <span>
                    {person.first} {person.last}
                    {" · "}
                    {t(person.scenario)}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
          <section className="rm-drawer-section">
            <h3 className="rm-drawer-section-title">{t("console.product.amlSubject")}</h3>
            <div className="rm-drawer-row">
              <label>
                {t("console.verifications.firstName")}
                <RequiredMark />
                <input
                  name="first_name"
                  type="text"
                  required
                  autoComplete="given-name"
                  disabled={pending}
                  value={firstName}
                  onChange={(event) => setFirstName(event.target.value)}
                />
              </label>
              <label>
                {t("console.verifications.lastName")}
                <RequiredMark />
                <input
                  name="last_name"
                  type="text"
                  required
                  autoComplete="family-name"
                  disabled={pending}
                  value={lastName}
                  onChange={(event) => setLastName(event.target.value)}
                />
              </label>
            </div>
            <div className="rm-drawer-row">
              <label>
                {t("console.product.amlBirth")}
                <input
                  name="date_of_birth"
                  type="date"
                  disabled={pending}
                  value={birth}
                  onChange={(event) => setBirth(event.target.value)}
                />
              </label>
              <label>
                {t("console.product.amlNationality")}
                <input
                  name="nationality"
                  type="text"
                  maxLength={2}
                  autoCapitalize="characters"
                  disabled={pending}
                  placeholder="FR"
                  value={nationality}
                  onChange={(event) => setNationality(event.target.value.toUpperCase())}
                />
              </label>
            </div>
          </section>
        </div>
        <div className="rm-drawer-foot">
          <button type="button" data-variant="secondary" onClick={closeDrawer}>
            {t("common.cancel")}
          </button>
          <button type="submit" disabled={pending || !selected}>
            {pending ? t("console.product.amlPending") : t("console.product.amlSubmit")}
          </button>
        </div>
      </form>
    </dialog>
  );

  return (
    <>
      <button type="button" className="rm-button" onClick={openDrawer} disabled={integrations.length === 0}>
        {t("console.product.newVerification")}
      </button>
      {mounted ? createPortal(drawer, document.body) : null}
    </>
  );
}
