"use client";

import { logoutAction } from "../app/(auth)/actions";

export function LogoutButton() {
  return (
    <form action={logoutAction}>
      <button type="submit" data-variant="secondary">
        Déconnexion
      </button>
    </form>
  );
}
