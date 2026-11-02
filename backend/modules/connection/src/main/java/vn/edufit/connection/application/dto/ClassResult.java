package vn.edufit.connection.application.dto;

import java.util.UUID;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;

public record ClassResult(UUID classId, UUID studentId, UUID tutorId, UUID needId) {
  public static ClassResult from(TutoringClass r) {
    return new ClassResult(r.getId(), r.getStudentId(), r.getTutorId(), r.getNeedId());
  }
}
