"use client";

import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

/*
 * Apparition au défilement.
 *
 * Le seul composant client du contenu, et le seul mouvement du site : le bloc
 * monte de quelques pixels en s'opacifiant, une fois, quand il entre dans le
 * champ. Il n'y a pas de parallaxe, pas de compteur qui s'incrémente, pas de
 * bloc qui arrive par la droite.
 *
 * Trois garde-fous, parce qu'une animation ne doit jamais pouvoir cacher du
 * contenu :
 *
 *   - `prefers-reduced-motion` neutralise l'état de départ (côté CSS) ;
 *   - sans `IntersectionObserver`, on révèle immédiatement ;
 *   - sans JavaScript du tout, la règle `<noscript>` du layout rend visible.
 */

/* Le bloc se révèle un peu avant d'être franchement à l'écran : sinon on voit
   l'animation démarrer au lieu de voir un contenu déjà en place. */
const ROOT_MARGIN = "0px 0px -10% 0px";

export function Reveal({
  children,
  /** Décalage en millisecondes. Sert à faire tomber une liste en cascade. */
  delay = 0,
  className,
}: {
  children: ReactNode;
  delay?: number;
  className?: string;
}) {
  const node = useRef<HTMLDivElement>(null);
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    const element = node.current;
    if (!element) {
      return;
    }

    if (typeof IntersectionObserver === "undefined") {
      setRevealed(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setRevealed(true);
          observer.disconnect();
        }
      },
      { rootMargin: ROOT_MARGIN, threshold: 0.05 },
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={node}
      className={className ? `rm-reveal ${className}` : "rm-reveal"}
      data-revealed={revealed ? "true" : "false"}
      style={delay > 0 ? ({ "--rm-reveal-delay": `${delay}ms` } as CSSProperties) : undefined}
    >
      {children}
    </div>
  );
}
