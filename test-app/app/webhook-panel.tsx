"use client";

import { useCallback, useState } from "react";

type Item = {
  at: string;
  signatureOk: boolean;
  event: string | null;
  body: string;
};

export function WebhookPanel() {
  const [items, setItems] = useState<Item[]>([]);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const response = await fetch("/api/webhooks/recognizme", { cache: "no-store" });
      if (!response.ok) {
        return;
      }
      const payload = (await response.json()) as { items?: Item[] };
      setItems(payload.items ?? []);
    } catch {
      // ignore
    } finally {
      setBusy(false);
    }
  }, []);

  const latest = items[0];

  return (
    <section className="webhook">
      <h2>Webhook live</h2>
      <p className="lead">
        Recogniz-Me poste <code>verification.completed</code> sur <code>/api/webhooks/recognizme</code>.
      </p>
      <button type="button" className="verify" disabled={busy} onClick={() => void load()}>
        {busy ? "Chargement…" : "Rafraîchir le webhook"}
      </button>
      {latest ? (
        <>
          <p className={latest.signatureOk ? "ok" : "error"}>
            {latest.signatureOk ? "Signature valide" : "Signature refusée"} · {latest.event ?? "corps illisible"} ·{" "}
            {latest.at}
          </p>
          <pre>{latest.body}</pre>
        </>
      ) : (
        <p className="lead">Aucun envoi affiché. Cliquez sur rafraîchir après une vérification.</p>
      )}
    </section>
  );
}
