# Kế Hoạch Triển Khai v2: Thông Báo Đổi Lịch, Floating Toast, Chấm Sidebar & Audit

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** End-to-end reschedule notification flow, floating notification toast, smart sidebar unread dots, and the backend API to support them.

**Architecture:** Spring `@TransactionalEventListener` domain events → JPA `Notification` entity → REST polling → React Context → Toast + Sidebar dots. No WebSocket (polling every 15s matches existing pattern).

**Tech Stack:** Java 21 / Spring Boot 3 / JPA, Next.js 15 / React 19 / TypeScript / Tailwind CSS

**Spec:** Báo cáo kiểm toán trong phần Audit Gap Analysis bên dưới + review report `plan_review_report.md`.

## Global Constraints

- **KHÔNG** sửa `backend/app/src/main/resources/application.yml` dưới bất kỳ hình thức nào.
- Event mới phải đặt trong `vn.edufit.scheduling.api.event` (tuân thủ ArchUnit rule: notification chỉ import `.api.` packages).
- Mọi listener mới phải dùng `@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)` + `@Transactional` — giống pattern hiện có.
- `Notification.type` tối đa 60 ký tự, `title` tối đa 200, `shortText` tối đa 500.
- Giữ nguyên toàn bộ 91 test files Vitest (671 tests) và Maven test suites hiện tại.

## Review Focus

1. **Duplicate notification khi parent = student (tự liên kết):** `findConfirmedParentUserIds` có thể trả về chính `student.userId` → gửi 2 notification cho 1 người. Cần deduplicate bằng `Set<UUID>`.
2. **`type` field overflow:** Các type mới như `SESSION_RESCHEDULE_PROPOSED` = 30 chars, `SESSION_RESCHEDULE_REJECTED` = 30 chars — nằm trong 60 chars limit. OK.
3. **Race condition giữa polling cycle và markAsRead:** Nếu user click notification đúng lúc poll xảy ra, unreadCount có thể flicker. Context cần `optimistic update` trước khi API trả về.
4. **sessionStorage leak cho seenNotificationIds:** Nếu user login nhiều tab, `sessionStorage` không share giữa các tab → Toast có thể hiện ở mỗi tab. Chấp nhận hành vi này (mỗi tab là independent session).
5. **Parent với nhiều student liên kết:** `findConfirmedParentUserIds(studentId)` chỉ trả về parents cho 1 student cụ thể. Đúng behavior — mỗi notification gắn với 1 `aboutStudentId`.

---

## Phần Audit Gap Analysis (Giữ Nguyên Từ v1)

| Module | Hành Động | Event | Listener? | Đánh Giá |
|--------|-----------|-------|-----------|----------|
| `scheduling` | Đề xuất đổi lịch | `SessionRescheduleProposedEvent` ✅ | ❌ Thiếu | **Scope Task 1** |
| `scheduling` | Từ chối đổi lịch | ❌ Chưa có event | ❌ Thiếu | **Scope Task 1** |
| `scheduling` | Chấp nhận đổi lịch | `SessionRescheduledEvent` ✅ | ⚠️ Thiếu parent | **Scope Task 1** |
| `scheduling` | Điểm danh | `SessionOutcomeRecordedEvent` ✅ | ❌ Thiếu | Out of scope |
| `verification` | Duyệt/Từ chối KYC | ❌ Chưa có event | ❌ Thiếu | Out of scope |
| `progress` | Ghi chú / Milestone | ❌ Chưa có event | ❌ Thiếu | Out of scope |
| `connection` | Lớp kết thúc | `TutoringClassEndedEvent` ✅ | ❌ Thiếu | Out of scope |
| `payment` | PaymentSession | `PaymentSessionChangedEvent` ✅ | ✅ Đã có | OK |
| `review` | Review updated | `ReviewUpdatedEvent` ✅ | ❌ Thiếu | Out of scope |

---

## Task 1: Backend — Domain Events & Notification Listeners cho Đổi Lịch

