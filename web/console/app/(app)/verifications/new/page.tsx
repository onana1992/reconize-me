import { PageHeader } from "../../../../components/page-header";
import { NewVerificationForm } from "./new-verification-form";

export default function NewVerificationPage() {
  return (
    <main>
      <PageHeader
        eyebrow="Vérifications"
        title="Nouvelle vérification"
        lead="Tous les champs sont optionnels. Un lien hosted flow est émis à la création."
      />
      <NewVerificationForm />
    </main>
  );
}
