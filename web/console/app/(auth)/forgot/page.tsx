import Link from "next/link";
import { ForgotForm } from "./forgot-form";

export default function ForgotPage() {
  return (
    <main className="rm-card rm-auth-card">
      <h1>Mot de passe oublié</h1>
      <p className="rm-lead">Indiquez l’e-mail du compte. Si un compte existe, un lien sera envoyé.</p>
      <ForgotForm />
      <p className="rm-auth-links">
        <Link href="/login">Retour à la connexion</Link>
      </p>
    </main>
  );
}
