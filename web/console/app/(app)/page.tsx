import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../components/page-header";
import { roleLabel } from "../../lib/labels";
import { requireMe } from "../../lib/session";

export default async function HomePage() {
  const me = await requireMe();

  return (
    <main>
      <PageHeader
        eyebrow={me.organization.slug}
        title={me.organization.name}
        lead={
          <>
            Connecté en tant que {me.email} · {roleLabel(me.role)}
          </>
        }
        actions={
          <Link href="/verifications/new" className="rm-button">
            Nouvelle vérification
          </Link>
        }
      />
      <p className="rm-plan">
        <StatusBadge label="Sandbox" tone="info" />
        <span className="rm-lead">usage 0 / — jusqu’à la souscription.</span>
      </p>
      {me.role === "owner" ? (
        <p className="rm-banner">L’authentification à deux facteurs sera exigée plus tard.</p>
      ) : null}
      <div className="rm-link-grid">
        <Link href="/verifications" className="rm-card rm-link-card">
          <h2>Vérifications</h2>
          <p>Liste des dossiers et liens hosted flow.</p>
        </Link>
        <Link href="/settings/keys" className="rm-card rm-link-card">
          <h2>Clés API</h2>
          <p>Préfixes visibles. Le secret n’est montré qu’à l’émission.</p>
        </Link>
        <Link href="/settings/team" className="rm-card rm-link-card">
          <h2>Équipe</h2>
          <p>Membres et invitations de l’organisation.</p>
        </Link>
      </div>
    </main>
  );
}
