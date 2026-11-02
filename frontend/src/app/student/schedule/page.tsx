"use client";

import React, { useEffect, useState } from "react";
import { ScheduleCalendarView, ConnectedClass } from "@/features/scheduling";
import { connectionApi } from "@/features/connection/api/connectionApi";

export default function StudentSchedulePage() {
  const [classes, setClasses] = useState<ConnectedClass[]>([]);

  useEffect(() => {
    connectionApi
      .getStudentClasses()
      .then((data) => {
        const classList = data || [];
        const mapped: ConnectedClass[] = classList.map((c) => ({
          classId: c.classId,
          title: c.subjectName
            ? `${c.subjectName} - ${c.educationLevelName || "Cấp học"} • ${c.tutorDisplayName || "Gia sư"}`
            : (c.tutorDisplayName || "Lớp học"),
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
      .catch(() => {
        setClasses([]);
      });
  }, []);

  return <ScheduleCalendarView role="STUDENT" availabilitySlots={[]} classes={classes} />;
}
