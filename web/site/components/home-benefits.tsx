import { Band, BandHead } from "./band";
import { Glyph, type GlyphName } from "./illustration";
import { Reveal } from "./reveal";
import type { Messages } from "../lib/messages";

/*
 * Quatre bénéfices — grille de cartes, comme sur les pages IDV du marché.
 */

export function HomeBenefits({ copy }: { copy: Messages["home"] }) {
  return (
    <Band tone="paper" labelledBy="home-benefits-title">
      <Reveal>
        <BandHead
          id="home-benefits-title"
          title={copy.benefitsTitle}
          lead={copy.benefitsLead}
        />
      </Reveal>

      <ul className="rm-benefits">
        {copy.benefits.map((benefit, index) => (
          <li key={benefit.title}>
            <Reveal delay={index * 60}>
              <div className="rm-benefit">
                <Glyph name={benefit.glyph as GlyphName} />
                <h3>{benefit.title}</h3>
                <p>{benefit.body}</p>
              </div>
            </Reveal>
          </li>
        ))}
      </ul>
    </Band>
  );
}
