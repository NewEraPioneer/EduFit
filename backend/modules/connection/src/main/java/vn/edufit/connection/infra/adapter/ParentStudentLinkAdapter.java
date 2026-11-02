package vn.edufit.connection.infra.adapter;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.profile.api.port.ParentStudentLinkPort;

/** Cung cấp cho module Profile thông tin liên kết phụ huynh – học sinh đã xác nhận. */
@Component
public class ParentStudentLinkAdapter implements ParentStudentLinkPort {

  private final ParentStudentLinkRepository links;

  public ParentStudentLinkAdapter(ParentStudentLinkRepository links) {
    this.links = links;
  }

  @Override
  public boolean hasConfirmedLink(UUID parentUserId, UUID studentId) {
    return parentUserId != null && studentId != null
        && links.existsByParentUserIdAndStudentIdAndStatus(parentUserId, studentId, "CONFIRMED");
  }
}
