"use client";

import React from "react";
import { TutorActiveClasses } from "@/features/connection";

export default function TutorClassesPage() {
  return (
    <div className="p-6 md:p-8 max-w-7xl mx-auto">
      <TutorActiveClasses />
    </div>
  );
}
