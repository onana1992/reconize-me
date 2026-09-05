import Link from "next/link";
import { LoginForm } from "./login-form";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const params = await searchParams;
  const next = params.next && params.next.startsWith("/") ? params.next : "/";

  return (
    <main className="rm-card rm-auth-card">
      <h1>Connexion</h1>
      <p className="rm-lead">Accédez à la console de votre organisation.</p>
      <LoginForm next={next} />
      <p className="rm-auth-links">
        <Link href="/forgot">Mot de passe oublié</Link>
        {" · "}
        <Link href="/signup">Créer un compte</Link>
      </p>
    </main>
  );
}
