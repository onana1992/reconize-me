"use client";

import { useEffect, useState } from "react";

type Item = {
  at: string;
  signatureOk: boolean;
  event: string | null;
  body: string;
};

export function WebhookPanel() {
  const [items, setItems] = useState<Item[]>([]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const response = await fetch("/api/webhooks/recognizme");
      if (!response.ok || cancelled) {
        return;
      }
      const payload = (await response.json()) as { items?: Item[] };
      if (!cancelled) {
        setItems(payload.items ?? []);
      }
    }
    void load();
    const timer = window.setInterval(() => void load(), 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  const latest = items[0];

  return (
    <section className="webhook">
      <h2>Webhook live</h2>
      <p className="lead">
        Recogniz-Me poste <code>verification.completed</code> sur <code>/api/webhooks/recognizme</code>.
      </p>
      {latest ? (
        <>
          <p className={latest.signatureOk ? "ok" : "error"}>
            {latest.signatureOk ? "Signature valide" : "Signature refusée"} · {latest.event ?? "corps illisible"} ·{" "}
            {latest.at}
          </p>
          <pre>{latest.body}</pre>
        </>
      ) : (
        <p className="lead">Aucun envoi reçu.</p>
      )}
    </section>
  );
}
