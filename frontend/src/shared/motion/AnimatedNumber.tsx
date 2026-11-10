"use client";

// Chuyển thể từ Motion Primitives (MIT, ibelick/motion-primitives): bỏ motion.create trong render để không remount.
import { motion, useSpring, useTransform, type SpringOptions } from "motion/react";
import { useEffect } from "react";
import { cn } from "@/lib/utils";

export function AnimatedNumber({
  value,
  className,
  springOptions = { stiffness: 90, damping: 18 },
}: {
  value: number;
  className?: string;
  springOptions?: SpringOptions;
}) {
  const spring = useSpring(0, springOptions);
  const display = useTransform(spring, (current) => Math.round(current).toLocaleString("vi-VN"));

  useEffect(() => {
    spring.set(value);
  }, [spring, value]);

  return <motion.span className={cn("tabular-nums", className)}>{display}</motion.span>;
}
