"use client";

import type { StaticImageData } from "next/image";
import Image from "next/image";
import { Reveal } from "./reveal";
import { Tabs } from "./tabs";
import type { Messages } from "../lib/messages";
import banking from "../public/media/industry-banking.jpg";
import commerce from "../public/media/industry-commerce.jpg";
import hr from "../public/media/industry-hr.jpg";
import insurance from "../public/media/industry-insurance.jpg";
import mobility from "../public/media/industry-mobility.jpg";
import telecom from "../public/media/industry-telecom.jpg";

/*
 * Industries — colonnes texte / média, points en cartes.
 * Pas de témoignage inventé (CDC §8.3). Aucun visage (RG-BRAND-12).
 */

const PHOTOS: Record<string, StaticImageData> = {
  banking,
  insurance,
  telecom,
  commerce,
  mobility,
  hr,
};

type Industry = Messages["home"]["industries"][number];

export function HomeIndustries({ copy }: { copy: Messages["home"] }) {
  return (
    <section className="rm-band rm-industries" data-tone="stone" aria-labelledby="home-industries-title">
      <div className="rm-shell">
        <div className="rm-band-head">
          <h2 id="home-industries-title" className="rm-title">
            {copy.industriesTitle}
          </h2>
          <p className="rm-lead">{copy.industriesLead}</p>
        </div>

        <Tabs
          label={copy.industriesTabsLabel}
          tabs={copy.industries.map((industry) => ({
            key: industry.key,
            label: industry.label,
            panel: (
              <IndustryStage industry={industry} asideEyebrow={copy.industriesAsideEyebrow} />
            ),
          }))}
        />
      </div>
    </section>
  );
}

function IndustryStage({
  industry,
  asideEyebrow,
}: {
  industry: Industry;
  asideEyebrow: string;
}) {
  const photo = PHOTOS[industry.key];

  return (
    <div className="rm-industry-stage">
      <div className="rm-industry-copy">
        <p className="rm-industry-body">{industry.body}</p>
        <ul className="rm-industry-points">
          {industry.points.map((point, index) => (
            <li key={point}>
              <Reveal delay={index * 50}>
                <div className="rm-industry-point">
                  <span className="rm-industry-point-mark" aria-hidden="true" />
                  <span className="rm-industry-point-text">{point}</span>
                </div>
              </Reveal>
            </li>
          ))}
        </ul>
      </div>

      <div className="rm-industry-media">
        {photo ? (
          <div className="rm-industry-photo" aria-hidden="true">
            <Image
              src={photo}
              alt=""
              fill
              sizes="(min-width: 64rem) 34rem, 100vw"
              className="rm-industry-photo-image"
            />
          </div>
        ) : null}
        <aside className="rm-industry-aside">
          <p className="rm-industry-aside-eyebrow">{asideEyebrow}</p>
          <p className="rm-industry-aside-body">{industry.aside}</p>
        </aside>
      </div>
    </div>
  );
}
