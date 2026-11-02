package vn.edufit.connection.web.response;

import java.util.UUID;
import vn.edufit.connection.application.dto.ClassResult;

public record ClassResponse(UUID classId, UUID studentId, UUID tutorId, UUID needId) {
  public static ClassResponse of(ClassResult r) {
    return new ClassResponse(r.classId(), r.studentId(), r.tutorId(), r.needId());
  }
}
