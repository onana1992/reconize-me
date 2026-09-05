import Link from "next/link";
import { ResendForm } from "./resend-form";

export default async function VerifyPendingPage({
  searchParams,
}: {
  searchParams: Promise<{ email?: string }>;
}) {
  const email = (await searchParams).email ?? "";

  return (
    <main className="rm-card rm-auth-card">
      <h1>Vérifiez votre boîte mail</h1>
      <p className="rm-lead">
        {email
          ? `Un lien de confirmation a été envoyé à ${email}. Ouvrez-le pour activer le compte.`
          : "Un lien de confirmation a été envoyé. Ouvrez-le pour activer le compte."}
      </p>
      {email ? <ResendForm email={email} /> : null}
      <p className="rm-auth-links">
        <Link href="/login">Connexion</Link>
      </p>
    </main>
  );
}
