"use client";

import React, { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { ScheduleCalendarView, ConnectedClass } from "@/features/scheduling";
import { connectionApi } from "@/features/connection/api/connectionApi";
import { progressApi } from "@/features/progress";

export interface LinkedStudent {
  studentId: string;
  studentName: string;
}

export default function ParentSchedulePage() {
  const [classes, setClasses] = useState<ConnectedClass[]>([]);
  const [students, setStudents] = useState<LinkedStudent[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let ignore = false;

    async function loadParentData() {
      try {
        setLoading(true);

        const [classesRes, linksRes, dashRes] = await Promise.allSettled([
          connectionApi.getStudentClasses(),
          connectionApi.getLinks(),
          progressApi.getParentDashboard(),
        ]);

        if (ignore) return;

        // 1. Map classes
        const classList = classesRes.status === "fulfilled" ? classesRes.value || [] : [];
        const mappedClasses: ConnectedClass[] = classList.map((c) => ({
          classId: c.classId,
          title: c.subjectName
            ? `${c.subjectName} - ${c.educationLevelName || "Cấp học"} • ${c.tutorDisplayName || "Gia sư"} (${c.studentDisplayName || "Con"})`
            : `${c.studentDisplayName || "Học sinh"} - ${c.tutorDisplayName || "Gia sư"}`,
          tutorId: c.tutorId,
          studentId: c.studentId,
          studentName: c.studentDisplayName ?? undefined,
          tutorName: c.tutorDisplayName ?? undefined,
          subjectName: c.subjectName ?? undefined,
          educationLevelName: c.educationLevelName ?? undefined,
          status: c.status,
        }));
        setClasses(mappedClasses);

        // 2. Discover linked students across classes, links, and dashboard
        const studentMap = new Map<string, string>();

        if (dashRes.status === "fulfilled" && dashRes.value?.success && dashRes.value.data?.students) {
          dashRes.value.data.students.forEach((s) => {
            if (s.studentId) {
              studentMap.set(s.studentId, s.studentName || `Học sinh ${s.studentId.slice(0, 8)}`);
            }
          });
        }

        mappedClasses.forEach((c) => {
          if (c.studentId && !studentMap.has(c.studentId)) {
            studentMap.set(c.studentId, c.studentName || `Học sinh ${c.studentId.slice(0, 8)}`);
          }
        });

        if (linksRes.status === "fulfilled" && Array.isArray(linksRes.value)) {
          linksRes.value.forEach((l) => {
            if (l.studentId && !studentMap.has(l.studentId)) {
              studentMap.set(l.studentId, `Học sinh ${l.studentId.slice(0, 8)}`);
            }
          });
        }

        const studentList: LinkedStudent[] = Array.from(studentMap.entries()).map(
          ([studentId, studentName]) => ({
            studentId,
            studentName,
          })
        );

        setStudents(studentList);

        if (studentList.length > 0) {
          setSelectedStudentId((prev) => prev || studentList[0].studentId);
        }
      } catch {
        if (!ignore) {
          setClasses([]);
          setStudents([]);
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    void loadParentData();

    return () => {
      ignore = true;
    };
  }, []);

  const activeStudentId = selectedStudentId || (students.length > 0 ? students[0].studentId : undefined);

  return (
    <div className="space-y-4">
      {/* Student Selector Dropdown for Parent */}
      {students.length > 0 && (
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white px-5 py-3.5 rounded-2xl border border-stone-200 shadow-2xs">
          <div className="flex items-center gap-2">
            <Users className="size-4 text-sky-600" />
            <label htmlFor="parent-student-select" className="text-xs font-bold text-neutral-800">
              Xem lịch học của con:
            </label>
          </div>
          <div className="flex items-center gap-2">
            <select
              id="parent-student-select"
              aria-label="Chọn học sinh"
              value={activeStudentId || ""}
              onChange={(e) => setSelectedStudentId(e.target.value)}
              className="rounded-xl border border-stone-200 bg-stone-50 px-3 py-1.5 text-xs font-bold text-neutral-800 focus:bg-white focus:outline-sky-500 cursor-pointer min-w-56"
            >
              {students.map((st) => (
                <option key={st.studentId} value={st.studentId}>
                  {st.studentName}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {/* When no linked students and loading complete */}
      {!loading && students.length === 0 && (
        <div className="rounded-2xl border border-stone-200 bg-white p-8 text-center text-xs font-semibold text-neutral-500 shadow-2xs">
          Chưa tìm thấy học sinh liên kết với tài khoản phụ huynh này.
        </div>
      )}

      {/* Calendar View with initialStudentId passed */}
      <ScheduleCalendarView
        key={activeStudentId || "empty"}
        role="PARENT"
        availabilitySlots={[]}
        classes={classes}
        initialStudentId={activeStudentId}
      />
    </div>
  );
}
