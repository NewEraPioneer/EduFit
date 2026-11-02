export type ReviewerType = "STUDENT" | "PARENT";

export interface ReviewItem {
  reviewId: string;
  tutorId: string;
  studentId: string;
  reviewerUserId?: string;
  reviewerName: string;
  reviewerType: ReviewerType;
  rating: number;
  comment: string;
  isEdited: boolean;
  completedSessionsAtReview: number;
  isAuthor?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TutorRatingSummary {
  tutorId: string;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<number, number>;
}

export interface CreateReviewPayload {
  tutorId: string;
  studentId: string;
  rating: number;
  comment: string;
}

export interface UpdateReviewPayload {
  rating: number;
  comment: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

