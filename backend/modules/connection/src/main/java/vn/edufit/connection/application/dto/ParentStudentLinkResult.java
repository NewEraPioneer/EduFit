package vn.edufit.connection.application.dto;

import java.time.Instant;
import java.util.UUID;
import vn.edufit.connection.infra.persistence.entity.ParentStudentLink;

public record ParentStudentLinkResult(UUID linkId, UUID parentUserId, UUID studentId, String status, Instant confirmedAt) {
  public static ParentStudentLinkResult from(ParentStudentLink r) {
    return new ParentStudentLinkResult(r.getId(), r.getParentUserId(), r.getStudentId(), r.getStatus(), r.getConfirmedAt());
  }
}
