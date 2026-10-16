import Link from "next/link";

export function BackLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <p className="rm-back">
      <Link href={href}>
        <span className="rm-back-chevron" aria-hidden="true" />
        {children}
      </Link>
    </p>
  );
}
