"use client";

import { useCallback, useState } from "react";
import { VerifyButton } from "./verify-button";

export default function HomePage() {
  const [mode, setMode] = useState<"redirect" | "incontext">("redirect");
  const [externalId, setExternalId] = useState("order-123");
  const [firstName, setFirstName] = useState("Ada");
  const [lastName, setLastName] = useState("Lovelace");
  const [email, setEmail] = useState("ada@example.com");
  const [error, setError] = useState<string | null>(null);
  const report = useCallback((message: string) => setError(message), []);

  return (
    <main>
      <h1>Vérifier une identité</h1>
      <p className="lead">
        Le bouton est celui de Recogniz-Me. Au clic, cette application crée la session sur son serveur, puis ouvre le
        parcours hébergé.
      </p>
      <form
        onSubmit={(event) => event.preventDefault()}
        onChange={() => setError(null)}
      >
        <fieldset>
          <label className="choice">
            <input
              type="radio"
              name="mode"
              checked={mode === "redirect"}
              onChange={() => setMode("redirect")}
            />
            Redirection
          </label>
          <label className="choice">
            <input
              type="radio"
              name="mode"
              checked={mode === "incontext"}
              onChange={() => setMode("incontext")}
            />
            InContext
          </label>
        </fieldset>
        <label>
          Identifiant externe
          <input value={externalId} onChange={(event) => setExternalId(event.target.value)} type="text" />
        </label>
        <label>
          Prénom
          <input value={firstName} onChange={(event) => setFirstName(event.target.value)} type="text" />
        </label>
        <label>
          Nom
          <input value={lastName} onChange={(event) => setLastName(event.target.value)} type="text" />
        </label>
        <label>
          E-mail
          <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" />
        </label>
      </form>
      <VerifyButton
        mode={mode}
        externalId={externalId}
        firstName={firstName}
        lastName={lastName}
        email={email}
        onError={report}
      />
      {error ? <p className="error">{error}</p> : null}
    </main>
  );
}
