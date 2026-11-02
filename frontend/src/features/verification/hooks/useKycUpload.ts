"use client";

import { useState, useCallback } from "react";
import { verificationService } from "@/services/verification";
import { ApiError } from "@/lib/api";
import {
  credentialUploadSchema,
  validateCredentialFile,
} from "../schemas/kycSchemas";
import type { CredentialType } from "../types";

export function useKycUpload() {
  const [institutionName, setInstitutionName] = useState("");
  const [graduationYear, setGraduationYear] = useState<number | undefined>(
    new Date().getFullYear()
  );
  const [documentType, setDocumentType] = useState<CredentialType>("DEGREE");
  const [notes, setNotes] = useState("");
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDocumentTypeChange = useCallback((newType: CredentialType) => {
    setDocumentType(newType);
    if (newType === "OTHER") {
      setGraduationYear(undefined);
    }
  }, []);

  const resetForm = useCallback(() => {
    setInstitutionName("");
    setGraduationYear(new Date().getFullYear());
    setDocumentType("DEGREE");
    setNotes("");
    setSelectedFiles([]);
    setError(null);
  }, []);

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files || []);
      for (const file of files) {
        const validation = validateCredentialFile(file);
        if (!validation.valid) {
          setError(validation.error || "Tệp không hợp lệ");
          e.target.value = "";
          return;
        }
      }

      setError(null);
      setSelectedFiles((currentFiles) => {
        const knownFiles = new Set(
          currentFiles.map((file) => `${file.name}:${file.size}:${file.lastModified}`)
        );
        return [
          ...currentFiles,
          ...files.filter(
            (file) => !knownFiles.has(`${file.name}:${file.size}:${file.lastModified}`)
          ),
        ];
      });
      e.target.value = "";
    },
    []
  );

  const removeFile = useCallback((indexToRemove: number) => {
    setSelectedFiles((files) => files.filter((_, idx) => idx !== indexToRemove));
  }, []);

  const submit = useCallback(
    async (onSuccess?: () => void, onClose?: () => void) => {
      // 1. Validate Form payload với Zod
      const parseResult = credentialUploadSchema.safeParse({
        documentType,
        institutionName,
        graduationYear,
        notes: notes.trim() || undefined,
      });

      if (!parseResult.success) {
        setError(parseResult.error.issues[0]?.message || "Thông tin không hợp lệ.");
        return;
      }

      // 2. Validate có ít nhất 1 file
      if (selectedFiles.length === 0) {
        setError("Vui lòng tải lên ít nhất 1 ảnh chụp bằng cấp hoặc tài liệu chứng minh.");
        return;
      }

      try {
        setIsSubmitting(true);
        setError(null);

        await verificationService.submitVerification(
          {
            credentials: [
              {
                type: parseResult.data.documentType,
                institution: parseResult.data.institutionName,
                year: parseResult.data.graduationYear ?? undefined,
                note: parseResult.data.notes,
                fileIndexes: selectedFiles.map((_, idx) => idx),
              },
            ],
          },
          selectedFiles
        );

        onSuccess?.();
        resetForm();
        onClose?.();
      } catch (err: unknown) {
        if (err instanceof ApiError && err.status === 401) {
          setError("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại trước khi nộp hồ sơ.");
        } else {
          setError(
            err instanceof Error
              ? err.message
              : "Không thể gửi hồ sơ xác minh. Vui lòng thử lại."
          );
        }
      } finally {
        setIsSubmitting(false);
      }
    },
    [documentType, institutionName, graduationYear, notes, selectedFiles, resetForm]
  );

  return {
    institutionName,
    setInstitutionName,
    graduationYear,
    setGraduationYear,
    documentType,
    setDocumentType,
    handleDocumentTypeChange,
    notes,
    setNotes,
    selectedFiles,
    isSubmitting,
    error,
    setError,
    handleFileChange,
    removeFile,
    resetForm,
    submit,
  };
}
