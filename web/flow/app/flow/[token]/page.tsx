type Props = { params: Promise<{ token: string }> };

export default async function ConsentPage({ params }: Props) {
  const { token } = await params;
  const short = token.length > 8 ? `${token.slice(0, 8)}…` : token;

  return (
    <main style={{ padding: "2rem", maxWidth: 480, margin: "0 auto" }}>
      <p style={{ letterSpacing: "0.08em", fontSize: 12, color: "#6b6560" }}>RECOGNIZ-ME</p>
      <h1>Consentement</h1>
      <p>
        Session <code>{short}</code>. La capture document arrive au sprint 2.
      </p>
      <form>
        <label style={{ display: "flex", gap: 8, alignItems: "flex-start", margin: "1.5rem 0" }}>
          <input type="checkbox" name="accept" disabled />
          <span>
            J’accepte que mes pièces d’identité et données biométriques soient traitées pour vérifier mon identité
            (texte <code>consent-v1</code>).
          </span>
        </label>
        <button type="button" disabled>
          Continuer
        </button>
      </form>
    </main>
  );
}
