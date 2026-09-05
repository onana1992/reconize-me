"use client";

import Link from "next/link";
import { useState } from "react";
import { CopyLinkButton } from "../../../../components/copy-link-button";
import { createVerificationAction } from "../actions";
import type { Verification } from "../../../../lib/api";
import { formatUtc } from "../../../../lib/status";

function errorMessage(code: string, fallback: string): string {
  if (code === "unauthorized") {
    return "Session expirée";
  }
  if (code === "external_id_conflict") {
    return "Cet identifiant externe existe déjà pour votre organisation.";
  }
  if (code === "validation_error") {
    return "Vérifiez les champs saisis (identifiant, e-mail).";
  }
  return fallback;
}

export function NewVerificationForm() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<Verification | null>(null);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await createVerificationAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(errorMessage(result.code, result.message));
      return;
    }
    setCreated(result.data);
  }

  if (created) {
    return (
      <section aria-live="polite" className="rm-card rm-created">
        <h2>Vérification créée</h2>
        <p>
          Expire le <strong>{formatUtc(created.expires_at)}</strong>
        </p>
        {created.hosted_url ? (
          <div className="rm-hosted">
            <p className="rm-url">
              <a href={created.hosted_url} target="_blank" rel="noreferrer">
                {created.hosted_url}
              </a>
            </p>
            <CopyLinkButton url={created.hosted_url} />
          </div>
        ) : (
          <p>Lien expiré</p>
        )}
        <p>
          <Link href={`/verifications/${created.id}`}>Voir le dossier</Link>
        </p>
      </section>
    );
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        Identifiant externe
        <input name="external_id" autoComplete="off" maxLength={128} />
      </label>
      <label>
        Prénom
        <input name="first_name" autoComplete="given-name" maxLength={100} />
      </label>
      <label>
        Nom
        <input name="last_name" autoComplete="family-name" maxLength={100} />
      </label>
      <label>
        E-mail
        <input name="email" type="email" autoComplete="email" maxLength={255} />
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <button type="submit" disabled={pending}>
        {pending ? "Création…" : "Créer la vérification"}
      </button>
    </form>
  );
}
