import docx
import copy
import sys

def set_cell_content(cell, text, bold_title=False):
    """Sets multi-line text into a cell while preserving style and bolding first line if requested."""
    # Clear existing text
    cell.text = ""
    lines = text.strip().split("\n")
    for i, line in enumerate(lines):
        if i == 0:
            p = cell.paragraphs[0]
        else:
            p = cell.add_paragraph()
        p.style = "normal"
        run = p.add_run(line)
        if bold_title and i == 0:
            run.bold = True

def populate_uc_table(tbl, uc_data):
    """
    Populates a cloned 15-row Use Case table with data dict.
    uc_data keys:
    0: (name)
    1: (created_by, date_created)
    2: (primary_actor)
    3: (trigger)
    4: (description)
    5: (preconditions)
    6: (postconditions)
    7: (normal_flow)
    8: (alternative_flows)
    9: (exceptions)
    10: (priority)
    11: (frequency)
    12: (business_rules)
    13: (other_info)
    14: (assumptions)
    """
    # Row 0: UC ID and Name
    tbl.rows[0].cells[1].text = uc_data[0]
    tbl.rows[0].cells[1].paragraphs[0].runs[0].bold = True
    
    # Row 1: Created By & Date Created
    tbl.rows[1].cells[1].text = uc_data[1][0]
    tbl.rows[1].cells[3].text = uc_data[1][1]
    
    # Rows 2 to 6
    for r_idx in [2, 3, 4, 5, 6]:
        set_cell_content(tbl.rows[r_idx].cells[1], uc_data[r_idx])
        
    # Row 7: Normal Flow (bold title)
    set_cell_content(tbl.rows[7].cells[1], uc_data[7], bold_title=True)
    
    # Row 8: Alternative Flows (bold title)
    set_cell_content(tbl.rows[8].cells[1], uc_data[8], bold_title=True)
    
    # Row 9: Exceptions (bold title)
    set_cell_content(tbl.rows[9].cells[1], uc_data[9], bold_title=False)
    
    # Rows 10 to 14
    for r_idx in [10, 11, 12, 13, 14]:
        set_cell_content(tbl.rows[r_idx].cells[1], uc_data[r_idx])

