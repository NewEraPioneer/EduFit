"use client";

import React, { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { ScheduleCalendarView, ConnectedClass } from "@/features/scheduling";
import { connectionApi } from "@/features/connection/api/connectionApi";

export default function StudentSchedulePage() {
  return (
    <Suspense fallback={<div className="py-12 text-center text-xs text-neutral-500">Đang tải lịch học...</div>}>
      <StudentScheduleContent />
    </Suspense>
  );
}

function StudentScheduleContent() {
  const searchParams = useSearchParams();
  const sessionId = searchParams.get("sessionId");
  const startAt = searchParams.get("startAt");
  const initialSession = sessionId && startAt && Number.isFinite(Date.parse(startAt))
    ? { sessionId, startAt }
    : undefined;
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

  return (
    <ScheduleCalendarView
      key={initialSession && `${initialSession.sessionId}:${initialSession.startAt}`}
      role="STUDENT"
      classes={classes}
      initialSession={initialSession}
    />
  );
}
