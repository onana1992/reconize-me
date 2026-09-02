import Link from "next/link";

export default function HomePage() {
  return (
    <main>
      <p className="rm-eyebrow">Recogniz-Me</p>
      <h1>Console</h1>
      <p className="rm-lead">Créez une vérification, copiez le lien hosted flow, suivez le dossier.</p>
      <p>
        <Link href="/verifications/new" className="rm-button">
          Nouvelle vérification
        </Link>
      </p>
    </main>
  );
}
