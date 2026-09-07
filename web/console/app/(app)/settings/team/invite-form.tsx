"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { RequiredMark } from "../../../../components/required-mark";
import { inviteMemberAction } from "../actions";

export function InviteForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(formData: FormData) {
    setPending(true);
    setError(null);
    setDone(false);
    const result = await inviteMemberAction(formData);
    setPending(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setDone(true);
    router.refresh();
  }

  return (
    <form action={onSubmit} className="rm-form">
      <label>
        <span>
          E-mail
          <RequiredMark />
        </span>
        <input name="email" type="email" required maxLength={255} />
      </label>
      {error ? (
        <p role="alert" className="rm-alert">
          {error}
        </p>
      ) : null}
      {done ? <p className="rm-notice">Invitation envoyée.</p> : null}
      <button type="submit" disabled={pending}>
        {pending ? "Envoi…" : "Inviter"}
      </button>
    </form>
  );
}
