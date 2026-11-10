"use client";

import React, { useCallback, useEffect, useState } from "react";
import { connectionService } from "@/services/connections";
import type { ConnectionRequest } from "../types";
import { discoveryApi } from "@/features/discovery";

const statusLabels: Record<string, string> = {
  PENDING: "Đang chờ gia sư phản hồi",
  ACCEPTED: "Gia sư đã đồng ý kết nối",
  REJECTED: "Gia sư đã từ chối",
  CANCELLED: "Đã hủy",
  EXPIRED: "Đã hết hạn",
};

export interface SentRequestsProps {
  studentId?: string;
}

export function SentRequests({ studentId }: SentRequestsProps) {
  const [requests, setRequests] = useState<ConnectionRequest[]>([]);
  const [tutorNames, setTutorNames] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await connectionService.requests(studentId);
      setRequests(result);
      const uniqueTutorIds = [...new Set(result.map((request) => request.tutorId))];
      const names = await Promise.all(
        uniqueTutorIds.map(async (tutorId) => {
          try {
            const detail = await discoveryApi.getTutorDetail(tutorId);
            return [tutorId, detail.data?.displayName || "Gia sư không còn công khai"] as const;
          } catch {
            return [tutorId, "Gia sư không còn công khai"] as const;
          }
        })
      );
      setTutorNames(Object.fromEntries(names));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể tải yêu cầu.");
    } finally {
      setLoading(false);
    }
  }, [studentId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-medium">Yêu cầu đã gửi</h3>
        <button
          className="rounded-full border px-4 py-2 text-sm"
          onClick={load}
          disabled={loading}
        >
          Làm mới
        </button>
      </div>
      {error && <p role="alert" className="text-danger-strong">{error}</p>}
      {loading && <p>Đang tải…</p>}
      {!loading && !error && !requests.length && <p>Chưa có yêu cầu kết nối.</p>}
      {requests.map((request) => (
        <div className="rounded-2xl border bg-surface p-4 text-sm" key={request.requestId}>
          <p className="font-medium">{tutorNames[request.tutorId] || "Đang tải tên gia sư…"}</p>
          <p>{statusLabels[request.status] || request.status}</p>
          <p>Đã gửi {new Date(request.createdAt).toLocaleString("vi-VN")}</p>
        </div>
      ))}
    </div>
  );
}
