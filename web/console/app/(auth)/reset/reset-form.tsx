"use client";

import Link from "next/link";
import { useState } from "react";
import { resetAction } from "../actions";

export function ResetForm({ token }: { token: string }) {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await resetAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(
        result.code === "invalid_or_expired_token"
          ? "Lien invalide ou expiré."
          : result.message,
      );
      return;
    }
    setDone(true);
  }

  if (done) {
    return (
      <p className="rm-notice">
        Mot de passe mis à jour. <Link href="/login">Se connecter</Link>
      </p>
    );
  }

  return (
    <form action={onSubmit} className="rm-form">
      <input type="hidden" name="token" value={token} />
      <label>
        Nouveau mot de passe
        <input
          name="password"
          type="password"
          autoComplete="new-password"
          required
          minLength={10}
          maxLength={128}
        />
        <span className="rm-hint">10 caractères minimum.</span>
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <button type="submit" disabled={pending}>
        {pending ? "Enregistrement…" : "Réinitialiser"}
      </button>
    </form>
  );
}
