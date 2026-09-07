"use client";

import { logoutAction } from "../app/(auth)/actions";
import { useT } from "../i18n/client";
import { NavIcon } from "./nav-icons";

export function LogoutButton() {
  const t = useT();

  return (
    <form action={logoutAction}>
      <button type="submit" data-variant="secondary" className="rm-logout" aria-label={t("common.logout")}>
        <NavIcon name="logout" />
        <span>{t("common.logout")}</span>
      </button>
    </form>
  );
}
