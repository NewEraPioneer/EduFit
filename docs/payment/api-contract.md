# Payment API contract

> Cập nhật 26/10/2026: [quy tắc hiện hành và trải nghiệm Payment](payment-usability.md). Xác nhận không tất toán sớm; đủ 24h phản hồi mới tự thu; hết 48h tranh chấp chuyển Admin và tiếp tục giữ tiền.

Cập nhật 24/10/2026, gồm bản sửa tài khoản ảo V1.18 sau code `3966d54`. Nghiệp vụ tại [implementation plan](payment_implementation_plan.md), vận hành tại [runbook](runbook.md).

## 1. Quy ước

- Base learner/tutor: `/api/v1/payment`; Admin: `/api/v1/admin/payment`.
- Dùng cookie `EDUFIT_SESSION` và CSRF như SDK `fetchApi`. Actor từ CurrentUser, không nhận userId/role tin cậy từ body.
- Tiền VND nguyên; UUID cho ID nội bộ. Không lưu balance ở client.
- API thông thường trả qua `ApiResponse`; webhook thành công trả `{"success":true}`.
- Wallet/history/Admin overview dùng `offset` mặc định 0, trong khoảng 0..100000; từng danh sách tối đa 50 dòng, không phải page number.
- Ví/topup/withdrawal history trả field snake_case của projection DB; session view dùng camelCase. FE types ở `frontend/src/features/payment/api/paymentApi.ts`.
- Validation request và lỗi nghiệp vụ theo exception handler chung. Anonymous wallet 401, actor không đủ quyền 403; không dựa vào ẩn nút FE để cấp quyền.

## 2. Ví và nạp/rút

| Method/path sau base | Input | Quyền và kết quả |
|---|---|---|
| GET /wallet?offset=0 | Không body | Authenticated, chỉ ví actor; wallet, entries, topups, withdrawals, topupEnabled/topupProvider |
| POST /topups | `{amount}` | Student/Parent; 10.000..100.000.000; tạo PENDING và checkout/QR, chưa credit |
| POST /topups/{id}/refresh | Không body | Owner/Admin; lookup payOS có chữ ký, lease 30s; trả topup hiện tại |
| POST /withdrawals | `{amount,bank,account,holder,bankBin?}` | Tutor; giữ tiền, tạo PENDING; một yêu cầu đang chờ/xử lý |
| POST /webhooks/payos | Raw JSON payOS có signature | Public, miễn CSRF chỉ path này; xác minh receipt rồi credit một lần |

Withdrawal: amount 10.000..100.000.000; bank không trống tối đa 100 ký tự; account 5..34 chữ số; holder không trống tối đa 150; bankBin nếu có là 6 chữ số. UI hiện gửi BIN từ lựa chọn ngân hàng. Bản ghi/client cũ không BIN tiếp tục manual, không tự đoán theo tên.

Checkout response: id/provider và checkoutUrl cho PAYOS; manual trả qrUrl, bankAccount, bankHolder, description, amount. Tạo lại topup là yêu cầu mới, không có client idempotency key cho tạo topup.

Wallet trả `available`/`held`. Topup status: PENDING/PAID/CANCELLED trong schema; không có workflow tự đồng bộ đầy đủ trạng thái hủy/hết hạn provider. Withdrawal status: PENDING/PROCESSING/PAID/REJECTED/FAILED.

Webhook kiểm chữ ký, order/provider, amount, currency VND, tài khoản nhận và link ID. Probe ký hợp lệ không khớp order chỉ ACK, không credit. Lookup kiểm response ký, order/amount/PAID/amountPaid và đúng một transaction khớp tài khoản/amount/reference/link; không có bước currency riêng như webhook.

Thiếu khóa payOS nhưng đủ manual config thì dùng manual QR. Thiếu cả hai thì topupEnabled=false và tạo topup bị từ chối. Bấm return URL không thay đổi tiền.

## 3. Học phí theo buổi

| Method/path sau base | Input | Điều kiện |
|---|---|---|
| GET /sessions?from=ISO&to=ISO | Instant có timezone | Buổi của lớp actor được xem, khoảng tối đa 93 ngày |
| GET /sessions/{id} | UUID session | Student/linked Parent/tutor buổi đó |
| POST /sessions/{id}/fund | `{amount}` | Student/confirmed Parent; 1..100.000.000, khớp giá hiện hành; đủ available |
| POST /sessions/{id}/confirm | Không body | Learner có quyền hoặc tutor; COMPLETED và qua endAt |
| POST /sessions/{id}/dispute | `{text,category?,evidence?}` | Participant; text 1..2000, evidence tối đa 4000, category DURATION/NO_SHOW/TECHNICAL/QUALITY/OTHER (mặc định OTHER); trong cửa sổ phản hồi |
| POST /sessions/{id}/report | `{amount,text}` | Payer hoặc Tutor; amount 0..charge.amount; text 1..4000; trước deadline 48h |