**Files:**
- Create: `backend/modules/scheduling/src/main/java/vn/edufit/scheduling/api/event/SessionRescheduleRejectedEvent.java`
- Modify: `backend/modules/scheduling/src/main/java/vn/edufit/scheduling/application/service/RescheduleSessionService.java:193-208`
- Modify: `backend/modules/notification/src/main/java/vn/edufit/notification/application/listener/NotificationEventListener.java`
- Test: `backend/modules/notification/src/test/java/vn/edufit/notification/application/listener/NotificationEventListenerTest.java`
- Test: `backend/modules/scheduling/src/test/java/vn/edufit/scheduling/application/RescheduleSessionServiceTest.java`

**Interfaces:**
- Consumes: `SessionRescheduleProposedEvent` (existing), `SessionRescheduledEvent` (existing), `ProfileFacade.findTutorById()`, `ProfileFacade.findStudentById()`, `ConnectionFacade.findConfirmedParentUserIds()`
- Produces: `SessionRescheduleRejectedEvent` (new event), 3 new `@TransactionalEventListener` methods in `NotificationEventListener`

### Steps

- [ ] **Step 1: Create `SessionRescheduleRejectedEvent.java`**

```java
// vn.edufit.scheduling.api.event
public record SessionRescheduleRejectedEvent(
    UUID eventId, Instant occurredAt,
    UUID proposalId, UUID sessionId, UUID classId,
    UUID tutorId, UUID studentId,
    UUID proposedBy, UUID rejectedBy, String reason
) implements BaseDomainEvent {
    public static SessionRescheduleRejectedEvent of(
        UUID proposalId, UUID sessionId, UUID classId,
        UUID tutorId, UUID studentId,
        UUID proposedBy, UUID rejectedBy, String reason) { ... }
    @Override public UUID getEventId() { return eventId; }
    @Override public Instant getOccurredAt() { return occurredAt; }
}
```

- [ ] **Step 2: Publish event in `RescheduleSessionService.respondToReschedule()` reject branch**

In `RescheduleSessionService.java`, after L206 (`log.info("Đã từ chối...")`), before `return updatedProposal`:

```java
eventPublisher.publishEvent(SessionRescheduleRejectedEvent.of(
    proposalId, session.getSessionId(), session.getClassId(),
    session.getTutorId(), session.getStudentId(),
    proposal.getProposedBy(), responderId, responseReason
));
```

- [ ] **Step 3: Write failing test — `RescheduleSessionServiceTest` verifies reject publishes event**

Assert that `ApplicationEventPublisher.publishEvent()` is called with a `SessionRescheduleRejectedEvent` when `accept = false`.

- [ ] **Step 4: Run Maven test — verify it passes**

