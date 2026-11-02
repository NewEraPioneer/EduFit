package vn.edufit.payment.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.profile.api.ProfileFacade;
import vn.edufit.shared.auth.CurrentUser;
import vn.edufit.shared.exception.ForbiddenOperationException;

@Component
public class PaymentAccess {
  private final ProfileFacade profiles;
  private final ConnectionFacade connections;

  public PaymentAccess(ProfileFacade profiles, ConnectionFacade connections) {
    this.profiles = profiles;
    this.connections = connections;
  }

  public void authenticated(CurrentUser a) {
    if (a == null || a.getUserId() == null)
      throw new ForbiddenOperationException("Phiên đăng nhập đã hết hạn");
  }

  public void learnerRole(CurrentUser a) {
    authenticated(a);
    if (!a.hasRole("STUDENT") && !a.hasRole("PARENT"))
      throw new ForbiddenOperationException("Chỉ học sinh/phụ huynh được cấp tiền học phí");
  }

  public void tutorRole(CurrentUser a) {
    authenticated(a);
    if (!a.hasRole("TUTOR")) throw new ForbiddenOperationException("Chỉ gia sư được rút thu nhập");
  }

  public void admin(CurrentUser a) {
    authenticated(a);
    if (!a.hasRole("ADMIN")) throw new ForbiddenOperationException("Chỉ Admin được đối soát");
  }

  public boolean student(CurrentUser a, UUID student) {
    return a.hasRole("STUDENT")
        && profiles
            .findStudentByUserId(a.getUserId())
            .map(p -> p.studentId().equals(student))
            .orElse(false);
  }

  public boolean linkedParent(CurrentUser a, UUID student) {
    return a.hasRole("PARENT") && connections.hasConfirmedParentLink(a.getUserId(), student);
  }

  public boolean learner(CurrentUser a, PaymentViews.Charge c) {
    return student(a, c.studentId())
        || (a.getUserId().equals(c.payerId()) && linkedParent(a, c.studentId()));
  }

  public boolean tutor(CurrentUser a, PaymentViews.Charge c) {
    return a.hasRole("TUTOR") && a.getUserId().equals(c.tutorUserId());
  }

  public boolean payer(CurrentUser actor, PaymentViews.Charge charge) {
    return actor.getUserId().equals(charge.payerId()) && learner(actor, charge);
  }

  public void participant(CurrentUser a, PaymentViews.Charge c) {
    authenticated(a);
    if (!learner(a, c) && !tutor(a, c))
      throw new ForbiddenOperationException("Không có quyền thao tác khoản học phí");
  }
}