| POST /sessions/{id}/comments | `{messageId,text}` | Participant; UUID idempotency, text 1..4000; 20 phản hồi/người/hồ sơ, chỉ khi DISPUTED/AWAITING_ADMIN/ADMIN_REVIEW |

Session response gồm sessionId/classId/studentId/tutorName/sessionStatus/startAt/endAt, amount/commissionBps/payerId/status, hai accepted flags/autoAccepted, disputeDeadline/reason, hai amount/report, settledAmount và canFund/canActAsLearner/canActAsTutor. Thêm reviewDeadline, canSubmitProposal, isPayer, disputeCategory, disputeEvidence, disputeReporterId, resolutionNote, resolvedAt, comments (id/side/message/created_at).

Status view: UNFUNDED/FUNDED/AWAITING_CONFIRMATION/DISPUTED/AWAITING_ADMIN/ADMIN_REVIEW/SETTLED/REFUNDED/EXPIRED. UNFUNDED không có charge trong DB.

- Fund chỉ khi nguồn SCHEDULED/COMPLETED và trước max(endAt,outcomeRecordedAt)+24h; snapshot giá và commission 1000 bps.
- Student được confirm/báo vấn đề/bổ sung thông tin khi Parent trả, không đề xuất số tiền; Parent cần là payer và còn confirmed link. Parent khác payer chỉ đọc nếu còn link.
- Không có khiếu nại và COMPLETED: tự tất toán sau reviewDeadline (reviewStartedAt+24h) dù thiếu xác nhận; hai phía xác nhận không tất toán sớm. reviewStartedAt lưu/thông báo một lần khi mở cửa sổ, không lùi về quá khứ khi ghi hoàn thành muộn.
- Hai báo cáo cùng mức trong 48h → AWAITING_ADMIN; không tự hết hạn lúc chờ Admin.
- DISPUTED hết 48h chưa khớp → ADMIN_REVIEW, tiếp tục giữ held chờ phân xử.
- Các state cuối không mở lại charge/cấp tiền lần hai.
- Hoàn held là tăng available nội bộ, không phải chuyển khoản hoàn ngân hàng.

## 4. Admin

Role ADMIN được kiểm ở HTTP security và application.

| Method/path sau Admin base | Input | Tác dụng |
|---|---|---|
| GET ?offset=0 | Offset | Topups/withdrawals và configuration |
| GET /configuration | Không | topupReady/manualReady/payoutReady/payoutEnabled/frontendUrl/webhookPath; không secret |
| GET /disputes?offset=0 | Offset | Các charge AWAITING_ADMIN và ADMIN_REVIEW |
| POST /topups/{id}/confirm | `{approve:true,note,reference}` | Chỉ manual; Admin đã đối chiếu receipt, reference thực 6..200 ký tự |
| POST /withdrawals/{id}/review | `{approve,note,reference?}` | Chỉ PENDING; true=đã chuyển thủ công thật, false=từ chối/hoàn held |
| POST /withdrawals/{id}/payout | `{approve:true,note}` | Dispatch chi qua provider; yêu cầu kênh chi sẵn sàng và BIN |
| POST /withdrawals/{id}/refresh | Không | Lookup payout, lease 60s; không tạo lại lệnh |
| GET /disputes/{id} | Không | Chi tiết hồ sơ, chỉ Admin |
| POST /disputes/{id}/resolve | `{amount,note}` | Admin; amount nguyên 0..charge.amount, note 1..2000; phân xử AWAITING_ADMIN/ADMIN_REVIEW |
| POST /disputes/{id}/review | `{approve,note}` | Duyệt đúng mức thống nhất hoặc từ chối/hoàn toàn bộ |

DTO Review dùng chung: approve boolean; note bắt buộc 1..2000 ký tự; reference nếu có tối đa 200. Khi manual paid, reference thực bắt buộc và trim. Dispute review cũ không nhận số tiền mới. Resolve mới cho phép Admin quyết định số tiền, chỉ tính phí >0 sau endAt; có thể phân xử nguồn chưa COMPLETED dựa bằng chứng, khác với auto-settlement bắt buộc COMPLETED.

Lưu ý contract hiện có: topup confirm dùng reference, không có nhánh từ chối topup; approve/note vẫn qua DTO validation nhưng không điều khiển credit. Endpoint payout tự mang nghĩa duyệt chi và dùng note, không đọc approve; muốn từ chối PENDING dùng endpoint review với approve=false. Không gửi approve=false tới confirm/payout với ý định hủy.

## 5. Payout, idempotency và thông báo

