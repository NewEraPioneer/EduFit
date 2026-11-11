"use client";

import React from "react";
import { AuthMascot } from "@/shared/motion/AuthMascot";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";

interface AuthCardHeaderProps {
  title: string;
  subtitle: string;
}

export function AuthCardHeader({ title, subtitle }: AuthCardHeaderProps) {
  return (
    <div className="text-center space-y-2">
      <div className="flex items-center justify-center gap-3 pb-1">
        <AuthMascot />
        <EduFitLogo href="/" size="lg" />
      </div>
      <h1 className="text-2xl font-bold font-heading text-ink">{title}</h1>
      <p className="text-sm text-muted">{subtitle}</p>
    </div>
  );
}
