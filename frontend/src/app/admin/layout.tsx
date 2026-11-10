import React from "react";
import { RoleLayout } from "@/shared/ui/RoleLayout";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <RoleLayout role="ADMIN">{children}</RoleLayout>;
}
