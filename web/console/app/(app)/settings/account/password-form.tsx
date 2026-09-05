"use client";

import { useState } from "react";
import { changePasswordAction } from "../../verifications/actions";

export function PasswordForm() {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    setDone(false);
    const result = await changePasswordAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(result.code === "invalid_credentials" ? "Mot de passe actuel incorrect." : result.message);
      return;
    }
    setDone(true);
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        Mot de passe actuel
        <input name="current_password" type="password" autoComplete="current-password" required />
      </label>
      <label>
        Nouveau mot de passe
        <input name="new_password" type="password" autoComplete="new-password" required minLength={10} maxLength={128} />
        <span className="rm-hint">10 caractères minimum.</span>
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {done ? <p className="rm-notice">Mot de passe mis à jour.</p> : null}
      <button type="submit" disabled={pending}>
        {pending ? "Enregistrement…" : "Changer le mot de passe"}
      </button>
    </form>
  );
}
