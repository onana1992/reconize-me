"use client";

import { useState } from "react";
import { PasswordInput } from "../../../../components/password-input";
import { RequiredMark } from "../../../../components/required-mark";
import { NEW_PASSWORD_ATTRS } from "../../../../lib/password";
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
        <span>
          Mot de passe actuel
          <RequiredMark />
        </span>
        <PasswordInput name="current_password" autoComplete="current-password" required />
      </label>
      <label>
        <span>
          Nouveau mot de passe
          <RequiredMark />
        </span>
        <PasswordInput
          name="new_password"
          autoComplete="new-password"
          required
          title="12 caractères min., avec majuscule, minuscule, chiffre et caractère spécial."
          {...NEW_PASSWORD_ATTRS}
        />
        <span className="rm-hint rm-hint-danger">
          12 caractères min., avec majuscule, minuscule, chiffre et caractère spécial.
        </span>
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
