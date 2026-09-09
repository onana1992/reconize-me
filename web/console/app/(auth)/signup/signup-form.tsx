"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useT } from "../../../i18n/client";
import { RequiredMark } from "../../../components/required-mark";

export function SignupForm({ email = "" }: { email?: string }) {
  const t = useT();
  const router = useRouter();
  const [pending, setPending] = useState(false);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    const formData = new FormData(event.currentTarget);
    const nextEmail = String(formData.get("email") ?? "").trim();
    router.push(`/signup/setup?${new URLSearchParams({ email: nextEmail })}`);
  }

  return (
    <form onSubmit={onSubmit} className="rm-form">
      <label>
        <span>
          {t("signup.email")}
          <RequiredMark />
        </span>
        <input
          name="email"
          type="email"
          autoComplete="email"
          required
          maxLength={255}
          defaultValue={email}
          placeholder={t("signup.emailPlaceholder")}
        />
      </label>
      <button type="submit" disabled={pending}>
        {pending ? t("signup.pending") : t("signup.submit")}
      </button>
    </form>
  );
}
