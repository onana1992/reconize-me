"use client";

// https://js.recogniz.me/v1/plugin.js
// Chargé localement dans layout.tsx (/recogniz-me.js).
// Votre bouton. Le libellé et le style restent dans votre interface.

declare global {
  var RecognizMe: {
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
    RecognizMe.open({
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
