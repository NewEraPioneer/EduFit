"""Render deterministic Payment documentation diagrams as PNG and editable SVG.

Run from the repository root with Python + Pillow:
    python scripts/render-payment-diagrams.py
Fonts default to Windows Segoe UI; set PAYMENT_DIAGRAM_FONT_DIR to another folder
containing segoeui.ttf and segoeuib.ttf when rendering elsewhere.
No provider requests or application/database changes are made.
"""

from pathlib import Path
from html import escape
import math
import os

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/payment/images"
FONT_DIR = Path(os.environ.get("PAYMENT_DIAGRAM_FONT_DIR", "C:/Windows/Fonts"))
COLORS = {
    "blue": ("#EFF6FF", "#2563EB"),
    "green": ("#ECFDF5", "#047857"),
    "amber": ("#FFFBEB", "#B45309"),
    "red": ("#FEF2F2", "#B91C1C"),
    "slate": ("#F1F5F9", "#475569"),
    "purple": ("#F5F3FF", "#7C3AED"),
}


class Diagram:
    def __init__(self, slug, number, title, subtitle, height):
        self.slug, self.w, self.h = slug, 1680, height
        self.image = Image.new("RGB", (self.w, self.h), "#FFFFFF")
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}">',
                    '<rect width="100%" height="100%" fill="white"/>']
        self.fonts = {}
        self.boxes = []
        self.text(64, 28, f"EDUFIT / PAYMENT / {number:02d}", 20, "#2563EB", True)
        self.text(64, 66, title, 38, "#0F172A", True)
        self.paragraph(64, 120, subtitle, 1552, 23, "#475569")
        self.draw.line((64, 164, 1616, 164), fill="#E2E8F0", width=2)
        self.svg.append('<path d="M64 164H1616" stroke="#E2E8F0" stroke-width="2"/>')

    def font(self, size, bold=False):
        key = (size, bold)
        if key not in self.fonts:
            self.fonts[key] = ImageFont.truetype(str(FONT_DIR / ("segoeuib.ttf" if bold else "segoeui.ttf")), size)
        return self.fonts[key]

    def text(self, x, y, value, size=24, color="#0F172A", bold=False):
        font = self.font(size, bold)
        # Pillow's lt anchor and SVG text-before-edge use the same top coordinate.
        self.draw.text((x, y), value, font=font, fill=color, anchor="lt")
        self.svg.append(f'<text x="{x}" y="{y}" font-family="Segoe UI, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}" dominant-baseline="text-before-edge">{escape(value)}</text>')
        bounds = self.draw.textbbox((x, y), value, font=font, anchor="lt")
        assert 0 <= bounds[0] and bounds[2] <= self.w and bounds[3] <= self.h, (self.slug, value, bounds)

    def wrap(self, value, width, size=24, bold=False):
        lines = []
        for para in value.split("\n"):
            line = ""
            for word in para.split():
                candidate = (line + " " + word).strip()
                if self.draw.textlength(candidate, font=self.font(size, bold)) > width:
                    assert line, (self.slug, "word exceeds width", word)
                    lines.append(line)
                    line = word
                else:
                    line = candidate
            lines.append(line)
        return lines

    def paragraph(self, x, y, value, width, size=24, color="#334155", bold=False, leading=1.4):
        for line in self.wrap(value, width, size, bold):
            self.text(x, y, line, size, color, bold)
            y += math.ceil(size * leading)
        return y

    def box(self, x, y, w, h, title, body, tone="blue", size=25):
        fill, border = COLORS[tone]
        self.draw.rounded_rectangle((x, y, x+w, y+h), radius=18, fill=fill, outline=border, width=2)
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="{fill}" stroke="{border}" stroke-width="2"/>')
        cursor = self.paragraph(x+24, y+20, title, w-48, 27, border, True, 1.25)
        cursor = self.paragraph(x+24, cursor+14, body, w-48, size)
        assert cursor <= y+h-10, (self.slug, "text overflow", title, cursor, y+h)
        for other in self.boxes:
            ox, oy, ow, oh = other
            assert x+w <= ox or ox+ow <= x or y+h <= oy or oy+oh <= y, (self.slug, "overlapping boxes", title)
        self.boxes.append((x,y,w,h))

    def arrow(self, points, color="#64748B", label=None, label_xy=None):
        self.draw.line(points, fill=color, width=4, joint="curve")
        self.svg.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in points)+f'" fill="none" stroke="{color}" stroke-width="4" stroke-linejoin="round"/>')
        x,y = points[-1]; px,py = points[-2]
        angle = math.atan2(y-py,x-px)
        tri = [(x,y)] + [(x-16*math.cos(angle+delta), y-16*math.sin(angle+delta)) for delta in (-0.5,0.5)]
        self.draw.polygon(tri, fill=color)
        self.svg.append('<polygon points="'+' '.join(f'{a:.1f},{b:.1f}' for a,b in tri)+f'" fill="{color}"/>')
        if label:
            assert label_xy
            self.text(*label_xy,label,21,color,True)

    def footer(self, value):
        self.paragraph(64,self.h-76,value,1552,21,"#475569")

    def save(self):
        OUT.mkdir(parents=True, exist_ok=True)
        self.svg.append('</svg>')
        (OUT/f'{self.slug}.svg').write_text('\n'.join(self.svg)+'\n',encoding='utf-8')
        self.image.save(OUT/f'{self.slug}.png',optimize=True)
        print(f'{self.slug}: {self.w}x{self.h}, {len(self.boxes)} boxes, text bounds/overlap PASS')


