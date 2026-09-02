import { headers } from "next/headers";
import { fetchFlowSession } from "../../../lib/api";
import { FlowShell } from "../../../components/flow-shell";
import { ConsentScreen, type FlowCopy } from "./consent-form";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ token: string }> };

type PageCopy = FlowCopy & {
  expiredTitle: string;
  expired: string;
  invalidTitle: string;
  invalid: string;
  errorTitle: string;
};

const COPY: Record<"fr" | "en", PageCopy> = {
  fr: {
    consentTitle: "Consentement",
    consentText:
      "En continuant, vous acceptez que vos pièces d’identité et données biométriques soient traitées pour vérifier votre identité pour le compte de l’entreprise qui vous a envoyé ce lien.",
    accept: "J’accepte",
    decline: "Je refuse",
    thanksTitle: "Merci",
    thanksBody: "Merci. La capture document arrive au sprint 2. La suite sera bientôt disponible.",
    declinedTitle: "Refus enregistré",
    declined: "Vous avez refusé le traitement de vos données.",
    error: "Une erreur s’est produite. Réessayez.",
    expiredTitle: "Lien expiré",
    expired: "Ce lien a expiré, contactez l’entreprise.",
    invalidTitle: "Lien invalide",
    invalid: "Vérifiez l’URL reçue, ou contactez l’entreprise.",
    errorTitle: "Erreur",
  },
  en: {
    consentTitle: "Consent",
    consentText:
      "By continuing, you agree that your identity documents and biometric data may be processed to verify your identity on behalf of the company that sent you this link.",
    accept: "I accept",
    decline: "I decline",
    thanksTitle: "Thank you",
    thanksBody: "Thank you. Document capture comes in sprint 2. The next step will be available soon.",
    declinedTitle: "Decision recorded",
    declined: "You declined processing of your data.",
    error: "Something went wrong. Please try again.",
    expiredTitle: "Link expired",
    expired: "This link has expired. Please contact the company.",
    invalidTitle: "Invalid link",
    invalid: "Check the URL you received, or contact the company.",
    errorTitle: "Error",
  },
};

export default async function ConsentPage({ params }: Props) {
  const { token } = await params;
  const headerList = await headers();
  const lang = (headerList.get("accept-language") ?? "fr").toLowerCase().startsWith("en") ? "en" : "fr";
  const copy = COPY[lang];
  const result = await fetchFlowSession(token);

  if (!result.ok) {
    if (result.status === 410 || result.code === "hosted_link_expired") {
      return (
        <FlowShell title={copy.expiredTitle}>
          <p>{copy.expired}</p>
        </FlowShell>
      );
    }
    if (result.status === 404) {
      return (
        <FlowShell title={copy.invalidTitle}>
          <p>{copy.invalid}</p>
        </FlowShell>
      );
    }
    return (
      <FlowShell title={copy.errorTitle}>
        <p>{copy.error}</p>
      </FlowShell>
    );
  }

  if (result.session.status === "pending_applicant") {
    return (
      <FlowShell title={copy.thanksTitle}>
        <p>{copy.thanksBody}</p>
      </FlowShell>
    );
  }

  if (result.session.status === "declined") {
    return (
      <FlowShell title={copy.declinedTitle}>
        <p>{copy.declined}</p>
      </FlowShell>
    );
  }

  if (result.session.status === "expired") {
    return (
      <FlowShell title={copy.expiredTitle}>
        <p>{copy.expired}</p>
      </FlowShell>
    );
  }

  return <ConsentScreen token={token} copy={copy} />;
}
