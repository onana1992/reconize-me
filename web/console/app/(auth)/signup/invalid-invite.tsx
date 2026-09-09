import Link from "next/link";

export function InvalidInvite({
  title,
  lead,
  login,
}: {
  title: string;
  lead: string;
  login: string;
}) {
  return (
    <main className="rm-card rm-auth-card">
      <h1>{title}</h1>
      <p className="rm-alert">{lead}</p>
      <p className="rm-auth-links">
        <Link href="/login">{login}</Link>
      </p>
    </main>
  );
}
