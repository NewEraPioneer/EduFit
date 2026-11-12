package vn.edufit.connection.application.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;
import vn.edufit.connection.infra.persistence.repository.ConnectionRequestRepository;
import vn.edufit.connection.application.policy.SlotConflictDetector;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.shared.exception.ErrorCode;
import vn.edufit.shared.exception.ResourceConflictException;

@Service
@Transactional(readOnly = true)
public class ConnectionFacadeImpl implements ConnectionFacade {
  private final ParentStudentLinkRepository links;
  private final TutoringClassRepository classes;
  private final ConnectionRequestRepository requests;
  private final SlotConflictDetector conflicts;

  @Override
  @Transactional
  public void updateAgreedSlots(UUID classId, List<String> slots) {
    var cls = classes.findByIdAndStatus(classId, "ACTIVE")
        .orElseThrow(() -> vn.edufit.shared.exception.EntityNotFoundException.of("Class", classId));
    requireNoRecurringConflict(classId, slots,
        classes.findByStudentIdAndStatus(cls.getStudentId(), "ACTIVE"), "học sinh");
    requireNoRecurringConflict(classId, slots,
        classes.findByTutorIdAndStatus(cls.getTutorId(), "ACTIVE"), "gia sư");
    cls.updateAgreedSlots(slots);
    classes.saveAndFlush(cls);
  }

  private void requireNoRecurringConflict(UUID classId, List<String> slots, List<TutoringClass> activeClasses, String owner) {
    var others = activeClasses.stream().filter(c -> !classId.equals(c.getId())).toList();
    var ids = others.stream().map(TutoringClass::getConnectionRequestId).filter(java.util.Objects::nonNull).toList();
    var overlap = conflicts.findConflictingSlots(slots,
        ClassAgreedSchedules.slots(others, requests.findAllById(ids)));
    if (!overlap.isEmpty()) throw new ResourceConflictException(ErrorCode.SLOT_CONFLICT_WITH_ACTIVE_SCHEDULE,
        "Lịch định kỳ mới trùng với lớp đang hoạt động của " + owner + ": " + String.join(", ", overlap));
  }

  public ConnectionFacadeImpl(ParentStudentLinkRepository links, TutoringClassRepository classes,
      ConnectionRequestRepository requests, SlotConflictDetector conflicts) {
    this.links = links;
    this.classes = classes;
    this.requests = requests;
    this.conflicts = conflicts;
  }

  @Override
  public boolean hasConfirmedParentLink(UUID parentUserId, UUID studentId) {
    return links.existsByParentUserIdAndStudentIdAndStatus(parentUserId, studentId, "CONFIRMED");
  }

  @Override
  public Optional<ActiveClassView> findActiveClass(UUID studentId, UUID tutorId) {
    return classes.findByStudentIdAndTutorIdAndStatus(studentId, tutorId, "ACTIVE")
        .map(c -> new ActiveClassView(c.getId(), c.getStudentId(), c.getTutorId(), c.getNeedId()));
  }

  @Override
  public List<ActiveClassView> findActiveClasses(UUID studentId, UUID tutorId) {
    if (studentId == null || tutorId == null) {
      return List.of();
    }
    return classes.findAllByStudentIdAndTutorIdAndStatus(studentId, tutorId, "ACTIVE").stream()
        .map(c -> new ActiveClassView(c.getId(), c.getStudentId(), c.getTutorId(), c.getNeedId()))
        .toList();
  }

  @Override
  public boolean isClassActive(UUID classId) {
    if (classId == null) {
      return false;
    }
    return classes.existsByIdAndStatus(classId, "ACTIVE");
  }

  @Override
  public boolean isClassActive(UUID classId, UUID tutorId, UUID studentId) {
    if (classId == null || tutorId == null || studentId == null) {
      return false;
    }
    return classes.existsByIdAndTutorIdAndStudentIdAndStatus(classId, tutorId, studentId, "ACTIVE");
  }

  @Override
  public Optional<ActiveClassView> findActiveClassById(UUID classId) {
    if (classId == null) {
      return Optional.empty();
    }
    return classes.findByIdAndStatus(classId, "ACTIVE")
        .map(c -> new ActiveClassView(c.getId(), c.getStudentId(), c.getTutorId(), c.getNeedId()));
  }

  @Override
  public Optional<ClassDetailView> findClassById(UUID classId) {
    if (classId == null) {
      return Optional.empty();
    }
    return classes.findById(classId).map(this::toClassDetailView);
  }

  @Override
  public List<ClassDetailView> findClassesByStudent(UUID studentId) {
    if (studentId == null) {
      return java.util.List.of();
    }
    return classes.findByStudentId(studentId).stream().map(this::toClassDetailView).toList();
  }

  @Override
  public List<ClassDetailView> findClassesByTutor(UUID tutorId) {
    if (tutorId == null) {
      return java.util.List.of();
    }
    return classes.findByTutorId(tutorId).stream().map(this::toClassDetailView).toList();
  }

  @Override
  public List<ClassDetailView> findClassesByIds(Collection<UUID> classIds) {
    if (classIds == null || classIds.isEmpty()) {
      return java.util.List.of();
    }
    return classes.findAllById(classIds).stream().map(this::toClassDetailView).toList();
  }

  @Override
  public java.util.List<UUID> findConfirmedParentUserIds(UUID studentId) {
    if (studentId == null) {
      return java.util.List.of();
    }
    return links.findByStudentIdAndStatus(studentId, "CONFIRMED").stream()
        .map(vn.edufit.connection.infra.persistence.entity.ParentStudentLink::getParentUserId)
        .toList();
  }

  @Override
  public java.util.List<UUID> findConfirmedStudentIds(UUID parentUserId) {
    if (parentUserId == null) {
      return java.util.List.of();
    }
    return links.findByParentUserIdAndStatus(parentUserId, "CONFIRMED").stream()
        .map(vn.edufit.connection.infra.persistence.entity.ParentStudentLink::getStudentId)
        .toList();
  }

  @Override
  public long countActiveClasses() {
    return classes.countByStatus("ACTIVE");
  }

  private ClassDetailView toClassDetailView(vn.edufit.connection.infra.persistence.entity.TutoringClass c) {
    return new ClassDetailView(
        c.getId(),
        c.getStudentId(),
        c.getTutorId(),
        c.getNeedId(),
        c.getStatus(),
        c.getStartedAt(),
        c.getEndedAt(),
        c.getPricePerSession()
    );
  }
}
