"use client";

import React, { useEffect, useState } from "react";
import { ScheduleCalendarView, ConnectedClass } from "@/features/scheduling";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { profileService, TutorAvailabilitySlot } from "@/services/profile";

export default function TutorSchedulePage() {
  const [availabilitySlots, setAvailabilitySlots] = useState<TutorAvailabilitySlot[]>([]);
  const [classes, setClasses] = useState<ConnectedClass[]>([]);

  useEffect(() => {
    profileService
      .getMyTutorProfile()
      .then((profile) => setAvailabilitySlots(profile.availabilitySlots || []))
      .catch(() => setAvailabilitySlots([]));

    connectionApi
      .getTutorClasses()
      .then((data) => {
        const classList = data || [];
        const mapped: ConnectedClass[] = classList.map((c) => ({
          classId: c.classId,
          title: c.subjectName
            ? `${c.subjectName} - ${c.educationLevelName || "Cấp học"} • ${c.studentDisplayName || "Học sinh"}`
            : (c.studentDisplayName || "Lớp học"),
          tutorId: c.tutorId,
          studentId: c.studentId,
          tutorName: c.tutorDisplayName ?? undefined,
          studentName: c.studentDisplayName ?? undefined,
          subjectName: c.subjectName ?? undefined,
          educationLevelName: c.educationLevelName ?? undefined,
          status: c.status,
        }));
        setClasses(mapped);
      })
      .catch(() => setClasses([]));
  }, []);

  return (
    <ScheduleCalendarView role="TUTOR" availabilitySlots={availabilitySlots} classes={classes} />
  );
}
