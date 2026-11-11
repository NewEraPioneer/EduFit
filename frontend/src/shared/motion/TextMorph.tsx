"use client";

// Chuyển thể từ Motion Primitives (MIT, ibelick/motion-primitives).
import { AnimatePresence, motion } from "motion/react";
import { useId, useMemo } from "react";

export function TextMorph({ children, className }: { children: string; className?: string }) {
  const uniqueId = useId();
  const characters = useMemo(() => {
    const counts: Record<string, number> = {};
    return children.split("").map((char) => {
      const lower = char.toLowerCase();
      counts[lower] = (counts[lower] || 0) + 1;
      return { id: `${uniqueId}-${lower}${counts[lower]}`, label: char === " " ? " " : char };
    });
  }, [children, uniqueId]);

  return (
    <span className={className} aria-label={children} role="text">
      <AnimatePresence mode="popLayout" initial={false}>
        {characters.map((character) => (
          <motion.span
            key={character.id}
            layoutId={character.id}
            className="inline-block"
            aria-hidden="true"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ type: "spring", stiffness: 280, damping: 18, mass: 0.3 }}
          >
            {character.label}
          </motion.span>
        ))}
      </AnimatePresence>
    </span>
  );
}
