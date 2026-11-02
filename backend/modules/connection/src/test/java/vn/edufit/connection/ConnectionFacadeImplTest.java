package vn.edufit.connection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edufit.connection.api.ConnectionFacade;
import vn.edufit.connection.application.service.ConnectionFacadeImpl;
import vn.edufit.connection.infra.persistence.entity.ConnectionRequest;
import vn.edufit.connection.infra.persistence.entity.TutoringClass;
import vn.edufit.connection.infra.persistence.repository.ParentStudentLinkRepository;
import vn.edufit.connection.infra.persistence.repository.TutoringClassRepository;

class ConnectionFacadeImplTest {
  private final ParentStudentLinkRepository links = mock(ParentStudentLinkRepository.class);
  private final TutoringClassRepository classes = mock(TutoringClassRepository.class);
  private final ConnectionFacade facade = new ConnectionFacadeImpl(links, classes);

  @ParameterizedTest
  @ValueSource(strings = {"CONFIRMED", "PENDING", "DECLINED", "REVOKED", "NONE"})
  void matchingAuthorizationQueriesOnlyConfirmedLinks(String status) {
    UUID parent = UUID.randomUUID(), student = UUID.randomUUID();
    when(links.existsByParentUserIdAndStudentIdAndStatus(parent, student, "CONFIRMED"))
        .thenReturn("CONFIRMED".equals(status));
    assertEquals("CONFIRMED".equals(status), facade.hasConfirmedParentLink(parent, student));
    verify(links).existsByParentUserIdAndStudentIdAndStatus(parent, student, "CONFIRMED");
    verifyNoMoreInteractions(links);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVE", "ENDED", "NONE"})
  void activeClassLookupReturnsOnlyActiveDetachedView(String status) {
    UUID student = UUID.randomUUID(), tutor = UUID.randomUUID(), goal = UUID.randomUUID();
    var request = new ConnectionRequest(student, tutor, goal, UUID.randomUUID(), List.of("Tue 19:00"), null, Instant.now());
    var tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    UUID id = UUID.randomUUID();
    ReflectionTestUtils.setField(tutoringClass, "id", id);
    when(classes.findByStudentIdAndTutorIdAndStatus(student, tutor, "ACTIVE"))
        .thenReturn("ACTIVE".equals(status) ? Optional.of(tutoringClass) : Optional.empty());
    assertEquals("ACTIVE".equals(status) ? Optional.of(new ConnectionFacade.ActiveClassView(id, student, tutor, goal))
        : Optional.empty(), facade.findActiveClass(student, tutor));
    verify(classes).findByStudentIdAndTutorIdAndStatus(student, tutor, "ACTIVE");
    verifyNoMoreInteractions(classes);
  }

  @Test
  void findActiveClassesReturnsListOfActiveViews() {
    UUID student = UUID.randomUUID(), tutor = UUID.randomUUID(), goal1 = UUID.randomUUID(), goal2 = UUID.randomUUID();
    var req1 = new ConnectionRequest(student, tutor, goal1, UUID.randomUUID(), List.of("Tue 19:00"), null, Instant.now());
    var class1 = new TutoringClass(req1, 200000L, Instant.now());
    UUID id1 = UUID.randomUUID();
    ReflectionTestUtils.setField(class1, "id", id1);

    var req2 = new ConnectionRequest(student, tutor, goal2, UUID.randomUUID(), List.of("Thu 19:00"), null, Instant.now());
    var class2 = new TutoringClass(req2, 200000L, Instant.now());
    UUID id2 = UUID.randomUUID();
    ReflectionTestUtils.setField(class2, "id", id2);

    when(classes.findAllByStudentIdAndTutorIdAndStatus(student, tutor, "ACTIVE"))
        .thenReturn(List.of(class1, class2));

    var result = facade.findActiveClasses(student, tutor);
    assertEquals(2, result.size());
    assertEquals(id1, result.get(0).classId());
    assertEquals(id2, result.get(1).classId());
    assertTrue(facade.findActiveClasses(null, tutor).isEmpty());
    assertTrue(facade.findActiveClasses(student, null).isEmpty());
  }

  @Test
  void contextResolvesExactlyOnePublicFacadeBean() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(ParentStudentLinkRepository.class, () -> links);
      context.registerBean(TutoringClassRepository.class, () -> classes);
      context.register(ConnectionFacadeImpl.class);
      context.refresh();
      assertEquals(1, context.getBeansOfType(ConnectionFacade.class).size());
      assertInstanceOf(ConnectionFacadeImpl.class, context.getBean(ConnectionFacade.class));
    }
  }

  @Test
  void isClassActiveSingleArgReturnsCorrectBoolean() {
    UUID activeId = UUID.randomUUID();
    UUID inactiveId = UUID.randomUUID();
    when(classes.existsByIdAndStatus(activeId, "ACTIVE")).thenReturn(true);
    when(classes.existsByIdAndStatus(inactiveId, "ACTIVE")).thenReturn(false);

    assertTrue(facade.isClassActive(activeId));
    assertFalse(facade.isClassActive(inactiveId));
    assertFalse(facade.isClassActive(null));
  }

  @Test
  void isClassActiveThreeArgsChecksClassTutorAndStudent() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    when(classes.existsByIdAndTutorIdAndStudentIdAndStatus(classId, tutorId, studentId, "ACTIVE")).thenReturn(true);

    assertTrue(facade.isClassActive(classId, tutorId, studentId));
    assertFalse(facade.isClassActive(classId, tutorId, UUID.randomUUID()));
    assertFalse(facade.isClassActive(null, tutorId, studentId));
    assertFalse(facade.isClassActive(classId, null, studentId));
    assertFalse(facade.isClassActive(classId, tutorId, null));
  }

  @Test
  void findActiveClassByIdReturnsActiveViewWhenPresent() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();

    var request = new ConnectionRequest(studentId, tutorId, needId, UUID.randomUUID(), List.of("Tue 19:00"), null, Instant.now());
    var tutoringClass = new TutoringClass(request, 200000L, Instant.now());
    ReflectionTestUtils.setField(tutoringClass, "id", classId);

    when(classes.findByIdAndStatus(classId, "ACTIVE")).thenReturn(Optional.of(tutoringClass));
    when(classes.findByIdAndStatus(UUID.randomUUID(), "ACTIVE")).thenReturn(Optional.empty());

    var result = facade.findActiveClassById(classId);
    assertTrue(result.isPresent());
    assertEquals(classId, result.get().classId());
    assertEquals(studentId, result.get().studentId());
    assertEquals(tutorId, result.get().tutorId());
    assertEquals(needId, result.get().needId());

    assertTrue(facade.findActiveClassById(null).isEmpty());
  }

  @Test
  void findClassByIdAndFindClassesByStudentOrTutorReturnExpectedDetails() {
    UUID classId = UUID.randomUUID();
    UUID tutorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID needId = UUID.randomUUID();
    Instant now = Instant.now();

    var request = new ConnectionRequest(studentId, tutorId, needId, UUID.randomUUID(), List.of("Mon 08:00"), null, now);
    var tutoringClass = new TutoringClass(request, 200000L, now);
    ReflectionTestUtils.setField(tutoringClass, "id", classId);

    when(classes.findById(classId)).thenReturn(Optional.of(tutoringClass));
    when(classes.findByStudentId(studentId)).thenReturn(List.of(tutoringClass));
    when(classes.findByTutorId(tutorId)).thenReturn(List.of(tutoringClass));

    var detailOpt = facade.findClassById(classId);
    assertTrue(detailOpt.isPresent());
    assertEquals(classId, detailOpt.get().classId());
    assertEquals("ACTIVE", detailOpt.get().status());

    assertEquals(1, facade.findClassesByStudent(studentId).size());
    assertEquals(1, facade.findClassesByTutor(tutorId).size());

    assertTrue(facade.findClassById(null).isEmpty());
    assertTrue(facade.findClassesByStudent(null).isEmpty());
    assertTrue(facade.findClassesByTutor(null).isEmpty());
  }

  @Test
  void findConfirmedParentAndStudentIdsReturnMappedUuids() {
    UUID parentUserId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    var link = new vn.edufit.connection.infra.persistence.entity.ParentStudentLink(parentUserId, studentId, Instant.now());
    when(links.findByStudentIdAndStatus(studentId, "CONFIRMED")).thenReturn(List.of(link));
    when(links.findByParentUserIdAndStatus(parentUserId, "CONFIRMED")).thenReturn(List.of(link));

    var parents = facade.findConfirmedParentUserIds(studentId);
    assertEquals(1, parents.size());
    assertEquals(parentUserId, parents.getFirst());

    var students = facade.findConfirmedStudentIds(parentUserId);
    assertEquals(1, students.size());
    assertEquals(studentId, students.getFirst());

    assertTrue(facade.findConfirmedParentUserIds(null).isEmpty());
    assertTrue(facade.findConfirmedStudentIds(null).isEmpty());
  }

  @Test
  void countActiveClassesDelegatesToRepository() {
    when(classes.countByStatus("ACTIVE")).thenReturn(15L);
    assertEquals(15L, facade.countActiveClasses());
    verify(classes).countByStatus("ACTIVE");
  }
}
