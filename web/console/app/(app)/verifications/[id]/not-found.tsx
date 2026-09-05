import Link from "next/link";

export default function NotFound() {
  return (
    <main>
      <p className="rm-eyebrow">Vérifications</p>
      <h1>Introuvable</h1>
      <p className="rm-lead">
        Cette vérification n’existe pas ou n’appartient pas à votre organisation.
      </p>
      <p>
        <Link href="/verifications">Retour à la liste</Link>
      </p>
    </main>
  );
}