def overview():
    d=Diagram('payment-overview',1,'Ba vòng đời của một khoản tiền','Tiền ngân hàng và số dư nội bộ có vai trò khác nhau; mỗi bước đều cần bằng chứng riêng.',1150)
    rows=[
        (220,'1. NẠP TIỀN',[
            ('Ngân hàng người nạp','Quét QR theo yêu cầu nạp.','blue'),
            ('Ngân hàng EduFit','Nhận tiền thật từ người nạp.','green'),
            ('Backend xác minh','Receipt có chữ ký và đúng topup.','blue'),
            ('Ví của người nạp','Tăng available đúng một lần.','green')]),
        (510,'2. THANH TOÁN BUỔI HỌC',[
            ('Payer cấp tiền','Available giảm; held tăng.','blue'),
            ('Buổi học kết thúc','COMPLETED; mở đủ 24h phản hồi.','slate'),
            ('Xử lý học phí','Đủ hạn hoặc Admin phân xử.','purple'),
            ('Chia trong ledger','Tutor nhận thu nhập; fee 10% phần trả.','green')]),
        (800,'3. RÚT THU NHẬP',[
            ('Ví gia sư','Yêu cầu rút; giữ riêng tiền W.','blue'),
            ('Admin kiểm tra','Chọn manual hoặc kênh chi payOS.','slate'),
            ('Xác minh kết quả','Chưa rõ thì giữ held, không chi lại.','amber'),
            ('Ngân hàng gia sư','Chỉ PAID khi kết quả đã xác nhận.','green')])]
    for y,label,nodes in rows:
        d.text(64,y-35,label,22,'#475569',True)
        for i,(title,body,tone) in enumerate(nodes):
            x=64+i*400
            d.box(x,y,352,180,title,body,tone)
            if i<3: d.arrow([(x+355,y+90),(x+396,y+90)])
    d.footer('Tất toán vào ví tutor không tự chuyển ngân hàng. Kênh thu và kênh chi phải có cấu hình/nguồn tiền phù hợp.')
    d.save()


