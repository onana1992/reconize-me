"use client";

import { useId, useRef, useState, type KeyboardEvent } from "react";
import { Band, BandHead } from "./band";
import { Glyph, type GlyphName } from "./illustration";
import type { Messages } from "../lib/messages";

/*
 * Services — fond vert (encre), onglets à gauche, panneau de contenu sur
 * l’espace horizontal restant. Trois services en parallèle de structure.
 */

type Service = Messages["home"]["services"][number];

export function HomeServices({ copy }: { copy: Messages["home"] }) {
  const base = useId();
  const [active, setActive] = useState(0);
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  const services = copy.services;

  const move = (to: number) => {
    const next = (to + services.length) % services.length;
    setActive(next);
    buttons.current[next]?.focus();
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
      event.preventDefault();
      move(active - 1);
    } else if (event.key === "ArrowDown" || event.key === "ArrowRight") {
      event.preventDefault();
      move(active + 1);
    } else if (event.key === "Home") {
      event.preventDefault();
      move(0);
    } else if (event.key === "End") {
      event.preventDefault();
      move(services.length - 1);
    }
  };

  return (
    <Band tone="ink" labelledBy="home-services-title">
      <BandHead
        id="home-services-title"
        title={copy.servicesTitle}
        lead={copy.servicesLead}
      />

      <div className="rm-services">
        <div
          className="rm-services-nav"
          role="tablist"
          aria-label={copy.servicesTabsLabel}
          aria-orientation="vertical"
          onKeyDown={onKeyDown}
        >
          {services.map((service, position) => (
            <button
              key={service.key}
              ref={(node) => {
                buttons.current[position] = node;
              }}
              type="button"
              role="tab"
              id={`${base}-tab-${service.key}`}
              className="rm-services-tab"
              aria-selected={position === active}
              aria-controls={`${base}-panel-${service.key}`}
              tabIndex={position === active ? 0 : -1}
              onClick={() => setActive(position)}
            >
              <span className="rm-services-tab-index" aria-hidden="true">
                {String(position + 1).padStart(2, "0")}
              </span>
              <span className="rm-services-tab-label">{service.nav}</span>
            </button>
          ))}
        </div>

        <div className="rm-services-stage">
          {services.map((service, position) => (
            <div
              key={service.key}
              role="tabpanel"
              id={`${base}-panel-${service.key}`}
              className="rm-services-panel"
              aria-labelledby={`${base}-tab-${service.key}`}
              hidden={position !== active}
              tabIndex={0}
            >
              <ServicePanel service={service} />
            </div>
          ))}
        </div>
      </div>
    </Band>
  );
}

function ServicePanel({ service }: { service: Service }) {
  return (
    <>
      <header className="rm-services-header">
        <p className="rm-services-badge">{service.badge}</p>
        <h3 className="rm-services-title">{service.title}</h3>
        <p className="rm-services-summary">{service.summary}</p>
      </header>

      <ol className="rm-services-cards">
        {service.cards.map((card, index) => (
          <li key={card.title} className="rm-services-card">
            <div className="rm-services-card-top">
              <span className="rm-services-card-index" aria-hidden="true">
                {String(index + 1).padStart(2, "0")}
              </span>
              <Glyph name={card.glyph as GlyphName} />
            </div>
            <h4>{card.title}</h4>
            <p>{card.body}</p>
          </li>
        ))}
      </ol>
    </>
  );
}
