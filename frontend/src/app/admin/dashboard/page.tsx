"use client";

import React from "react";
import { useAuth } from "@/context/AuthContext";
import { AdminDashboardView } from "@/features/admin";

export default function AdminDashboardPage() {
  const { user } = useAuth();
  if (!user) return null;
  return <AdminDashboardView user={user} />;
}
