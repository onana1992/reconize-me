"use client";

// https://recogniz-me-api.onrender.com/v1/plugin.js
// Chargé dans layout.tsx. Le libellé et le style restent dans cette application.

declare global {
  interface Window {
    RecognizMe: {
      open: (options: {
        mode: "redirect" | "incontext";
        endpoint: string;
        externalId?: string;
        firstName?: string;
        lastName?: string;
        email?: string;
      }) => Promise<void>;
    };
  }
}

type Props = {
  mode: "redirect" | "incontext";
  externalId: string;
  firstName: string;
  lastName: string;
  email: string;
  onError: (message: string) => void;
};

export function Verify({ mode, externalId, firstName, lastName, email, onError }: Props) {
  function verify() {
    window.RecognizMe.open({
      mode,
      endpoint: "/api/verifications",
      externalId,
      firstName,
      lastName,
      email,
    }).catch(function (error: { message?: string }) {
      console.error(error.message);
      onError(error.message ?? "La session n’a pas pu être créée.");
    });
  }

  return (
    <button type="button" className="verify" onClick={verify}>
      Vérifier l’identité
    </button>
  );
}