def topup():
    d=Diagram('payment-topup-flow',2,'Nhập số tiền → QR → tăng ví','QR mở ngay trong popup EduFit; callback trình duyệt không phải bằng chứng tiền đã vào ví.',1370)
    steps=[
        ('1. Người dùng nhập 100.000đ','Student/Parent bấm nạp trong ví. Backend kiểm quyền và giới hạn tiền.'),
        ('2. Lưu yêu cầu nạp PENDING','Backend gắn order riêng, gọi payOS tạo checkout ngoài transaction DB.'),
        ('3. Hiển thị QR trong popup','SDK payOS nhúng checkout; đúng số tiền và mã yêu cầu, không rời trang.'),
        ('4. Người dùng quét QR bằng app ngân hàng','Tiền thật chuyển tới tài khoản nhận liên kết kênh thu EduFit.'),
        ('5. Backend xác minh giao dịch','Webhook đã ký hoặc signed lookup phải khớp yêu cầu đã lưu.'),
        ('6. Ghi ví, ledger, audit và thông báo','Available tăng 100.000đ một lần; UI tải lại để hiện số dư mới.')]
    for i,(title,body) in enumerate(steps):
        y=200+i*175
        d.box(64,y,1000,138,title,body,'green' if i==5 else 'blue')
        if i<5: d.arrow([(564,y+141),(564,y+171)])
    d.box(1120,200,496,230,'Chưa cộng tiền','Tạo QR, bấm quay về hoặc gửi ảnh biên lai đều không phải xác nhận nhận tiền.','amber')
    d.box(1120,505,496,265,'Webhook chậm hoặc bị mất','Owner/Admin có thể refresh. Job query provider; lease topup 30s để tránh gọi liên tục.','purple')
    d.box(1120,845,496,275,'Sai receipt hoặc kết quả chưa rõ','Không credit. Sai số tiền/tài khoản hoặc chuyển nhiều lần cần đối soát; không cộng tay.','red')
    d.footer('FE refresh 15s khi tab đang hiển thị và khi focus. Chưa kiểm chứng giao dịch merchant live bằng khóa thật.')
    d.save()


def session():
    d=Diagram('payment-session-flow',3,'Giữ học phí → phản hồi 24h → tất toán','Một buổi có một charge và một payer. Giá và commission được snapshot khi cấp tiền.',1260)
    nodes=[
        (200,'1. Cấp tiền cho buổi học','Payer đủ tiền: available -A, held +A. Giá phải khớp hồ sơ hiện hành.','blue'),
        (400,'2. Kiểm tra buổi đã hoàn thành','Nguồn Scheduling phải COMPLETED và thời gian server đã tới endAt.','blue'),
        (600,'3. Giữ đủ cửa sổ phản hồi 24h','Xác nhận chỉ ghi việc học. Đủ reviewStartedAt + 24h, COMPLETED và không có vấn đề mới tự thu.','purple'),
        (800,'4. Tất toán trong một transaction','Giải phóng held; trả phần dư payer; cộng tutor và ghi hoa hồng.','green')]
    for i,(y,title,body,tone) in enumerate(nodes):
        d.box(64,y,1000,150,title,body,tone)
        if i<3:d.arrow([(564,y+153),(564,y+196)])
    d.box(1120,200,496,240,'Ai được cấp tiền?','Student của buổi hoặc Parent có confirmed link. Available phải đủ, không tự trả cho mình.','slate')
    d.box(1120,500,496,250,'Mốc 24 giờ','Mở/lưu/thông báo cùng transaction. Ghi nhận hoàn thành muộn vẫn đủ 24h; không backdate.','amber')
    d.box(1120,810,496,250,'Nếu mở tranh chấp','Chặn tất toán thường; giữ tiền và chuyển luồng báo cáo 48h ở sơ đồ 04.','red')
    d.box(64,1000,1000,130,'Ví dụ A = S = 200.000đ','Tutor +180.000đ; hoa hồng +20.000đ; payer held -200.000đ.','green')
    d.footer('Chưa có gate buộc nạp/cấp tiền trước khi bắt đầu học. Hủy/vắng mặt chưa có case: hoàn khoản giữ; đã có case chờ Admin.')
    d.save()


