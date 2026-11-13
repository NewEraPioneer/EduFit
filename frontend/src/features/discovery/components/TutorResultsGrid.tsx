"use client";

import React from "react";
import type { TutorDiscoveryCard } from "../types";
import { TutorCard } from "./TutorCard";
import type { TutorViewMode } from "./TutorResultsToolbar";

export interface TutorResultsGridProps {
  tutors: TutorDiscoveryCard[];
  viewMode: TutorViewMode;
  onViewDetail: (tutorId: string) => void;
  onRequestConnect: (tutorId: string) => void;
}

export function TutorResultsGrid({ tutors, viewMode, onViewDetail, onRequestConnect }: TutorResultsGridProps) {
  return (
    <div className={viewMode === "grid" ? "grid gap-4 sm:grid-cols-2 xl:grid-cols-3" : "space-y-3"}>
      {tutors.map((tutor) => (
        <TutorCard
          key={tutor.tutorId}
          tutor={tutor}
          layout={viewMode}
          onViewDetail={onViewDetail}
          onRequestConnect={onRequestConnect}
        />
      ))}
    </div>
  );
}