def main():
    print("Loading docs/SRS Edufit.docx...")
    doc = docx.Document("docs/SRS Edufit.docx")
    
    # ==========================================
    # 1. TABLE 5: Group 3 Overview
    # ==========================================
    print("Updating Table 5 (Group 3 Overview)...")
    t5 = doc.tables[5]
    # Row 1: UC3.1
    t5.rows[1].cells[3].text = (
        "Initiates an authenticated, rate-limited (10/hr) parent-student account linkage. "
        "Explicit HTTP error status codes (404 Not Found, 400 Invalid Operation, 409 Conflict) "
        "replace legacy silent responses to eliminate silent failures from email typos."
    )
    # Row 5: UC3.5
    t5.rows[5].cells[3].text = (
        "Tutor accepts or declines a request; acceptance atomically creates the TutoringClass (ACTIVE) "
        "after a Dual Source Conflict Check against tutor active recurring schedules and scheduled sessions. "
        "Sessions follow a two-step consent flow (remaining PROPOSED until explicit confirmation)."
    )
    
    # ==========================================
    # 2. TABLE 6: Group 4 Overview
    # ==========================================
    print("Updating Table 6 (Group 4 Overview)...")
    t6 = doc.tables[6]
    # Add UC4.7
    r47 = t6.add_row()
    r47.cells[0].text = "UC4.7"
    r47.cells[1].text = "Propose Recurring Class Schedule"
    r47.cells[2].text = "Tutor, Student (Proposer)"
    r47.cells[3].text = (
        "Proposes weekly recurring lesson time slots for an active class across a 4-to-8 week bounded horizon "
        "to prevent schedule fragmentation and calendar data explosion."
    )
    # Add UC4.8
    r48 = t6.add_row()
    r48.cells[0].text = "UC4.8"
    r48.cells[1].text = "Respond to Recurring Schedule Proposal"
    r48.cells[2].text = "Student, Tutor (Responder)"
    r48.cells[3].text = (
        "Mutual consent workflow to accept or decline a recurring schedule; acceptance atomically replaces "
        "un-started future sessions in the window and enforces PostgreSQL GiST exclusion against double booking."
    )
    
    # ==========================================
    # 3. TABLE 36: UC3.1 Send Link Invitation
    # ==========================================
    print("Updating Table 36 (UC3.1 Send Link Invitation)...")
    t36 = doc.tables[36]
    # Row 4: Description
    set_cell_content(t36.rows[4].cells[1], (
        "Either party of a future Parent–Student link invites the other by email. The invitation grants nothing by itself: "
        "the link, and with it the Parent's access to the Student's data, exists only after the invited party accepts (UC3.2). "
        "Endpoints are authenticated and rate-limited (10 invitations/hour) with explicit error codes (404, 400, 409) to ensure unambiguous UX."
    ))
    # Row 5: Preconditions
    set_cell_content(t36.rows[5].cells[1], (
        "PRE-1: The user is logged in as a Student or a Parent and the account is ACTIVE (authenticated with Bearer JWT).\n"
        "PRE-2: The user knows the email address of the other party.\n"
        "PRE-3: The user has not exceeded the hourly rate limit of 10 invitations per hour (NFR-16)."
    ))
    # Row 6: Postconditions
    set_cell_content(t36.rows[6].cells[1], (
        "POST-1: If a matching account exists with the eligible opposite role and no conflicting link or invitation exists, "
        "an invitation with status PENDING is created (valid for 14 days, BR-29).\n"
        "POST-2: An in-app notification for the invited party is recorded (FR-12).\n"
        "POST-3: No access to any data is granted before the invitation is accepted (BR-27).\n"
        "POST-4: If the target email does not exist, has the wrong role, or conflicts with an existing link/cooldown, "
        "an explicit HTTP error response (404, 400, 409) is returned to inform the inviter immediately."
    ))
    # Row 7: Normal Flow
    set_cell_content(t36.rows[7].cells[1], (
        "3.1.0 Invite the other party by email\n"
        "1. User opens the linked-accounts page.\n"
        "2. System displays the existing links, the sent and received invitations (FR-14) and an \"Invite\" form.\n"
        "3. User enters the email address of the other party and submits.\n"
        "4. System validates the user quota (maximum 10 invitations per hour per user, NFR-16), validates the email format, and checks that it is not the user's own address.\n"
        "5. System looks for an ACTIVE account with the opposite role (a Student invites a Parent account; a Parent invites a Student account) via IAM Facade (BR-26).\n"
        "6. System checks that there is neither a CONFIRMED link nor a PENDING invitation between the two accounts (BR-28), and verifies that the pair is not in an active 7-day cooldown period following a previous decline (BR-29).\n"
        "7. System acquires a PostgreSQL advisory lock on the normalized user pair (Math.min(a,b), Math.max(a,b)) to serialize concurrent requests.\n"
        "8. System creates the invitation with status PENDING (valid for 14 days), records the in-app notification for the invited party (FR-12), and returns HTTP 201 Created with invitation details."
    ), bold_title=True)
    # Row 9: Exceptions
    set_cell_content(t36.rows[9].cells[1], (
        "3.1.0.E1 Invalid email format or own address (step 4): System returns HTTP 400 INVALID_OPERATION (\"Cannot send link invitation to own address or invalid email format\").\n"
        "3.1.0.E2 Rate limit exceeded (step 4): System returns HTTP 429 RATE_LIMIT_EXCEEDED when exceeding 10 invitations per hour (NFR-16).\n"
        "3.1.0.E3 Target account does not exist (step 5): System returns HTTP 404 RESOURCE_NOT_FOUND (\"No account found with this email address. Please check and try again.\").\n"
        "3.1.0.E4 Target account exists but has invalid role (step 5): System returns HTTP 400 INVALID_OPERATION (\"Target account does not have the eligible opposite role.\").\n"
        "3.1.0.E5 Confirmed link or pending invitation already exists (step 6): System returns HTTP 409 CONFLICT_DETECTED (\"Accounts are already linked or an invitation is currently pending.\").\n"
        "3.1.0.E6 Cooldown period active after recent decline (step 6): System returns HTTP 409 CONFLICT_DETECTED (\"An invitation between these accounts was declined recently. Please wait for the 7-day cooldown period to end.\") (BR-29).\n"
        "3.1.0.E7 System error while saving (step 8): Transaction is rolled back; System returns HTTP 500 and the user may retry."
    ))
    # Row 13: Other Information
    set_cell_content(t36.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-16 — Security & UX: Explicit error feedback & Abuse protection (Security)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-12 — Notification of link and connection events\n"
        "FR-13 — Automatic expiry\n"
        "FR-14 — Status tracking of invitations and requests\n"
        "Notes\n"
        "Architectural & UX Alignment (Connection Plan 2.2): The legacy anti-enumeration silent response (\"If a matching account exists, your invitation has been sent\") was superseded because the endpoint requires JWT authentication and enforces a strict rate limit of 10 invitations per hour per user (NFR-16). Returning explicit HTTP status codes (404, 400, 409) eliminates silent user failures when users mistype family members' emails, while the quota prevents brute-force enumeration.\n"
        "Two-Tier Concurrency Defense: Uses PostgreSQL Advisory Locks on the account pair plus database partial unique indexes (uq_link_invitation_pending) to prevent race conditions when both parties invite each other concurrently."
    ))
    
    # ==========================================
    # 4. TABLE 38: UC3.3 Revoke Link
    # ==========================================
    print("Updating Table 38 (UC3.3 Revoke Link)...")
    t38 = doc.tables[38]
    # Row 6: Postconditions
    set_cell_content(t38.rows[6].cells[1], (
        "POST-1: The link status is REVOKED, with the time and the actor recorded.\n"
        "POST-2: The Parent's access to the Student's data ends immediately (BR-31).\n"
        "POST-3: Data already created stays with the Student (BR-31).\n"
        "POST-4: The affected parties are notified (FR-12).\n"
        "POST-5 (Admin only): The revocation and reason are written to the audit log (FR-15).\n"
        "POST-6: Re-linking between the two accounts after revocation is permitted through a new invitation once revoked (BR-31, DB schema migration V1.11)."
    ))
    # Row 13: Other Info
    set_cell_content(t38.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-11 — Atomic saves (Reliability)\n"
        "NFR-15 — Concurrent state changes (Consistency)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-12 — Notification of link and connection events\n"
        "FR-15 — Audit log of link revocation by Admin\n"
        "Notes\n"
        "A Parent cannot revoke a link; this asymmetry protects the Student's control over his/her data. An Admin can intervene if a link was created by mistake or in case of dispute. Revoking ends access immediately and completely, without any grace period.\n"
        "Re-linking Lifecycle: In accordance with database schema migration V1.11 (allow_relink_after_revocation), revoking a link terminates immediate data access, but the student or parent may subsequently re-establish the relationship by initiating a new invitation once revoked."
    ))
    
    # ==========================================
    # 5. TABLE 40: UC3.5 Respond to Connection Request
    # ==========================================
    print("Updating Table 40 (UC3.5 Respond to Connection Request)...")
    t40 = doc.tables[40]
    # Row 4: Description
    set_cell_content(t40.rows[4].cells[1], (
        "The Tutor accepts or rejects a request. Acceptance creates the teaching relationship between the Tutor and the Student "
        "(the TutoringClass / Engagement), which is the precondition for scheduling (Group 4) and for learning plans and progress tracking (Group 5). "
        "The workflow adheres to a Two-Step Consent model: accepting the connection activates the class, while tutoring sessions remain in PROPOSED "
        "status until mutually confirmed by both parties."
    ))
    # Row 6: Postconditions
    set_cell_content(t40.rows[6].cells[1], (
        "POST-1 (accepted): The request is ACCEPTED and exactly one TutoringClass (Engagement) with status ACTIVE exists between the Tutor and the Student (BR-38). "
        "Initial tutoring sessions remain in PROPOSED status until explicitly confirmed as SCHEDULED by both sides.\n"
        "POST-2 (rejected): The request is REJECTED, with the optional reason; no TutoringClass is created.\n"
        "POST-3: The Student, and the Parent who sent the request if any, are notified (FR-12)."
    ))
    # Row 7: Normal Flow
    set_cell_content(t40.rows[7].cells[1], (
        "3.5.0 Accept a request\n"
        "1. Tutor opens the request inbox.\n"
        "2. System lists PENDING requests, oldest first, with the Student's first name, subject and date (FR-14).\n"
        "3. Tutor selects a request.\n"
        "4. System displays the data the Tutor needs to decide: the goal (subject, level, goal type, deadline), the desired slots, the message, and whether the request was sent by the Student or by a Parent; contact details and unrelated student goals remain hidden (BR-37 Privacy Whitelist).\n"
        "5. System executes a Dual Source Conflict Check, cross-referencing the student's desired weekly slots against (1) the tutor's existing SCHEDULED sessions and (2) active recurring class schedules, providing early conflict warnings.\n"
        "6. Tutor selects \"Accept\".\n"
        "7. System acquires a pessimistic write lock on the request, verifies that the request is still PENDING and the goal is ACTIVE, and executes an atomic transaction (@Transactional, NFR-11):\n"
        "   a. Sets the ConnectionRequest status to ACCEPTED.\n"
        "   b. Creates a new TutoringClass entity with status ACTIVE (BR-38).\n"
        "   c. Emits TutoringClassCreatedEvent to downstream modules.\n"
        "8. System records the notifications (FR-12) and displays a confirmation with the active class and a link to confirm or propose sessions (UC4.1). Initial sessions remain in PROPOSED status until explicitly confirmed (Two-Step Scheduling Consent)."
    ), bold_title=True)
    # Row 9: Exceptions
    set_cell_content(t40.rows[9].cells[1], (
        "3.5.0.E1 Request cancelled or expired in the meantime (steps 3-7): System informs the Tutor that the request is no longer open and refreshes the inbox (NFR-15).\n"
        "3.5.0.E2 An ACTIVE TutoringClass with the Student for this goal already exists (step 7): System does not create a duplicate class and returns HTTP 409 CONFLICT (BR-38).\n"
        "3.5.0.E3 Goal is no longer ACTIVE or Tutor verification revoked (step 7): System refuses the acceptance and returns HTTP 400 INVALID_OPERATION.\n"
        "3.5.0.E4 System error (step 7): The transaction is rolled back; the request stays PENDING and the Tutor may retry (NFR-11)."
    ))
    # Row 13: Other Info
    set_cell_content(t40.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-11 — Atomic saves (Reliability)\n"
        "NFR-15 — Concurrent state changes (Consistency)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-12 — Notification of link and connection events\n"
        "FR-13 — Automatic expiry\n"
        "FR-14 — Status tracking of invitations and requests\n"
        "Notes\n"
        "Data shown to the Tutor before acceptance is deliberately limited via the BR-37 Privacy Whitelist. Batch hydration is used to load request details in O(1) database queries, eliminating N+1 performance bottlenecks.\n"
        "Two-Step Scheduling Consent: Accepting the connection request activates the class entity (TutoringClass), establishing the administrative relationship. Proposed sessions do not automatically become SCHEDULED until both sides have confirmed the specific date/time, protecting calendars from unverified slots."
    ))
    
    # ==========================================
    # 6. TABLE 41: Group 3 Business Rules
    # ==========================================
    print("Updating Table 41 (Group 3 Business Rules)...")
    t41 = doc.tables[41]
    # Row 5: BR-28
    t41.rows[5].cells[2].text = (
        "Between the same two accounts there can be at most one PENDING invitation, and no invitation is possible while a CONFIRMED link exists. "
        "Enforced via PostgreSQL partial unique indexes (uq_link_invitation_pending and uq_parent_student_confirmed) and advisory locks."
    )
    # Row 6: BR-29
    t41.rows[6].cells[2].text = (
        "A PENDING invitation expires after 14 days. After a decline, the same inviter cannot invite the same account again for 7 days "
        "(cooldown enforced via HTTP 409 CONFLICT_DETECTED)."
    )
    # Row 8: BR-31
    t41.rows[8].cells[2].text = (
        "When a link is revoked, the Parent's access to the Student's data ends immediately. Data already created (requests, engagements, sessions, notes) "
        "stays with the Student. The same accounts can be linked again only through a new invitation (supported by DB schema migration V1.11 allow_relink_after_revocation)."
    )
    # Row 11: BR-34
    t41.rows[11].cells[2].text = (
        "Between one Student and one tutor there can be at most one PENDING request, and none while an ACTIVE engagement exists. "
        "A Student can have at most 5 PENDING requests at the same time."
    )
    # Row 15: BR-38
    t41.rows[15].cells[2].text = (
        "Accepting a request atomically creates exactly one ACTIVE TutoringClass (Engagement) between the tutor and the Student. "
        "Pre-checks desired slots against the tutor's existing scheduled sessions and active recurring schedules (Dual Source Conflict Check). "
        "The TutoringClass is the precondition for scheduling sessions, learning plans and progress tracking. Tutoring sessions follow a "
        "Two-Step Consent flow: sessions remain in PROPOSED status until explicitly confirmed as SCHEDULED by both parties."
    )
    
    # ==========================================
    # 7. TABLE 43: Group 3 NFR
    # ==========================================
    print("Updating Table 43 (Group 3 NFR)...")
    t43 = doc.tables[43]
    # Row 4: NFR-16
    t43.rows[4].cells[1].text = "Security & UX — Explicit error feedback & Abuse protection"
    t43.rows[4].cells[2].text = (
        "Endpoints require JWT authentication and enforce a rate limit of 10 invitations per hour per user. Explicit error status codes "
        "(404 Not Found, 400 Invalid Operation, 409 Conflict) are returned to prevent silent user failure when an inviter mistypes an email address, "
        "while the rate limit protects against brute-force account enumeration."
    )
    
    # ==========================================
    # 8. TABLE 44: UC4.1 Propose Session
    # ==========================================
    print("Updating Table 44 (UC4.1 Propose Session)...")
    t44 = doc.tables[44]
    set_cell_content(t44.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-11 — Atomic saves (Reliability)\n"
        "NFR-15 — Concurrent state changes (Consistency)\n"
        "NFR-18 — Time zone handling (Consistency)\n"
        "NFR-20 — Minimal disclosure in conflict feedback (Privacy)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-16 — Session notifications\n"
        "FR-17 — Automatic expiry of session proposals\n"
        "FR-19 — Session list and calendar\n"
        "FR-21 — Session history\n"
        "FR-22 — Repeat note\n"
        "Notes\n"
        "One proposal creates exactly one session. For one-off sessions, each session is proposed and accepted separately. "
        "For establishing a recurring weekly schedule across multiple weeks for an active class, users utilize the Bounded Horizon "
        "Recurring Class Schedule workflow (UC4.7 & UC4.8). A proposal made by the Tutor is not limited to the Tutor's declared availability, "
        "because the Tutor decides about his/her own time (BR-41)."
    ))
    
    # ==========================================
    # 9. TABLE 45: UC4.2 Check Schedule Conflict
    # ==========================================
    print("Updating Table 45 (UC4.2 Check Schedule Conflict)...")
    t45 = doc.tables[45]
    set_cell_content(t45.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-17 — Double-booking protection (Two-Tier Concurrency Defense)\n"
        "NFR-18 — Time zone handling (Consistency)\n"
        "NFR-19 — Schedule check response time (Performance)\n"
        "NFR-20 — Minimal disclosure in conflict feedback (Privacy)\n"
        "Notes\n"
        "This check gives early feedback only at application level. The deterministic guarantee against double booking is enforced inside "
        "the saving transaction of UC4.3 at the storage engine level via PostgreSQL GiST Exclusion Constraints (btree_gist) and JPA @Version "
        "optimistic locking (NFR-17). Only SCHEDULED sessions block a calendar; PROPOSED sessions and reschedule proposals do not (BR-42)."
    ))
    
    # ==========================================
    # 10. TABLE 46: UC4.3 Respond to Session Proposal
    # ==========================================
    print("Updating Table 46 (UC4.3 Respond to Session Proposal)...")
    t46 = doc.tables[46]
    # Row 7: Normal Flow
    set_cell_content(t46.rows[7].cells[1], (
        "4.3.0 Accept a proposal\n"
        "1. User opens the proposal.\n"
        "2. System displays the proposer, date, time, mode, place, repeat note and message; for a reschedule it shows the current time next to the new time.\n"
        "3. User selects \"Accept\".\n"
        "4. System asks for confirmation.\n"
        "5. User confirms.\n"
        "6. System checks that the proposal is still PENDING and not expired. In the same atomic transaction (@Transactional), system acquires a pessimistic row lock and verifies interval availability against the database (BR-42, NFR-17, NFR-15).\n"
        "7. System sets the session status to SCHEDULED (or updates to the new time and releases the old slot) and writes the session history. The PostgreSQL GiST Exclusion Constraint (ex_tutoring_session_no_tutor_overlap) guarantees that concurrent requests cannot double-book the tutor or student (NFR-11, FR-21).\n"
        "8. System records the notifications (FR-16) and displays a confirmation with the session in the calendar."
    ), bold_title=True)
    # Row 9: Exceptions
    set_cell_content(t46.rows[9].cells[1], (
        "4.3.0.E1 Proposal no longer PENDING: withdrawn, answered by another eligible person, or expired (steps 1-6): System informs the user of the current status and refreshes the list (NFR-15).\n"
        "4.3.0.E2 Double booking / slot overlap detected by database GiST exclusion constraint (step 6-7): PostgreSQL throws exclusion_violation (SQLSTATE 23P01), mapped to ResourceConflictException. System returns HTTP 409 CONFLICT (\"Khung giờ học bị trùng lặp với buổi học đã có.\"), keeping the proposal PENDING so the user can suggest another time (NFR-17, NFR-20).\n"
        "4.3.0.E3 A user from the proposer's own side tries to respond, for example a Parent accepting a proposal that the Student made (any step): System denies with an authorization error and changes nothing (BR-40, NFR-07).\n"
        "4.3.0.E4 The link was revoked or the Engagement is no longer ACTIVE (steps 1-7): System stops and informs the user; nothing is changed (NFR-07).\n"
        "4.3.0.E5 System error (step 7): The transaction is rolled back; the proposal stays PENDING and the user may retry (NFR-11)."
    ))
    # Row 13: Other Info
    set_cell_content(t46.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-11 — Atomic saves (Reliability)\n"
        "NFR-15 — Concurrent state changes (Consistency)\n"
        "NFR-17 — Double-booking protection (Two-Tier Concurrency Defense)\n"
        "NFR-20 — Minimal disclosure in conflict feedback (Privacy)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-16 — Session notifications\n"
        "FR-19 — Session list and calendar\n"
        "FR-21 — Session history\n"
        "FR-22 — Repeat note\n"
        "Notes\n"
        "When the Tutor proposes and several eligible people exist on the learner side (the Student and linked Parents), the first valid response takes effect and the others are told the current status (NFR-15).\n"
        "Storage Engine Double-Booking Defense: PostgreSQL GiST exclusion constraint (btree_gist) acts as the ultimate defensive barrier against race conditions during simultaneous acceptances, guaranteeing that no two overlapping SCHEDULED sessions can ever be committed to the database."
    ))
    
    # ==========================================
    # 11. TABLE 47: UC4.4 Reschedule Session
    # ==========================================
    print("Updating Table 47 (UC4.4 Reschedule Session)...")
    t47 = doc.tables[47]
    set_cell_content(t47.rows[13].cells[1], (
        "Non-functional requirements (details in Appendix)\n"
        "NFR-07 — Server-side authorization (Security)\n"
        "NFR-11 — Atomic saves (Reliability)\n"
        "NFR-15 — Concurrent state changes (Consistency)\n"
        "NFR-17 — Double-booking protection (Two-Tier Concurrency Defense)\n"
        "NFR-20 — Minimal disclosure in conflict feedback (Privacy)\n"
        "Related functional requirements (details in Appendix)\n"
        "FR-16 — Session notifications\n"
        "FR-17 — Automatic expiry of session proposals\n"
        "FR-21 — Session history\n"
        "Notes\n"
        "Because the original slot stays valid, a rejected, withdrawn or expired reschedule never leaves the Student without a session. "
        "The rescheduled interval is validated at database level via PostgreSQL GiST exclusion upon acceptance. "
        "For altering recurring weekly schedules for the entire class, use UC4.7 & UC4.8."
    ))
    
    # ==========================================
    # 12. TABLE 50: Group 4 Business Rules
    # ==========================================
    print("Updating Table 50 (Group 4 Business Rules)...")
    t50 = doc.tables[50]
    # Row 3: BR-41
    t50.rows[3].cells[2].text = (
        "A session starts at least 2 hours after the moment of proposal, ends after it starts and lasts between 30 and 240 minutes. "
        "Intervals are evaluated as half-open [start, end). A proposal by the Student or a Parent must lie inside one of the Tutor’s "
        "declared weekly availability slots; a proposal by the Tutor is not restricted. The same rules apply to individual reschedules."
    )
    # Row 4: BR-42
    t50.rows[4].cells[2].text = (
        "No double booking & Storage-level concurrency defense: A Tutor or a Student cannot have two SCHEDULED sessions that overlap. "
        "Intervals are strictly half-open [start, end): a session ending at 19:00 and one starting at 19:00 do not overlap. "
        "Only SCHEDULED sessions occupy a calendar. Double booking is deterministically prevented at the database storage engine using "
        "PostgreSQL GiST Exclusion Constraints (btree_gist extension: EXCLUDE USING gist (tutor_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&) "
        "WHERE (status IN ('SCHEDULED', 'IN_PROGRESS'))) and JPA @Version optimistic locking. "
        "Any concurrent conflict triggers SQLSTATE 23P01 (exclusion_violation) and returns HTTP 409 CONFLICT (SCHEDULE_OVERLAP)."
    )
    # Row 7: BR-45
    t50.rows[7].cells[2].text = (
        "Reschedule and Recurring Class Schedule (Bounded Horizon): Only a SCHEDULED session that has not started can be rescheduled individually (UC4.4); "
        "it stays SCHEDULED at its original time until accepted. For long-term weekly schedules of an active class, the Tutor or Student may propose a "
        "Recurring Class Schedule (UC4.7) across a 4-to-8 week bounded horizon. Updating the recurring schedule (UC4.8) requires mutual consent, "
        "atomically replaces un-started future sessions of the class within the bounded window, and rolls back with HTTP 409 CONFLICT if any generated slot triggers a GiST exclusion violation."
    )
    # Row 8: BR-46
    t50.rows[8].cells[2].text = (
        "Cancellation: Any party of a SCHEDULED session (Tutor, Student or linked Parent) can cancel it without the other party’s consent, before it starts, "
        "with a mandatory reason. A cancellation less than 12 hours before start is labelled 'late' and the label is visible to the other party. "
        "No fee applies in this version. After the start time a session can no longer be cancelled; its outcome is recorded instead (BR-47)."
    )
    
    # ==========================================
    # 13. TABLE 51: Group 4 Functional Requirements
    # ==========================================
    print("Updating Table 51 (Group 4 Functional Requirements)...")
    t51 = doc.tables[51]
    # Add FR-23
    r23 = t51.add_row()
    r23.cells[0].text = "FR-23"
    r23.cells[1].text = "Recurring Class Schedule Management"
    r23.cells[2].text = (
        "The system provides capabilities to propose (UC4.7) and respond to (UC4.8) weekly recurring class schedules within a 4-to-8 week bounded horizon. "
        "On mutual acceptance, future un-started sessions of the class within the horizon are atomically replaced by newly generated sessions, "
        "protected by database GiST exclusion constraints."
    )
    
    # ==========================================
    # 14. TABLE 52: Group 4 Non-Functional Requirements
    # ==========================================
    print("Updating Table 52 (Group 4 Non-Functional Requirements)...")
    t52 = doc.tables[52]
    # Row 1: NFR-17
    t52.rows[1].cells[1].text = "Consistency — Double-booking protection (Two-Tier Concurrency Defense)"
    t52.rows[1].cells[2].text = (
        "Double-booking protection is deterministically enforced at the database storage engine using PostgreSQL GiST Exclusion Constraints "
        "(btree_gist extension on tutor_id WITH = and tstzrange(start_at, end_at, '[)') WITH && WHERE status IN ('SCHEDULED', 'IN_PROGRESS')). "
        "This is combined with JPA @Version optimistic locking on tutoring_session entities and pessimistic write locking "
        "(@Lock(LockModeType.PESSIMISTIC_WRITE)) during state transitions. Early application-level validation provides prompt user feedback, "
        "while the storage engine GiST constraint prevents race conditions under high concurrency across distributed pods, "
        "throwing SQLSTATE 23P01 mapped to HTTP 409 CONFLICT."
    )
    
    # ==========================================
    # 15. INSERT UC4.7 AND UC4.8 TABLES AFTER TABLE 49
    # ==========================================
    print("Inserting UC4.7 and UC4.8 specification tables after Table 49...")
    t49 = doc.tables[49]
    t44_template = doc.tables[44]
    
    # Clone template for UC4.7
    t47_elem = copy.deepcopy(t44_template._tbl)
    t47_table = docx.table.Table(t47_elem, doc)
    
    uc47_data = {
        0: "UC4.7 Propose Recurring Class Schedule",
        1: ("Hieu", "20-Sep-2026"),
        2: "Tutor, Student (the proposing party)",
        3: "A Tutor or Student selects \"Propose Recurring Schedule\" on the active class details or calendar view.",
        4: (
            "Proposes a weekly recurring lesson schedule (e.g., Tuesdays and Thursdays 18:00–20:00) for an active TutoringClass "
            "across a bounded horizon of 4 to 8 weeks. Establishing or updating a recurring schedule requires mutual consent (UC4.8) "
            "and supersedes future un-started sessions of the class within the horizon upon acceptance."
        ),
        5: (
            "PRE-1: The user is logged in as a Tutor, Student, or Parent with CONFIRMED link.\n"
            "PRE-2: The TutoringClass is in ACTIVE status (BR-38).\n"
            "PRE-3: The proposed weekly time slots satisfy BR-41 (duration 30–240 minutes, half-open intervals). "
            "If proposed by learner side, slots must lie within the Tutor's declared weekly availability.\n"
            "PRE-4: The recurrence horizon (effectiveFrom to effectiveTo) is bounded between 4 and 8 weeks."
        ),
        6: (
            "POST-1: A RecurringProposal entity with status PENDING is created and stored with proposed weekly slots, effective date window, and reason.\n"
            "POST-2: Existing scheduled sessions remain completely unchanged until the proposal is mutually accepted (BR-45).\n"
            "POST-3: The opposite party is notified via in-app notification (FR-16).\n"
            "POST-4: The proposal appears in the class and scheduling views for both parties (FR-19)."
        ),
        7: (
            "4.7.0 Propose a recurring schedule for a class\n"
            "1. User opens the active TutoringClass details page and selects \"Propose Recurring Schedule\".\n"
            "2. System displays the recurring schedule modal: day-of-week slot selector, session start/end times (or duration), effective date range (default 4 to 8 weeks), mode (online/offline), and an optional reason (up to 300 characters). The Tutor's current weekly availability and existing class commitments are displayed as guidance.\n"
            "3. User selects the desired weekly slots (e.g., Tue & Thu 18:00–20:00), specifies the effective date window, and submits.\n"
            "4. System validates that the TutoringClass is ACTIVE, the time slots conform to BR-41, and the effective window satisfies the 4-to-8 week bounded horizon constraint.\n"
            "5. System checks for early conflicts against the proposing party's calendar.\n"
            "6. System saves the RecurringProposal with status PENDING and records the proposal history (NFR-11, FR-21).\n"
            "7. System publishes RecurringScheduleProposedEvent, records in-app notifications for the responding party (FR-16), and returns HTTP 201 Created.\n"
            "8. System displays confirmation: \"Recurring schedule proposal sent. Waiting for mutual confirmation.\""
        ),
        8: (
            "4.7.1 Withdraw recurring proposal (starts from class schedule view)\n"
            "a. The proposing party selects \"Withdraw\" on a PENDING recurring schedule proposal.\n"
            "b. System updates the proposal status to WITHDRAWN and notifies the other party."
        ),
        9: (
            "4.7.0.E1 Invalid recurrence horizon (step 4): Window is less than 4 weeks or greater than 8 weeks. System highlights the date range and prompts the user to select a valid bounded horizon.\n"
            "4.7.0.E2 Invalid slot times or learner-side slots outside tutor availability (step 4): System flags invalid slots according to BR-41.\n"
            "4.7.0.E3 TutoringClass no longer ACTIVE (step 4): System rejects the proposal and displays an error (BR-38).\n"
            "4.7.0.E4 A recurring proposal is already PENDING for this class (step 2): System displays the existing proposal and prompts the user to await a response or withdraw it first.\n"
            "4.7.0.E5 System error while saving (step 6): Transaction is rolled back; System returns HTTP 500 and user may retry (NFR-11)."
        ),
        10: "High (Must Have)",
        11: "Low to medium; typically once at class onboarding or upon term schedule change.",
        12: "BR-18, BR-38, BR-39, BR-40, BR-41, BR-42, BR-45",
        13: (
            "Non-functional requirements (details in Appendix)\n"
            "NFR-07 — Server-side authorization (Security)\n"
            "NFR-11 — Atomic saves (Reliability)\n"
            "NFR-15 — Concurrent state changes (Consistency)\n"
            "NFR-18 — Time zone handling (Consistency)\n"
            "Related functional requirements (details in Appendix)\n"
            "FR-16 — Session notifications\n"
            "FR-19 — Session list and calendar\n"
            "FR-21 — Session history\n"
            "FR-23 — Recurring class schedule management\n"
            "Notes\n"
            "Bounded Horizon: Bounding recurrence to 4–8 weeks prevents calendar data bloat and reduces long-range scheduling uncertainty. "
            "Once established, individual exceptions (e.g. sick leave or exam reschedule) are handled through individual session rescheduling (UC4.4) "
            "or cancellation (UC4.5) without altering the master recurring schedule."
        ),
        14: "Recurring schedules apply to 1-on-1 classes. All session dates generated from the recurring rule are stored as individual TutoringSession records with UTC timestamps."
    }
    populate_uc_table(t47_table, uc47_data)
    
    # Clone template for UC4.8
    t48_elem = copy.deepcopy(t44_template._tbl)
    t48_table = docx.table.Table(t48_elem, doc)
    
    uc48_data = {
        0: "UC4.8 Respond to Recurring Schedule Proposal",
        1: ("Hieu", "20-Sep-2026"),
        2: "Student, Tutor (the responding party)",
        3: "A user opens a pending recurring schedule proposal from a notification or from the class schedule view.",
        4: (
            "The responding party accepts or declines a proposed weekly recurring schedule for an active TutoringClass. "
            "On acceptance, an atomic transaction cancels future un-started sessions of the class within the bounded horizon, "
            "batch-generates new weekly sessions, and commits them under PostgreSQL GiST exclusion constraint protection against double booking."
        ),
        5: (
            "PRE-1: The user is logged in as the eligible responding party for the active TutoringClass (BR-40).\n"
            "PRE-2: A RecurringProposal exists in PENDING status for the class."
        ),
        6: (
            "POST-1 (accepted): The RecurringProposal status is ACCEPTED. In an atomic transaction, future un-started SCHEDULED sessions "
            "of this class within the horizon are cancelled, and new weekly sessions corresponding to the proposed slots are batch-inserted "
            "in SCHEDULED status (BR-42, BR-45, NFR-11, NFR-17).\n"
            "POST-2 (declined): The RecurringProposal status is REJECTED with optional reason; existing sessions remain unchanged.\n"
            "POST-3: Both parties are notified of the decision (FR-16) and the event is recorded in audit history (FR-21)."
        ),
        7: (
            "4.8.0 Accept a recurring schedule proposal\n"
            "1. User opens the pending recurring schedule proposal.\n"
            "2. System displays the proposed weekly slots (day of week, start/end times), effective horizon dates, proposer identity, and reason.\n"
            "3. User selects \"Accept\".\n"
            "4. System prompts for final confirmation.\n"
            "5. User confirms.\n"
            "6. System starts an atomic database transaction (@Transactional, NFR-11):\n"
            "   a. Locks the RecurringProposal row and verifies status is PENDING.\n"
            "   b. Cancels all future SCHEDULED sessions for this class falling within [effectiveFrom, effectiveTo] that have not yet started.\n"
            "   c. Generates concrete session instances for each week within the bounded horizon based on the agreed weekly slots.\n"
            "   d. Batch-inserts the new sessions in SCHEDULED status into the tutoring_session table.\n"
            "   e. PostgreSQL GiST Exclusion Constraint validates all newly generated intervals against existing SCHEDULED sessions across the entire database.\n"
            "   f. Sets the RecurringProposal status to ACCEPTED.\n"
            "7. System publishes RecurringScheduleUpdatedEvent and sends in-app notifications to both parties (FR-16).\n"
            "8. System returns HTTP 200 OK and renders the updated schedule in the calendar view."
        ),
        8: (
            "4.8.1 Decline the recurring proposal (branches at step 3)\n"
            "a. User selects \"Decline\" and optionally enters a reason (up to 300 characters).\n"
            "b. System sets the RecurringProposal status to REJECTED, preserves existing scheduled sessions without modification, and notifies the proposer (FR-16)."
        ),
        9: (
            "4.8.0.E1 Proposal no longer PENDING (steps 2-6): Proposal was withdrawn, already responded to, or expired. System notifies user and refreshes view (NFR-15).\n"
            "4.8.0.E2 Schedule conflict detected during batch insertion (step 6): One or more generated session intervals overlap with an existing SCHEDULED session of the tutor or student. PostgreSQL throws exclusion_violation (SQLSTATE 23P01). The entire transaction is rolled back 100% (existing sessions remain intact). System returns HTTP 409 CONFLICT (\"One or more recurring slots conflict with an already scheduled session\") (BR-42, NFR-17).\n"
            "4.8.0.E3 Proposer attempts to respond to own proposal (step 3): System denies authorization and returns HTTP 403 FORBIDDEN (BR-40, NFR-07).\n"
            "4.8.0.E4 Class no longer ACTIVE (step 6): System aborts transaction and returns HTTP 400 INVALID_OPERATION.\n"
            "4.8.0.E5 System error during batch transaction (step 6): Entire transaction rolls back; no partial session generation occurs; System returns HTTP 500 (NFR-11)."
        ),
        10: "High (Must Have)",
        11: "Low to medium; matches UC4.7.",
        12: "BR-18, BR-38, BR-39, BR-40, BR-41, BR-42, BR-45",
        13: (
            "Non-functional requirements (details in Appendix)\n"
            "NFR-07 — Server-side authorization (Security)\n"
            "NFR-11 — Atomic saves (Reliability)\n"
            "NFR-15 — Concurrent state changes (Consistency)\n"
            "NFR-17 — Double-booking protection (Two-Tier Concurrency Defense)\n"
            "Related functional requirements (details in Appendix)\n"
            "FR-16 — Session notifications\n"
            "FR-19 — Session list and calendar\n"
            "FR-21 — Session history\n"
            "FR-23 — Recurring class schedule management\n"
            "Notes\n"
            "All-or-Nothing Atomic Batch Generation: Thanks to PostgreSQL GiST Exclusion Constraints and transactional rollback, "
            "either ALL weekly sessions within the bounded horizon are successfully created and guaranteed conflict-free, "
            "or NONE are created and the prior schedule remains completely undisturbed."
        ),
        14: "Acceptance applies to all weeks within the horizon. Single-session overrides during the horizon are managed via UC4.4/UC4.5."
    }
    populate_uc_table(t48_table, uc48_data)
    
    # Create paragraphs for insertion
    p_spacer1 = doc.add_paragraph(" ")
    p_h47 = doc.add_paragraph("UC4.7 Propose Recurring Class Schedule", style="Heading 3")
    p_spacer2 = doc.add_paragraph(" ")
    p_h48 = doc.add_paragraph("UC4.8 Respond to Recurring Schedule Proposal", style="Heading 3")
    
    # Remove from document end
    for p in [p_spacer1, p_h47, p_spacer2, p_h48]:
        doc._body._body.remove(p._p)
        
    # Insert sequence right after Table 49
    curr = t49._tbl
    for item in [p_spacer1._p, p_h47._p, t47_elem, p_spacer2._p, p_h48._p, t48_elem]:
        curr.addnext(item)
        curr = item
        
    print("Saving updated document to docs/SRS Edufit.docx...")
    doc.save("docs/SRS Edufit.docx")
    print("Update completed successfully!")

if __name__ == "__main__":
    main()