Run: `mvn test -f backend/pom.xml -pl modules/scheduling -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Add `onSessionRescheduleProposed()` listener in `NotificationEventListener`**

Pattern: Reuse existing `resolveRecipientAndNotify()` (L259-271) which correctly resolves `proposedBy` (userId) against `tutor.userId()` via `ProfileFacade`.

Additionally notify confirmed parents via `connectionFacade.findConfirmedParentUserIds(event.studentId())`. Deduplicate recipients with `Set<UUID>`.

```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
@Transactional
public void onSessionRescheduleProposed(SessionRescheduleProposedEvent event) {
    if (event == null) return;
    // 1. Resolve counterparty (reuse resolveRecipientAndNotify pattern)
    // 2. Also notify confirmed parents for the student
    // Type: "SESSION_RESCHEDULE_PROPOSED", TargetType: "SESSION", TargetId: event.sessionId()
    // Title: "Đề xuất đổi lịch học"
    // ShortText: "Có đề xuất dời buổi học sang khung giờ mới, cần xác nhận"
}
```

- [ ] **Step 6: Add `onSessionRescheduleRejected()` listener**

```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
@Transactional
public void onSessionRescheduleRejected(SessionRescheduleRejectedEvent event) {
    if (event == null) return;
    // Notify proposedBy that their proposal was rejected
    // Type: "SESSION_RESCHEDULE_REJECTED", TargetType: "SESSION", TargetId: event.sessionId()
    // Title: "Đề xuất đổi lịch bị từ chối"
    // ShortText: "Đề xuất đổi lịch buổi học đã bị từ chối" + (reason != null ? ": " + reason : "")
}
```

- [ ] **Step 7: Update `onSessionRescheduled()` — add parent notification**

After existing `notifyBoth()` call (L195), add:

```java
if (connectionFacade != null) {
    connectionFacade.findConfirmedParentUserIds(event.studentId()).forEach(parentUserId -> {
        // skip if parentUserId already matches tutor or student userId (deduplicate)
        repository.save(new Notification(parentUserId, event.studentId(), "SESSION_UPDATE",
            "Lịch học đã thay đổi", "Thời gian buổi học đã được cập nhật lại", "SESSION", event.sessionId()));
    });
}
```

- [ ] **Step 8: Write failing tests for all 3 new listeners in `NotificationEventListenerTest.java`**

3 tests:
1. `onSessionRescheduleProposed_savesNotificationForCounterparty` — mock ProfileFacade, verify `repository.save()` called.
2. `onSessionRescheduleRejected_notifiesProposer` — verify notification saved for `proposedBy` userId.
3. `onSessionRescheduled_notifiesParents` — mock `connectionFacade.findConfirmedParentUserIds()` returning 1 parent, verify extra `repository.save()`.

- [ ] **Step 9: Run all tests and verify**

Run: `mvn test -f backend/pom.xml -pl modules/notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add backend/modules/scheduling/src/main/java/vn/edufit/scheduling/api/event/SessionRescheduleRejectedEvent.java
git add backend/modules/scheduling/src/main/java/vn/edufit/scheduling/application/service/RescheduleSessionService.java
git add backend/modules/notification/src/main/java/vn/edufit/notification/application/listener/NotificationEventListener.java
git add backend/modules/notification/src/test/ backend/modules/scheduling/src/test/
git commit -m "feat(notification): handle reschedule propose/reject/accept events with parent notification"
```

---

## Task 2: Backend — Unread Summary API Endpoint

**Files:**
- Modify: `backend/modules/notification/src/main/java/vn/edufit/notification/infra/persistence/repository/NotificationRepository.java`
- Create: `backend/modules/notification/src/main/java/vn/edufit/notification/web/response/UnreadSummaryResponse.java`
- Modify: `backend/modules/notification/src/main/java/vn/edufit/notification/application/service/NotificationService.java`
- Modify: `backend/modules/notification/src/main/java/vn/edufit/notification/web/NotificationController.java`
- Test: `backend/modules/notification/src/test/java/vn/edufit/notification/web/NotificationControllerTest.java`

**Interfaces:**
- Consumes: `NotificationRepository`, `CurrentUser`
- Produces: `GET /api/v1/notifications/unread-summary` → `UnreadSummaryResponse { total, byCategory: Map<String, Long> }`

### Steps

- [ ] **Step 1: Add repository query**

In `NotificationRepository.java`:
```java
@Query("SELECT CASE " +
       "WHEN n.type LIKE 'SESSION%' OR n.type LIKE 'RESCHEDULE%' THEN 'SCHEDULE' " +
       "WHEN n.type LIKE 'CONNECTION%' THEN 'CONNECTION' " +
       "WHEN n.type LIKE 'LINK%' THEN 'LINK' " +
       "WHEN n.type LIKE 'PAYMENT%' THEN 'PAYMENT' " +
       "WHEN n.type LIKE 'REVIEW%' THEN 'REVIEW' " +
       "WHEN n.type LIKE 'KYC%' OR n.type LIKE 'VERIFICATION%' THEN 'VERIFICATION' " +
       "ELSE 'OTHER' END as category, COUNT(n) as cnt " +
       "FROM Notification n WHERE n.userId = :userId AND n.isRead = false " +
       "GROUP BY category")
