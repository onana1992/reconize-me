import Link from "next/link";
import { notFound } from "next/navigation";
import { StatusBadge } from "@kyc/brand";
import { CopyLinkButton } from "../../../components/copy-link-button";
import { getVerification } from "../../../lib/api";
import { formatUtc, statusLabel, statusTone } from "../../../lib/status";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ id: string }> };

export default async function VerificationDetailPage({ params }: Props) {
  const { id } = await params;
  const result = await getVerification(id);

  if (!result.ok && result.status === 404) {
    notFound();
  }

  if (!result.ok) {
    const message = result.code === "unauthorized" ? "Session expirée" : result.message;
    return (
      <main>
        <p className="rm-eyebrow">Recogniz-Me</p>
        <h1>Vérification</h1>
        <p role="alert" className="rm-alert">
          {message}
        </p>
      </main>
    );
  }

  const verification = result.data;
  const applicant = verification.applicant;
  const hasApplicant = Boolean(applicant?.first_name || applicant?.last_name || applicant?.email);

  return (
    <main>
      <p className="rm-eyebrow">Recogniz-Me</p>
      <h1>Dossier</h1>
      <p>
        <StatusBadge label={statusLabel(verification.status)} tone={statusTone(verification.status)} />
      </p>
      <dl className="rm-details">
        <div>
          <dt>Identifiant</dt>
          <dd>
            <code>{verification.id}</code>
          </dd>
        </div>
        {verification.external_id ? (
          <div>
            <dt>Identifiant externe</dt>
            <dd>{verification.external_id}</dd>
          </div>
        ) : null}
        <div>
          <dt>Créé</dt>
          <dd>{formatUtc(verification.created_at)}</dd>
        </div>
        <div>
          <dt>Mis à jour</dt>
          <dd>{formatUtc(verification.updated_at)}</dd>
        </div>
        <div>
          <dt>Expire</dt>
          <dd>{formatUtc(verification.expires_at)}</dd>
        </div>
        {hasApplicant ? (
          <div>
            <dt>Applicant</dt>
            <dd>
              {[applicant?.first_name, applicant?.last_name].filter(Boolean).join(" ") || "—"}
              {applicant?.email ? (
                <>
                  <br />
                  {applicant.email}
                </>
              ) : null}
            </dd>
          </div>
        ) : null}
        <div>
          <dt>Lien hosted</dt>
          <dd>
            {verification.hosted_url ? (
              <>
                <p className="rm-url">
                  <a href={verification.hosted_url} target="_blank" rel="noreferrer">
                    {verification.hosted_url}
                  </a>
                </p>
                <CopyLinkButton url={verification.hosted_url} />
              </>
            ) : (
              <p>Lien expiré</p>
            )}
          </dd>
        </div>
      </dl>
      <p>
        <Link href="/verifications/new">Nouvelle vérification</Link>
      </p>
    </main>
  );
}
