import { apiClient } from "@/lib/api";

export interface EducationLevel {
  levelId: number;
  name: string;
  sortOrder: number;
}

export interface SubjectItem {
  subjectId: number;
  name: string;
}

export interface CatalogResponse {
  educationLevels: EducationLevel[];
  subjects: SubjectItem[];
}

export interface SubjectTrack {
  trackId: number;
  subjectId: number;
  code: string;
  name: string;
  levelIds: number[];
}

export interface GoalTag {
  goalTagId: number;
  code: string;
  name: string;
  description?: string;
  requiresTextbook: boolean;
  requiresExamProvince: boolean;
  premium: boolean;
}

export interface GoalTagRule {
  goalTagId: number;
  subjectId: number;
  trackId: number | null;
  levelId: number;
}

export interface TextbookSeries {
  seriesId: number;
  code: string;
  name: string;
  subjectIds: number[];
}

export interface Province {
  provinceId: number;
  name: string;
}

export interface District {
  districtId: number;
  provinceId: number;
  name: string;
}

export interface SpecializedSchool {
  schoolId: number;
  provinceId: number;
  name: string;
}

/** Danh mục nhu cầu học: bộ chọn Lớp → Môn → (Nhánh) → Mục tiêu dựng từ goalTagRules. */
export interface LearningCatalog {
  educationLevels: EducationLevel[];
  subjects: SubjectItem[];
  tracks: SubjectTrack[];
  goalTags: GoalTag[];
  goalTagRules: GoalTagRule[];
  textbooks: TextbookSeries[];
  provinces: Province[];
  districts: District[];
  specializedSchools: SpecializedSchool[];
}

let learningCatalogRequest: Promise<LearningCatalog> | null = null;

export const catalogService = {
  /**
   * Lấy danh mục dùng chung (Cấp học và Môn học đang hoạt động)
   */
  async getCatalog(): Promise<CatalogResponse> {
    const res = await apiClient.get<CatalogResponse>("/api/v1/catalogs");
    return res.data as CatalogResponse;
  },

  /**
   * Danh mục nhu cầu học (lớp, môn, nhánh, mục tiêu và luật, bộ sách, địa giới).
   * Dữ liệu ít thay đổi nên dùng chung một lần tải cho cả phiên; lỗi thì cho phép tải lại.
   */
  async getLearningCatalog(): Promise<LearningCatalog> {
    if (!learningCatalogRequest) {
      learningCatalogRequest = apiClient
        .get<LearningCatalog>("/api/v1/catalogs/learning")
        .then((res) => res.data as LearningCatalog)
        .catch((error) => {
          learningCatalogRequest = null;
          throw error;
        });
    }
    return learningCatalogRequest;
  },

  /** Chỉ dùng trong test: xóa bộ nhớ đệm danh mục. */
  resetLearningCatalogCache(): void {
    learningCatalogRequest = null;
  },
};
