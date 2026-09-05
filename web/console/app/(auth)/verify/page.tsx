import Link from "next/link";
import { SecretBlock } from "../../../components/secret-block";
import { verifyEmail } from "../actions";

export default async function VerifyPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const token = (await searchParams).token ?? "";

  if (!token) {
    return (
      <main className="rm-card rm-auth-card">
        <h1>Vérification</h1>
        <p className="rm-alert">Lien incomplet.</p>
      </main>
    );
  }

  const result = await verifyEmail(token);

  if (!result.ok) {
    return (
      <main className="rm-card rm-auth-card">
        <h1>Lien invalide</h1>
        <p>
          {result.code === "invalid_or_expired_token"
            ? "Ce lien a déjà été utilisé ou a expiré."
            : result.message}
        </p>
        <p className="rm-auth-links">
          <Link href="/login">Connexion</Link>
        </p>
      </main>
    );
  }

  return (
    <main className="rm-card rm-auth-card">
      <h1>E-mail confirmé</h1>
      {result.data.key ? (
        <SecretBlock
          title="Clé API Sandbox"
          description="Copiez-la maintenant. Elle ne sera plus montrée."
          value={result.data.key}
        />
      ) : (
        <p className="rm-notice">Votre compte est activé. Vous pouvez vous connecter.</p>
      )}
      <p>
        <Link href="/login" className="rm-button">
          Se connecter
        </Link>
      </p>
    </main>
  );
}