List<Object[]> countUnreadByCategory(@Param("userId") UUID userId);
```

- [ ] **Step 2: Create `UnreadSummaryResponse.java`**

```java
public record UnreadSummaryResponse(
    long total,
    Map<String, Long> byCategory
) {}
```

- [ ] **Step 3: Add service method `getUnreadSummary(CurrentUser)`**

Calls `countUnreadByCategory()`, transforms `List<Object[]>` to `Map<String, Long>`, sums total.

- [ ] **Step 4: Add controller endpoint**

```java
@GetMapping("/unread-summary")
public ResponseEntity<ApiResponse<UnreadSummaryResponse>> getUnreadSummary() { ... }
```

- [ ] **Step 5: Write test, run, verify**

Run: `mvn test -f backend/pom.xml -pl modules/notification -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(notification): add GET /unread-summary endpoint for sidebar category dots"
```

---

## Task 3: Frontend — NotificationContext & Floating Toast

**Files:**
- Create: `frontend/src/features/notification/context/NotificationContext.tsx`
- Create: `frontend/src/features/notification/components/NotificationFloatingToast.tsx`
- Modify: `frontend/src/shared/ui/TopHeader.tsx:52-63` — remove polling, consume context
- Modify: `frontend/src/features/notification/types/index.ts` — add `UnreadSummaryResponse`
- Modify: `frontend/src/features/notification/api/notificationApi.ts` — add `getUnreadSummary()`
- Modify: `frontend/src/features/notification/index.ts` — re-export
- Test: `frontend/src/__tests__/features/notification/NotificationToast.test.tsx`
- Modify: `frontend/src/__tests__/features/notification/TopHeaderPolling.test.tsx`

**Interfaces:**
- Consumes: `notificationApi.getNotifications()`, `notificationApi.getUnreadSummary()`, `getNotificationRoute()`, `useToast()`
- Produces: `useNotification()` hook → `{ unreadCount, unreadCategoryMap, notifications }`, `NotificationFloatingToast` component

### Steps

- [ ] **Step 1: Add `UnreadSummaryResponse` type and API method**

```ts
// types/index.ts
export interface UnreadSummaryResponse {
  total: number;
  byCategory: Record<string, number>;
}

// api/notificationApi.ts
async getUnreadSummary(): Promise<ApiResponse<UnreadSummaryResponse>> {
  return fetchApi<UnreadSummaryResponse>("/api/v1/notifications/unread-summary", { method: "GET" });
}
```

- [ ] **Step 2: Create `NotificationContext.tsx`**

Single polling loop (15s). Manages:
- `unreadCount`: total unread
- `unreadCategoryMap`: `{ SCHEDULE: boolean, CONNECTION: boolean, LINK: boolean, PAYMENT: boolean, VERIFICATION: boolean }`
- `recentNotifications`: last 5 items for toast detection
- `seenToastIds`: `Set<string>` (React ref, not sessionStorage — per-mount lifetime)
- Detects new unseen notifications → queues them into Toast system

- [ ] **Step 3: Create `NotificationFloatingToast.tsx`**

Position: `fixed top-20 right-5 z-50` (below TopHeader, separate from action Toast at `bottom-5 right-5`).
Auto-dismiss: 4.5s. Click → `markAsRead` + `router.push(getNotificationRoute(item, role))`.
Design: Card with notification icon, title, shortText, relative time, [X] close button.

- [ ] **Step 4: Remove polling from `TopHeader.tsx`**

Delete L52-63 (`React.useEffect` with `refreshCount` + `setInterval(refreshCount, 15_000)`).
Replace with: `const { unreadCount } = useNotification();` and remove `unreadNotifications` local state.

- [ ] **Step 5: Update `TopHeaderPolling.test.tsx`**

Adapt test to verify that `TopHeader` reads from context (mock `NotificationContext`) instead of calling `notificationApi.getUnreadCount()` directly.

- [ ] **Step 6: Write `NotificationToast.test.tsx`**

Tests:
1. Toast appears when new notification detected (ID not in seenToastIds).
2. Toast auto-dismisses after 4.5s (fake timers).
3. Click on Toast calls `markAsRead` and `router.push`.
4. No toast for already-seen notification IDs.

- [ ] **Step 7: Run Vitest, verify all 91 files pass**

Run: `npm --prefix frontend test -- --run`
Expected: 91 passed, 671+ tests

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(notification): centralized NotificationContext with floating toast and remove TopHeader duplicate polling"
```

