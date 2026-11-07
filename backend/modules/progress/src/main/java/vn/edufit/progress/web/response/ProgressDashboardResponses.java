package vn.edufit.progress.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.edufit.progress.application.dto.ProgressViews.ClassSelectorItemView;
import vn.edufit.progress.application.dto.ProgressViews.ParentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.ParentStudentSummaryView;
import vn.edufit.progress.application.dto.ProgressViews.ProgressOverviewView;
import vn.edufit.progress.application.dto.ProgressViews.StudentDashboardView;
import vn.edufit.progress.application.dto.ProgressViews.TeachingHistoryItemView;
import vn.edufit.scheduling.api.dto.ClassSessionStatsView;

public class ProgressDashboardResponses {

  public record ClassSelectorItem(
      UUID classId,
      String title,
      String status,
      Integer progressPercentage
  ) {
    public static ClassSelectorItem from(ClassSelectorItemView v) {
      if (v == null) return null;
      return new ClassSelectorItem(v.classId(), v.title(), v.status(), v.progressPercentage());
    }
  }

  public record StudentDashboardResponse(
      UUID studentId,
      String studentName,
      List<ClassSelectorItem> classes,
      UUID selectedClassId,
      UUID tutorId,
      String tutorName,
      UUID needId,
      String goalTitle,
      Integer progressPercentage,
      PlanResponse plan,
      ProgressOverviewView overview,
      List<SessionNoteTimelineResponse> recentNotes,
      boolean hasMoreNotes
  ) {
    public static StudentDashboardResponse from(StudentDashboardView v) {
      if (v == null) return null;
      return new StudentDashboardResponse(
          v.studentId(),
          v.studentName(),
          v.classes() != null ? v.classes().stream().map(ClassSelectorItem::from).toList() : List.of(),
          v.selectedClassId(),
          v.tutorId(),
          v.tutorName(),
          v.needId(),
          v.goalTitle(),
          v.progressPercentage(),
          PlanResponse.from(v.plan()),
          v.overview(),
          v.recentNotes() != null ? v.recentNotes().stream().map(SessionNoteTimelineResponse::from).toList() : List.of(),
          v.hasMoreNotes()
      );
    }
  }

  public record ParentStudentSummary(
      UUID studentId,
      String studentName,
      List<ClassSelectorItem> classes,
      StudentDashboardResponse activeClassDashboard
  ) {
    public static ParentStudentSummary from(ParentStudentSummaryView v) {
      if (v == null) return null;
      return new ParentStudentSummary(
          v.studentId(),
          v.studentName(),
          v.classes() != null ? v.classes().stream().map(ClassSelectorItem::from).toList() : List.of(),
          StudentDashboardResponse.from(v.activeClassDashboard())
      );
    }
  }

  public record ParentDashboardResponse(
      List<ParentStudentSummary> students
  ) {
    public static ParentDashboardResponse from(ParentDashboardView v) {
      if (v == null) return null;
      return new ParentDashboardResponse(
          v.students() != null ? v.students().stream().map(ParentStudentSummary::from).toList() : List.of()
      );
    }
  }

  public record TeachingHistoryItem(
      UUID classId,
      UUID studentId,
      String studentName,
      UUID needId,
      String goalTitle,
      String status,
      Integer progressPercentage,
      Instant startedAt,
      Instant endedAt,
      ClassSessionStatsView sessionStats
  ) {
    public static TeachingHistoryItem from(TeachingHistoryItemView v) {
      if (v == null) return null;
      return new TeachingHistoryItem(
          v.classId(),
          v.studentId(),
          v.studentName(),
          v.needId(),
          v.goalTitle(),
          v.status(),
          v.progressPercentage(),
          v.startedAt(),
          v.endedAt(),
          v.sessionStats()
      );
    }
  }
}
