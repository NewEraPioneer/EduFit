"use client";

import React from "react";
import Link from "next/link";
import Image from "next/image";

interface EduFitLogoProps {
  size?: "sm" | "md" | "lg";
  showText?: boolean;
  href?: string;
  className?: string;
  roleBadge?: string;
}

export function EduFitLogo({
  size = "md",
  showText = true,
  href = "/",
  className = "",
  roleBadge,
}: EduFitLogoProps) {
  const iconSizes = {
    sm: "w-6 h-6",
    md: "w-8 h-8",
    lg: "w-9 h-9",
  };

  const textSizes = {
    sm: "text-sm font-medium",
    md: "text-base font-medium",
    lg: "text-lg font-bold",
  };

  const logoContent = (
    <div className={`inline-flex min-w-0 items-center gap-2 group select-none shrink-0 ${className}`}>
      {/* Icon Logo EduFit chuẩn thương hiệu */}
      <div
        className={`${iconSizes[size]} relative shrink-0 flex items-center justify-center transition-transform duration-200`}
      >
        <Image
          src="/edufit-mark.png"
          alt={showText ? "" : "EduFit"}
          width={36}
          height={36}
          className="w-full h-full object-contain block"
          priority
        />
      </div>

      {/* Tên thương hiệu EduFit */}
      <div
        aria-hidden={!showText}
        className={`flex flex-col justify-center overflow-hidden whitespace-nowrap leading-tight transition-[max-width,opacity,transform] duration-200 ease-in-out ${
          showText ? "max-w-40 translate-x-0 opacity-100" : "pointer-events-none max-w-0 -translate-x-2 opacity-0"
        }`}
      >
        <span
          className={`font-heading tracking-tight text-ink group-hover:text-action-strong transition-colors ${textSizes[size]}`}
        >
          Edu<span className="text-action-strong">Fit</span>
        </span>
        {roleBadge && (
          <span className="text-xs text-muted font-medium tracking-normal -mt-0.5">
            {roleBadge}
          </span>
        )}
      </div>
    </div>
  );

  if (href) {
    return (
      <Link href={href} className="inline-flex items-center shrink-0">
        {logoContent}
      </Link>
    );
  }

  return logoContent;
}
