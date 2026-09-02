import type { ReactNode } from "react";
import { Logo } from "@kyc/brand";

type Props = { title: string; children: ReactNode };

/** Cadre unique du parcours applicant : marque, titre, contenu. Aucun chrome d'application. */
export function FlowShell({ title, children }: Props) {
  return (
    <main className="rm-flow">
      <p className="rm-flow-brand">
        <Logo size={22} />
        <span>Recogniz-Me</span>
      </p>
      <h1>{title}</h1>
      {children}
    </main>
  );
}
