import { Logo } from "@kyc/brand";

export default function Loading() {
  return (
    <main className="rm-flow">
      <p className="rm-flow-brand">
        <Logo size={22} />
        <span>Recogniz-Me</span>
      </p>
      <div aria-hidden className="rm-skeleton rm-flow-skeleton-title" />
      <div aria-hidden className="rm-skeleton rm-flow-skeleton-body" />
    </main>
  );
}
