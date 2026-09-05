import Link from "next/link";
import { ResetForm } from "./reset-form";

export default async function ResetPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const token = (await searchParams).token ?? "";

  return (
    <main className="rm-card rm-auth-card">
      <h1>Nouveau mot de passe</h1>
      {token ? (
        <ResetForm token={token} />
      ) : (
        <p className="rm-alert">Lien incomplet. Demandez un nouvel e-mail.</p>
      )}
      <p className="rm-auth-links">
        <Link href="/login">Connexion</Link>
      </p>
    </main>
  );
}
