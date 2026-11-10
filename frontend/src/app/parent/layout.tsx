import React from "react";
import { RoleLayout } from "@/shared/ui/RoleLayout";

export default function ParentLayout({ children }: { children: React.ReactNode }) {
  return <RoleLayout role="PARENT">{children}</RoleLayout>;
}