def dispute():
    d=Diagram('payment-dispute-flow',4,'Tranh chấp 48 giờ và quyết định Admin','48h tính từ lúc mở tranh chấp; đây là hạn báo cáo của hai phía, không phải hạn xử lý của Admin.',1450)
    d.box(490,200,700,150,'1. Một phía mở tranh chấp','Sau endAt, trước reviewDeadline nếu đã mở → DISPUTED; giữ toàn bộ A.','red')
    d.arrow([(650,353),(650,380),(404,380),(404,416)])
    d.arrow([(1030,353),(1030,380),(1276,380),(1276,416)])
    d.box(64,420,680,155,'2a. Báo cáo của payer','Gửi riêng nội dung và mức tiền đề nghị S, trong khoảng 0..A.','blue')
    d.box(936,420,680,155,'2b. Báo cáo phía tutor','Gửi riêng nội dung và mức tiền đề nghị S, trước openedAt + 48h.','purple')
    d.arrow([(404,578),(404,610),(840,610),(840,646)])
    d.arrow([(1276,578),(1276,610),(840,610),(840,646)])
    d.box(490,650,700,150,'3. Đã có hai báo cáo cùng số tiền?','Nội dung không cần giống nhau. Report chỉ nhận khi now < deadline.','slate')
    d.arrow([(490,725),(404,725),(404,856)],label='CÓ, TRONG HẠN',label_xy=(90,785),color='#047857')
    d.arrow([(1190,725),(1276,725),(1276,856)],label='ĐỦ 48H CHƯA KHỚP',label_xy=(1300,785),color='#B45309')
    d.box(64,860,680,160,'4a. AWAITING_ADMIN','Giữ A chờ Admin; không hết hạn chỉ vì Admin xử lý lâu.','green')
    d.box(936,860,680,160,'4b. ADMIN_REVIEW','Chuyển Admin phân xử; tiếp tục giữ toàn bộ A, không tự hoàn hoặc chuyển tiền.','amber')
    d.arrow([(404,1023),(404,1076)])
    d.arrow([(1276,1023),(1276,1076)])
    d.box(64,1080,680,230,'5a. Admin duyệt mức thống nhất','Duyệt: trả đúng S, fee 10% S, hoàn A-S.\nTừ chối hoặc S=0: hoàn toàn bộ A.','green')
    d.box(936,1080,680,230,'5b. Admin phân xử có lý do','Đủ/một phần: tutor nhận S - phí 10% S; hoàn A-S.\nS=0: hoàn toàn bộ, không phí.','slate')
    d.footer('Admin phân xử sau endAt dựa bằng chứng; auto-settlement vẫn cần COMPLETED. Hoàn là hoàn ví nội bộ.')
    d.save()


def payout():
    d=Diagram('payment-payout-flow',6,'Rút tiền: giữ trước, xác minh rồi ghi kết quả','Lệnh chi cần Admin duyệt. Timeout không có nghĩa ngân hàng chưa chuyển tiền.',1410)
    steps=[
        ('1. Tutor yêu cầu rút W','Chọn số tiền và tài khoản đã lưu; xác nhận → PENDING, available -W, held +W.'),
        ('2. Admin duyệt chi payOS','Cần kênh chi bật, khóa riêng và thông tin ngân hàng có BIN.'),
        ('3. Lưu PROCESSING trước HTTP','Withdrawal UUID là referenceId và idempotency key cố định; tạo lệnh một lần.'),
        ('4. Xác minh kết quả hoặc query lại','Chữ ký, ID/reference, amount, BIN, tài khoản và đúng một transaction phải khớp.')]
    for i,(title,body) in enumerate(steps):
        y=200+i*180
        d.box(64,y,1000,140,title,body,'blue' if i<2 else 'purple')
        if i<3:d.arrow([(564,y+143),(564,y+176)])
    d.box(1120,200,496,280,'Nhánh manual ở PENDING','Admin chuyển ngoài app rồi xác minh, nhập reference thật để ghi PAID; hoặc từ chối để trả held.','slate')
    d.box(1120,560,496,320,'Đã PROCESSING','Chặn manual paid/reject và không gửi create lần hai. API/job chỉ lookup; lease 60s. Crash trước dispatch có thể cần hỗ trợ provider.','amber')
    for x,tone,title,body in [
        (64,'green','SUCCEEDED đã khớp','PAID; held -W. Không cộng lại ví và không trừ lần hai.'),
        (600,'red','Thất bại cuối đã khớp','FAILED/CANCELLED/REVERSED: ghi FAILED; available +W, held -W.'),
        (1136,'amber','Kết quả chưa chắc chắn','Giữ PROCESSING và held; không hoàn tiền, không chi lại.')]:
        d.arrow([(564,883),(564,920),(x+240,920),(x+240,976)])
        d.box(x,980,480,260,title,body,tone)
    d.footer('Kênh thu không tự cấp nguồn tiền cho kênh chi. Chỉ dùng kết quả cuối đã xác minh để kết thúc khoản rút.')
    d.save()


