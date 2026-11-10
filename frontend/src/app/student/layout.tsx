import React from "react";
import { RoleLayout } from "@/shared/ui/RoleLayout";

export default function StudentLayout({ children }: { children: React.ReactNode }) {
  return <RoleLayout role="STUDENT">{children}</RoleLayout>;
}
