import Link from "next/link";
import { StatusBadge } from "@kyc/brand";
import { PageHeader } from "../../../components/page-header";
import { listVerifications } from "../../../lib/api";
import { sessionCookieHeader } from "../../../lib/session";
import { formatUtc, statusLabel, statusTone } from "../../../lib/status";

export const dynamic = "force-dynamic";

export default async function VerificationsPage() {
  const result = await listVerifications(await sessionCookieHeader());

  return (
    <main>
      <PageHeader
        eyebrow="Console"
        title="Vérifications"
        lead="Dossiers de votre organisation."
        actions={
          <Link href="/verifications/new" className="rm-button">
            Nouvelle vérification
          </Link>
        }
      />
      {!result.ok ? (
        <p role="alert" className="rm-alert">
          {result.message}
        </p>
      ) : result.data.data.length === 0 ? (
        <div className="rm-empty">
          <p>Aucune vérification.</p>
          <p>
            <Link href="/verifications/new">Créer la première</Link>
          </p>
        </div>
      ) : (
        <div className="rm-table-wrap">
          <table className="rm-table">
            <thead>
              <tr>
                <th>Statut</th>
                <th>Identifiant</th>
                <th>Créé</th>
              </tr>
            </thead>
            <tbody>
              {result.data.data.map((row) => (
                <tr key={row.id}>
                  <td>
                    <StatusBadge label={statusLabel(row.status)} tone={statusTone(row.status)} />
                  </td>
                  <td>
                    <Link href={`/verifications/${row.id}`}>
                      <code>{row.id.slice(0, 8)}</code>
                    </Link>
                  </td>
                  <td>{formatUtc(row.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
