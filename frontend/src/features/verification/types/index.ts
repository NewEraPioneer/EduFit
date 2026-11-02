import { z } from "zod";
import {
  credentialUploadSchema,
  kycReviewSchema,
  credentialTypeSchema,
  revokeKycSchema,
  type RevokeKycInput,
} from "../schemas/kycSchemas";

export type CredentialType = z.infer<typeof credentialTypeSchema>;
export type CredentialUploadInput = z.infer<typeof credentialUploadSchema>;
export type KycReviewInput = z.infer<typeof kycReviewSchema>;
export type { RevokeKycInput };
export { revokeKycSchema };

export type {
  VerificationStatus,
  CredentialResponse,
  CredentialFileResponse,
  VerificationRequestDetail,
  SubmitVerificationPayload,
  PendingQueueItem,
  PendingQueueResponse,
} from "@/services/verification";
