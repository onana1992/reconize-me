"use client";

import { useState } from "react";
import { loginAction } from "../actions";

export function LoginForm({ next }: { next: string }) {
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    const result = await loginAction(formData);
    setPending(false);
    if (result && !result.ok) {
      if (result.code === "email_unverified") {
        setError("Vérifiez votre e-mail avant de vous connecter.");
        return;
      }
      if (result.code === "invalid_credentials") {
        setError("Identifiants invalides.");
        return;
      }
      if (result.code === "rate_limited") {
        setError("Trop de tentatives. Réessayez dans quelques minutes.");
        return;
      }
      setError(result.message);
    }
  }

  return (
    <form action={onSubmit} className="rm-form">
      <input type="hidden" name="next" value={next} />
      <label>
        E-mail
        <input name="email" type="email" autoComplete="email" required maxLength={255} />
      </label>
      <label>
        Mot de passe
        <input name="password" type="password" autoComplete="current-password" required maxLength={128} />
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      <button type="submit" disabled={pending}>
        {pending ? "Connexion…" : "Se connecter"}
      </button>
    </form>
  );
}
