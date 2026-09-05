import { PageHeader } from "../../../../components/page-header";
import { SettingsNav } from "../../../../components/settings-nav";
import { roleLabel } from "../../../../lib/labels";
import { requireMe } from "../../../../lib/session";
import { PasswordForm } from "./password-form";

export default async function AccountPage() {
  const me = await requireMe();

  return (
    <main>
      <PageHeader
        eyebrow="Paramètres"
        title="Compte"
        lead={
          <>
            Connecté en tant que <strong>{me.email}</strong> ({roleLabel(me.role)}).
          </>
        }
      />
      <SettingsNav />
      <section className="rm-section">
        <h2>Mot de passe</h2>
        <PasswordForm />
      </section>
    </main>
  );
}
