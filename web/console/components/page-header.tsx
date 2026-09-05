import type { ReactNode } from "react";

type Props = {
  eyebrow?: string;
  title: string;
  lead?: ReactNode;
  actions?: ReactNode;
};

export function PageHeader({ eyebrow, title, lead, actions }: Props) {
  return (
    <header className="rm-page-head">
      <div>
        {eyebrow ? <p className="rm-eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
        {lead ? <p className="rm-lead">{lead}</p> : null}
      </div>
      {actions ? <div className="rm-page-head-actions">{actions}</div> : null}
    </header>
  );
}