def atomic():
    d=Diagram('payment-atomic-ledger',7,'Cơ chế chống sai số dư và ghi tiền trùng','Một transaction gắn trạng thái nghiệp vụ, số dư, ledger, audit và thông báo thành một lần thay đổi.',1260)
    steps=[
        ('1. BEGIN và khóa payment_mutex','SELECT FOR UPDATE serialize thao tác tiền giữa nhiều request/instance.'),
        ('2. Kiểm điều kiện và số dư','Đúng actor/state/reference; available và held không được âm.'),
        ('3. Ghi các thay đổi liên quan','State + wallet + entry(event_key unique) + audit + NotificationSink.'),
        ('4. COMMIT khi tất cả thành công','Chỉ sau commit, các request khác mới thấy thay đổi tiền đã hoàn tất.')]
    for i,(title,body) in enumerate(steps):
        y=200+i*195
        d.box(64,y,1000,155,title,body,'green' if i==3 else 'blue')
        if i<3:d.arrow([(564,y+158),(564,y+191)])
    d.box(1120,200,496,260,'Không giữ lock khi gọi payOS','HTTP tạo checkout/chi/lookup nằm ngoài transaction tiền; lỗi mạng không giữ khóa DB.','slate')
    d.box(1120,520,496,270,'Webhook/refresh bị lặp','Trạng thái và reference kiểm lại; unique event_key ngăn một sự kiện ghi sổ hai lần.','purple')
    d.box(64,1040,1552,120,'Bất kỳ bước ghi nào lỗi → ROLLBACK','Không để ví đã tăng nhưng thiếu entry/notification, hoặc charge tất toán mà tutor chưa nhận số dư.','red')
    d.footer('Mutex toàn cục ưu tiên đúng đắn cho quy mô hiện tại; throughput production chưa được đo và chưa có SLA p95.')
    d.save()


def examples():
    d=Diagram('payment-settlement-examples',5,'Ví dụ: giữ 200.000đ thì tiền đi đâu?','Ví payer ban đầu 1.000.000đ; tutor ban đầu 0đ. S là số tiền thực tất toán, A = 200.000đ.',1120)
    d.box(64,200,1552,150,'Sau khi cấp tiền cho buổi học','Payer available = 800.000đ; held = 200.000đ. Tổng quyền lợi vẫn 1.000.000đ, nhưng chỉ 800.000đ được dùng tiếp.','blue')
    xs=[64,600,1136]
    cases=[('Trả đủ S = 200.000đ','Fee = 20.000đ\nTutor nhận = 180.000đ\nHoàn payer = 0đ\nPayer cuối = 800.000đ','green'),
           ('Trả một phần S = 150.000đ','Fee = 15.000đ\nTutor nhận = 135.000đ\nHoàn payer = 50.000đ\nPayer cuối = 850.000đ','purple'),
           ('Không thu phí S = 0đ','Fee = 0đ\nTutor nhận = 0đ\nHoàn payer = 200.000đ\nPayer cuối = 1.000.000đ','amber')]
    for x,(title,body,tone) in zip(xs,cases):
        d.arrow([(840,353),(840,400),(x+240,400),(x+240,466)])
        d.box(x,470,480,345,title,body,tone)
    d.box(64,875,1552,140,'Tất cả kết quả cuối đều giải phóng held của charge về 0đ','Commission = floor(S × 1000 / 10000). Tiền hoàn quay lại đúng payer; chưa có bước chuyển ngân hàng trong sơ đồ này.','slate')
    d.footer('Commission ghi entry nền tảng (user_id null), chưa có ví Admin hoặc chức năng rút hoa hồng riêng.')
    d.save()


if __name__ == '__main__':
    for render in [overview,topup,session,dispute,examples,payout,atomic]:
        render()
