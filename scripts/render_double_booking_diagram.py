import os
import subprocess
import shutil

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS_DIR = os.path.join(BASE_DIR, "docs")
ARTIFACT_DIR = r"C:\Users\thanh\.gemini\antigravity\brain\5c45e275-8b7e-43cc-bf72-7a5fc1572da6"
CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
if not os.path.exists(CHROME_PATH):
    CHROME_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

html_content = """<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<style>
  @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&family=JetBrains+Mono:wght@500;600;700&display=swap');
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #FFFFFF;
    font-family: 'Plus Jakarta Sans', sans-serif;
    color: #0f172a;
    padding: 30px;
    width: 1600px;
    height: 950px;
  }
  .card {
    border: 2px solid #e2e8f0;
    border-radius: 20px;
    padding: 32px;
    background: #FFFFFF;
    box-shadow: 0 10px 25px -5px rgba(0,0,0,0.05);
  }
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 2px solid #f1f5f9;
    padding-bottom: 20px;
    margin-bottom: 28px;
  }
  .title h2 { font-size: 26px; font-weight: 800; color: #0f172a; display: flex; align-items: center; gap: 10px; }
  .badge { background: #fee2e2; color: #dc2626; font-size: 13px; font-weight: 700; padding: 4px 12px; border-radius: 999px; }
  .desc { font-size: 14px; color: #64748b; margin-top: 4px; }
  
  .grid-content {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 30px;
  }
  
  /* Timeline Box */
  .timeline-box {
    background: #f8fafc;
    border: 1.5px solid #cbd5e1;
    border-radius: 14px;
    padding: 24px;
  }
  .timeline-title { font-size: 16px; font-weight: 700; color: #334155; margin-bottom: 20px; }
  
  .timeline-visual {
    position: relative;
    height: 180px;
    border-left: 2px solid #94a3b8;
    margin-left: 50px;
    margin-top: 10px;
  }
  .axis-label { position: absolute; left: -45px; font-size: 12px; font-weight: 600; color: #64748b; font-family: 'JetBrains Mono', monospace; }
  .axis-grid { position: absolute; left: 0; right: 0; border-top: 1px dashed #cbd5e1; }
  
  .session-bar {
    position: absolute;
    left: 40px;
    width: 280px;
    border-radius: 8px;
    padding: 10px 14px;
    color: white;
    font-size: 13px;
    font-weight: 700;
    box-shadow: 0 4px 10px rgba(0,0,0,0.1);
  }
  .sess-a {
    top: 20px;
    height: 80px;
    background: linear-gradient(135deg, #059669, #10b981);
    border: 1px solid #047857;
  }
  .sess-b {
    top: 60px;
    left: 180px;
    height: 80px;
    background: linear-gradient(135deg, #dc2626, #ef4444);
    border: 1px solid #b91c1c;
  }
  .overlap-zone {
    position: absolute;
    top: 60px;
    left: 180px;
    width: 140px;
    height: 40px;
    background: repeating-linear-gradient(45deg, rgba(239, 68, 68, 0.4), rgba(239, 68, 68, 0.4) 6px, rgba(220, 38, 38, 0.6) 6px, rgba(220, 38, 38, 0.6) 12px);
    border: 2px dashed #991b1b;
    border-radius: 6px;
    z-index: 20;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 10px;
    font-weight: 800;
  }
  
  .sql-box {
    background: #0f172a;
    border-radius: 14px;
    padding: 22px;
    color: #f8fafc;
    font-family: 'JetBrains Mono', monospace;
    font-size: 13px;
    line-height: 1.6;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .sql-box pre { margin: 0; white-space: pre-wrap; }
  .kw { color: #38bdf8; font-weight: 700; }
  .fn { color: #fbbf24; }
  .str { color: #a3e635; }
  .cmt { color: #64748b; font-style: italic; }
  
  .footer-status {
    margin-top: 24px;
    padding: 16px 20px;
    background: #fff1f2;
    border: 1.5px solid #fecdd3;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13.5px;
  }
  .status-tag {
    font-family: 'JetBrains Mono', monospace;
    font-weight: 700;
    color: #be123c;
    background: #ffe4e6;
    padding: 4px 10px;
    border-radius: 6px;
  }
</style>
</head>
<body>
<div class="card">
  <div class="header">
    <div class="title">
      <h2>PostgreSQL GiST Exclusion Constraint <span class="badge">NFR-17 Double-Booking Defense</span></h2>
      <p class="desc">Cơ chế bảo vệ chống trùng lặp lịch dạy ở cấp Cơ sở dữ liệu vật lý (PostgreSQL 17 + btree_gist extension)</p>
    </div>
  </div>

  <div class="grid-content">
    <div class="timeline-box">
      <div class="timeline-title">Minh họa Xung đột Thời gian (Overlap Detection)</div>
      <div class="timeline-visual">
        <div class="axis-label" style="top: 15px;">14:00</div>
        <div class="axis-grid" style="top: 20px;"></div>

        <div class="axis-label" style="top: 55px;">15:00</div>
        <div class="axis-grid" style="top: 60px;"></div>

        <div class="axis-label" style="top: 95px;">16:00</div>
        <div class="axis-grid" style="top: 100px;"></div>

        <div class="axis-label" style="top: 135px;">17:00</div>
        <div class="axis-grid" style="top: 140px;"></div>

        <!-- Session A -->
        <div class="session-bar sess-a">
          Session A (14:00 – 16:00)<br>
          <span style="font-size: 11px; opacity: 0.9;">status: SCHEDULED ✓</span>
        </div>

        <!-- Session B -->
        <div class="session-bar sess-b">
          Session B (15:00 – 17:00)<br>
          <span style="font-size: 11px; opacity: 0.9;">status: SCHEDULED ✗</span>
        </div>

        <!-- Overlap marker -->
        <div class="overlap-zone">TRÙNG 15:00-16:00</div>
      </div>
      <p style="margin-top: 15px; font-size: 12.5px; color: #64748b; line-height: 1.4;">
        Toán tử <strong>&&</strong> (Overlaps) kiểm tra vùng giao cắt giữa 2 dải thời gian <code>tstzrange</code>. Nếu cùng <code>tutor_id</code> hoặc <code>student_id</code>, hệ thống chặn ngay lập tức.
      </p>
    </div>

    <div class="sql-box">
      <pre><span class="cmt">-- 1. Bật extension btree_gist</span>
<span class="kw">CREATE EXTENSION IF NOT EXISTS</span> btree_gist;

<span class="cmt">-- 2. Ràng buộc loại trừ chống trùng lịch Gia sư</span>
<span class="kw">ALTER TABLE</span> tutoring_session <span class="kw">ADD CONSTRAINT</span> no_tutor_overlap
  <span class="kw">EXCLUDE USING</span> gist (
    tutor_id <span class="kw">WITH</span> =,
    <span class="fn">tstzrange</span>(start_at, end_at, <span class="str">'[)'</span>) <span class="kw">WITH</span> &&
  )
  <span class="kw">WHERE</span> (status = <span class="str">'SCHEDULED'</span>);

<span class="cmt">-- 3. Ràng buộc thời lượng 30 - 240 phút (BR-41)</span>
<span class="kw">ALTER TABLE</span> tutoring_session <span class="kw">ADD CONSTRAINT</span> chk_session_time
  <span class="kw">CHECK</span> (end_at > start_at <span class="kw">AND</span> end_at - start_at 
         <span class="kw">BETWEEN INTERVAL</span> <span class="str">'30 minutes'</span> <span class="kw">AND INTERVAL</span> <span class="str">'240 minutes'</span>);</pre>
    </div>
  </div>

  <div class="footer-status">
    <div><strong>Kết quả kích hoạt ràng buộc:</strong> PostgreSQL ném mã lỗi <code>SQLSTATE 23P01 (exclusion_violation)</code></div>
    <div class="status-tag">Backend Rollback ➔ HTTP 409 CONFLICT</div>
  </div>
</div>
</body>
</html>
"""

html_path = os.path.join(BASE_DIR, "db_render.html")
with open(html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

out_png = os.path.join(DOCS_DIR, "edufit_double_booking_protection_4k.png")
cmd = [
    CHROME_PATH,
    "--headless=new",
    "--disable-gpu",
    f"--screenshot={out_png}",
    "--window-size=1600,950",
    "--force-device-scale-factor=2",
    html_path
]
subprocess.run(cmd, capture_output=True)
if os.path.exists(html_path):
    os.remove(html_path)

if os.path.exists(out_png):
    print(f"Rendered: {out_png}")
    shutil.copy(out_png, os.path.join(ARTIFACT_DIR, "edufit_double_booking_protection_4k.png"))