---

## Task 4: Frontend — Sidebar Unread Dots

**Files:**
- Modify: `frontend/src/shared/ui/Sidebar.tsx`
- Modify: `frontend/src/shared/ui/DashboardShell.tsx`
- Modify: `frontend/src/__tests__/shared/SidebarNavigation.test.tsx`

**Interfaces:**
- Consumes: `useNotification()` → `unreadCategoryMap`
- Produces: Visual dots on specific nav items for each role

### Steps

- [ ] **Step 1: Add `notificationDot` to `NavItem` interface**

```ts
interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  badge?: string | null;
  notificationCategory?: string; // maps to unreadCategoryMap key
}
```

Add `notificationCategory` to relevant items:
- Student: `Lịch học tuần` → `"SCHEDULE"`, `Yêu cầu kết nối` → `"CONNECTION"`, `Liên kết phụ huynh` → `"LINK"`, `Ví & Học phí` → `"PAYMENT"`
- Tutor: `Lịch dạy` → `"SCHEDULE"`, `Yêu cầu học viên` → `"CONNECTION"`, `Xác minh bằng cấp` → `"VERIFICATION"`, `Thu nhập & Rút tiền` → `"PAYMENT"`
- Parent: `Lịch học gia đình` → `"SCHEDULE"`, `Liên kết con em` → `"LINK"`, `Ví & Học phí` → `"PAYMENT"`
- Admin: `Duyệt bằng cấp KYC` → `"VERIFICATION"`, `Đối soát thanh toán` → `"PAYMENT"`

- [ ] **Step 2: Render dot in `SidebarNav` component**

Read `unreadCategoryMap` from `useNotification()` context.

Expanded mode: dot right of label text.
Collapsed mode: dot at top-right corner of icon wrapper.

```tsx
{item.notificationCategory && unreadCategoryMap[item.notificationCategory] && (
  <span className="size-2 rounded-full bg-amber-500 animate-pulse ring-1 ring-white dark:ring-stone-900 shrink-0" />
)}
```

For collapsed:
```tsx
<div className="relative">
  <Icon className="size-4.5 ..." />
  {item.notificationCategory && unreadCategoryMap[item.notificationCategory] && (
    <span className="absolute -top-1 -right-1 size-2 rounded-full bg-amber-500 animate-pulse ring-1 ring-white dark:ring-stone-900" />
  )}
</div>
```

- [ ] **Step 3: Wire `NotificationContext.Provider` in `DashboardShell.tsx`**

Wrap `Sidebar` + `TopHeader` + `main` in `<NotificationProvider role={role}>`.

- [ ] **Step 4: Update `SidebarNavigation.test.tsx`**

Add tests:
1. Sidebar renders dot on `Lịch học tuần` when `unreadCategoryMap.SCHEDULE` is true.
2. No dot when `unreadCategoryMap.SCHEDULE` is false.
3. Collapsed sidebar renders dot on icon wrapper.

- [ ] **Step 5: Run Vitest, verify all pass**

Run: `npm --prefix frontend test -- --run`
Expected: All pass

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(notification): smart sidebar unread dots driven by notification category context"
```

---

## Task 5: Integration Verification & Regression Guard

- [ ] **Step 1: Run full backend test suite**

Run: `mvn test -f backend/pom.xml`
Expected: BUILD SUCCESS, all modules pass

- [ ] **Step 2: Run full frontend test suite**

Run: `npm --prefix frontend test -- --run`
Expected: 91+ files, 671+ tests, all pass

- [ ] **Step 3: Verify `application.yml` untouched**

Run: `git diff backend/app/src/main/resources/application.yml`
Expected: No changes

- [ ] **Step 4: Final commit & push**

```bash
git push origin feat/notification-reschedule-toast-sidebar
```
