import React from "react";
import { RoleLayout } from "@/shared/ui/RoleLayout";

export default function TutorLayout({ children }: { children: React.ReactNode }) {
  return <RoleLayout role="TUTOR">{children}</RoleLayout>;
}