- Trước HTTP: lưu PROCESSING, cố định UUID withdrawal làm referenceId và x-idempotency-key.
- Approve đồng thời/lặp không dispatch lần hai. PROCESSING không được manual confirm/reject.
- Chữ ký và provider ID/reference, số tiền, BIN/tài khoản phải khớp đúng một transaction.
- SUCCEEDED đã xác minh → PAID/trừ held một lần. FAILED/CANCELLED/REVERSED đã xác minh → FAILED/hoàn held một lần.
- Không rõ/timeout/mismatch → giữ tiền. API/job chỉ lookup; không retry create. Job mặc định 30s, lease từng payout 60s.
- Notification nạp/rút dùng shared sink cùng transaction ghi tiền, target PAYMENT_WALLET. PaymentSessionChangedEvent phát đồng bộ trong transaction cho cửa sổ phản hồi/case/settlement, target PAYMENT_SESSION + sessionId; FE route đúng detail theo role, Admin nhận khi đồng thuận/escalation.
- Readiness chỉ chứng minh đủ cấu hình, không xác nhận merchant/key/bank thực sự hợp lệ.

## 6. Schema và tài liệu liên quan

[V1.16](../../backend/app/src/main/resources/db/migration/payment/V1.16__create_payment_wallet_and_settlement.sql) tạo bảy bảng; [V1.17](../../backend/app/src/main/resources/db/migration/payment/V1.17__add_payment_provider_reconciliation.sql) bổ sung metadata/status/lease/index. Không sửa migration đã áp dụng. Ledger event key/reference unique và mutex DB bảo vệ thao tác tiền.

[V1.18](../../backend/app/src/main/resources/db/migration/payment/V1.18__store_payos_checkout_receiving_account.sql) bổ sung `payment_topup.provider_account_number`: tài khoản nhận từ checkout có chữ ký, có thể là tài khoản ảo riêng cho đơn. Response tạo checkout phải khớp order/amount/VND và URL HTTPS đúng host payOS; lỗi validation trả message riêng theo nguyên nhân. Webhook/lookup khớp snapshot qua `accountNumber` hoặc `virtualAccountNumber`, cùng các điều kiện receipt cũ. Snapshot là metadata nội bộ, không thêm field vào wallet/history projection; chữ ký vẫn bắt buộc. Hàng cũ NULL fallback `PAYMENT_BANK_ACCOUNT`, không học tài khoản mới từ webhook.

Xem [setup](payos-setup.md) cho cấu hình thu/chi riêng; [verification](verification-report.md) cho test thực chạy. Chưa kiểm chứng tiền thật.

## Tài khoản nhận tiền mặc định (V1.20)

- `GET /api/v1/payment/wallet`: Tutor có thêm `payoutAccount` (null nếu chưa lưu): id, bank_name, bank_bin, bank_account, bank_holder, updated_at. Chỉ trả tài khoản của actor. Các role khác không có field này.
- `PUT /api/v1/payment/payout-account`: Tutor, cookie + CSRF; body `{bank,bankBin,account,holder,expectedAccountId}`. BIN 6 số, account 5..34 số; lần đầu expectedAccountId=null, thay đổi phải gửi đúng ID hiện tại. Mỗi lần lưu sinh ID mới để chặn form cũ ghi đè/rút nhầm. Không tra cứu hoặc tự nhận đã xác minh chủ tài khoản ngân hàng.
- `POST /api/v1/payment/withdrawals/saved-account`: Tutor, cookie + CSRF; body `{amount,payoutAccountId}`. Backend xác minh owner/ID dưới lock rồi snapshot thông tin ngân hàng vào withdrawal; không tin bank/owner từ client. Stale/foreign ID bị từ chối trước khi giữ tiền.
- Endpoint withdrawals nhận thông tin ngân hàng trực tiếp vẫn giữ để tương thích client cũ. UI mới dùng tài khoản đã lưu; mỗi Tutor chỉ một yêu cầu PENDING/PROCESSING.
- V1.19 bỏ inferred consent 1h cho charge đang chờ; V1.20 tạo payment_payout_account, không tự suy ra tài khoản mặc định từ withdrawal cũ. Confirmation 24h; dispute 48h tính từ lúc mở; từ V1.21 hai xác nhận không tất toán sớm; hết 48h chuyển ADMIN_REVIEW, tiếp tục held. API confirm/dispute/report giữ path cũ.

## V1.21 và trải nghiệm 26/10

Tutor wallet có thêm `pendingIncome`: tổng dự kiến sau phí của charge chưa terminal. Trang chi tiết dùng reviewDeadline backend, không suy diễn endAt+24h. API Scheduling `POST /api/v1/scheduling/sessions/{id}/outcome` với `{outcome:"COMPLETED"}` dùng quyền tutor hiện có; public summary/timeline có thêm outcomeRecordedAt. Xem [cơ chế và migration](payment-usability.md).
