import { NewVerificationForm } from "./new-verification-form";

export default function NewVerificationPage() {
  return (
    <main>
      <p className="rm-eyebrow">Recogniz-Me</p>
      <h1>Nouvelle vérification</h1>
      <p className="rm-lead">
        Tous les champs sont optionnels. Un lien hosted flow est émis à la création.
      </p>
      <NewVerificationForm />
    </main>
  );
}
