import { CopyButton } from "./copy-button";

type Props = {
  title: string;
  description: string;
  value: string;
  copyLabel?: string;
};

export function SecretBlock({ title, description, value, copyLabel }: Props) {
  return (
    <section className="rm-card rm-secret-card">
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="rm-secret">
        <code>{value}</code>
        <CopyButton value={value} label={copyLabel} />
      </div>
    </section>
  );
}
