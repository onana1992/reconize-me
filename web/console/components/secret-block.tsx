import { CopyButton } from "./copy-button";

type Props = {
  title?: string;
  description: string;
  value: string;
  copyLabel?: string;
};

export function SecretBlock({ title, description, value, copyLabel }: Props) {
  return (
    <section className="rm-card rm-secret-card">
      {title ? <h3>{title}</h3> : null}
      <p className="rm-lead">{description}</p>
      <div className="rm-secret">
        <code>{value}</code>
        <CopyButton value={value} label={copyLabel} />
      </div>
    </section>
  );
}
