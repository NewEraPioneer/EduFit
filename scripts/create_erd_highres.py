import os
import subprocess
import shutil
from PIL import Image, ImageEnhance, ImageFilter

# 1. Paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DOCS_DIR = os.path.join(BASE_DIR, "docs")
ARTIFACT_DIR = r"C:\Users\thanh\.gemini\antigravity\brain\5c45e275-8b7e-43cc-bf72-7a5fc1572da6"
CHROME_PATH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
if not os.path.exists(CHROME_PATH):
    CHROME_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

os.makedirs(DOCS_DIR, exist_ok=True)
os.makedirs(ARTIFACT_DIR, exist_ok=True)

# ==============================================================================
# BƯỚC 1: LÀM SẠCH VÀ LÀM NÉT ẢNH GỐC TỪ SRS (TẨY NỀN XÁM THÀNH NỀN TRẮNG 100%)
# ==============================================================================
def enhance_original_image(input_path, output_path):
    if not os.path.exists(input_path):
        print(f"Skipping {input_path}, not found.")
        return
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()
    
    new_data = []
    # Điểm ảnh gần trắng (>240 trên cả 3 kênh R,G,B) chuyển hẳn về trắng tinh (255, 255, 255, 255)
    # Các điểm ảnh chữ và đường viền (tối màu) giữ nguyên hoặc làm đậm nét
    for item in data:
        r, g, b, a = item
        if r > 238 and g > 238 and b > 238:
            new_data.append((255, 255, 255, 255))
        elif r < 120 and g < 120 and b < 120:
            # Tăng độ đậm nét cho chữ và đường kẻ
            new_data.append((max(0, r - 25), max(0, g - 25), max(0, b - 25), 255))
        else:
            new_data.append((r, g, b, a))
            
    img.putdata(new_data)
    
    # Tăng độ tương phản (Contrast) và độ sắc nét (Sharpness)
    rgb_img = img.convert("RGB")
    enhancer_con = ImageEnhance.Contrast(rgb_img)
    enhanced = enhancer_con.enhance(1.25)
    enhancer_sharp = ImageEnhance.Sharpness(enhanced)
    final_img = enhancer_sharp.enhance(1.4)
    
    final_img.save(output_path, "PNG", dpi=(300, 300))
    print(f"Generated clean white background image: {output_path}")

enhance_original_image(
    os.path.join(DOCS_DIR, "srs_erd_database_schema.png"),
    os.path.join(DOCS_DIR, "srs_erd_database_schema_white_sharp.png")
)
enhance_original_image(
    os.path.join(DOCS_DIR, "srs_erd_conceptual.png"),
    os.path.join(DOCS_DIR, "srs_erd_conceptual_white_sharp.png")
)

# ==============================================================================
# BƯỚC 2: TẠO SƠ ĐỒ LƯỢC ĐỒ CSDL QUAN HỆ 14 BẢNG (PHYSICAL SCHEMA ERD) CHUẨN 4K
# ==============================================================================
html_relational = """<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<title>EduFit - SRS Database Schema ERD</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap');
  
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background-color: #FFFFFF;
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
    color: #1e293b;
    padding: 40px;
    width: 2500px;
    height: 1650px;
    position: relative;
    overflow: hidden;
  }
  
  /* Header Title */
  .diagram-header {
    margin-bottom: 25px;
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 16px;
  }
  .title-group h1 {
    font-size: 32px;
    font-weight: 800;
    color: #0f172a;
    letter-spacing: -0.5px;
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .title-group h1 span.badge {
    font-size: 14px;
    font-weight: 700;
    background: #0284c7;
    color: white;
    padding: 4px 12px;
    border-radius: 999px;
  }
  .title-group p {
    font-size: 15px;
    color: #64748b;
    margin-top: 4px;
    font-weight: 500;
  }
  .legend {
    display: flex;
    gap: 20px;
    align-items: center;
    font-size: 13px;
    font-weight: 600;
  }
  .legend-item { display: flex; align-items: center; gap: 8px; }
  .legend-pk { background: #fef3c7; color: #b45309; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', monospace; font-size: 11px; font-weight: 700; border: 1px solid #fde68a; }
  .legend-fk { background: #e0f2fe; color: #0369a1; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', monospace; font-size: 11px; font-weight: 700; border: 1px solid #bae6fd; }
  .legend-dot { width: 12px; height: 12px; border-radius: 3px; }

  /* Canvas Container */
  .canvas {
    position: relative;
    width: 100%;
    height: 1500px;
  }

  /* Table Cards */
  .table-card {
    position: absolute;
    width: 290px;
    background: #FFFFFF;
    border-radius: 12px;
    border: 1.5px solid #cbd5e1;
    box-shadow: 0 4px 12px -2px rgba(15, 23, 42, 0.08), 0 2px 6px -1px rgba(15, 23, 42, 0.04);
    z-index: 10;
  }
  
  .table-header {
    padding: 10px 14px;
    border-top-left-radius: 10px;
    border-top-right-radius: 10px;
    color: #FFFFFF;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .table-header .table-name {
    font-size: 14.5px;
    font-weight: 700;
    letter-spacing: -0.2px;
  }
  .table-header .table-count {
    font-size: 11px;
    opacity: 0.85;
    font-family: 'JetBrains Mono', monospace;
  }

  /* Header Color Variants */
  .hdr-blue { background: linear-gradient(135deg, #1e40af, #2563eb); }
  .hdr-teal { background: linear-gradient(135deg, #0f766e, #0d9488); }
  .hdr-emerald { background: linear-gradient(135deg, #047857, #10b981); }
  .hdr-purple { background: linear-gradient(135deg, #6b21a8, #7e22ce); }
  .hdr-indigo { background: linear-gradient(135deg, #4338ca, #6366f1); }
  .hdr-amber { background: linear-gradient(135deg, #b45309, #d97706); }
  .hdr-rose { background: linear-gradient(135deg, #be123c, #e11d48); }

  .columns-list {
    padding: 8px 0;
  }
  .col-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 4px 12px;
    font-size: 12px;
    border-bottom: 1px solid #f8fafc;
  }
  .col-row:last-child { border-bottom: none; }
  .col-row:hover { background-color: #f1f5f9; }
  
  .col-left {
    display: flex;
    align-items: center;
    gap: 6px;
    font-family: 'JetBrains Mono', monospace;
    font-weight: 600;
    color: #1e293b;
  }
  .col-right {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: #64748b;
  }

  .pk-badge {
    background: #fef3c7;
    color: #b45309;
    font-size: 9px;
    font-weight: 700;
    padding: 1px 4px;
    border-radius: 3px;
    border: 1px solid #fde68a;
  }
  .fk-badge {
    background: #e0f2fe;
    color: #0369a1;
    font-size: 9px;
    font-weight: 700;
    padding: 1px 4px;
    border-radius: 3px;
    border: 1px solid #bae6fd;
  }

  /* SVG Relationships Canvas */
  svg.connectors {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
    pointer-events: none;
  }
  .rel-line {
    fill: none;
    stroke: #94a3b8;
    stroke-width: 1.8;
  }
  .rel-text {
    font-family: 'Plus Jakarta Sans', sans-serif;
    font-size: 11px;
    font-weight: 700;
    fill: #475569;
    text-shadow: 1px 1px 0 #fff, -1px 1px 0 #fff, 1px -1px 0 #fff, -1px -1px 0 #fff;
  }
</style>
</head>
<body>

<div class="diagram-header">
  <div class="title-group">
    <h1>EduFit — Initial Database Schema ERD <span class="badge">14 Core Tables (SRS V1.0)</span></h1>
    <p>Lược đồ CSDL quan hệ khởi tạo trong tài liệu Yêu cầu phần mềm (SRS EduFit) — Thiết kế chuẩn hóa cho Slide & Báo cáo</p>
  </div>
  <div class="legend">
    <div class="legend-item"><span class="legend-pk">PK</span> Khóa chính</div>
    <div class="legend-item"><span class="legend-fk">FK</span> Khóa ngoại</div>
    <div class="legend-item"><span class="legend-dot" style="background:#2563eb;"></span> Tài khoản / IAM</div>
    <div class="legend-item"><span class="legend-dot" style="background:#0d9488;"></span> Hồ sơ & Liên kết</div>
    <div class="legend-item"><span class="legend-dot" style="background:#7e22ce;"></span> Gia sư & Bằng cấp</div>
    <div class="legend-item"><span class="legend-dot" style="background:#d97706;"></span> Lớp học & Buổi học</div>
    <div class="legend-item"><span class="legend-dot" style="background:#e11d48;"></span> Thanh toán & Đánh giá</div>
  </div>
</div>

<div class="canvas">
  <!-- SVG Connector Lines -->
  <svg class="connectors">
    <!-- User -> StudentProfile -->
    <path d="M 330 200 L 400 200 L 400 210 L 470 210" class="rel-line" />
    <text x="375" y="195" class="rel-text">1:1</text>

    <!-- User -> TutorProfile -->
    <path d="M 190 320 L 190 480" class="rel-line" />
    <text x="195" y="400" class="rel-text">1:1</text>

    <!-- User -> ParentStudentLink -->
    <path d="M 330 240 L 420 240 L 420 40 L 890 40 L 890 120" class="rel-line" />
    <text x="650" y="32" class="rel-text">parent 1:N</text>

    <!-- StudentProfile -> ParentStudentLink -->
    <path d="M 770 200 L 820 200 L 820 180 L 890 180" class="rel-line" />
    <text x="830" y="170" class="rel-text">student 1:N</text>

    <!-- TutorProfile -> TutorSubject -->
    <path d="M 330 580 L 470 580" class="rel-line" />
    <text x="390" y="572" class="rel-text">1:N</text>

    <!-- Subject -> TutorSubject -->
    <path d="M 615 760 L 615 670" class="rel-line" />
    <text x="620" y="720" class="rel-text">1:N</text>

    <!-- StudentProfile -> LearningGoal -->
    <path d="M 620 330 L 620 380 L 890 380 L 890 420" class="rel-line" />
    <text x="750" y="372" class="rel-text">1:N</text>

    <!-- Subject -> LearningGoal -->
    <path d="M 770 820 L 1030 820 L 1030 590" class="rel-line" />
    <text x="900" y="812" class="rel-text">1:N</text>

    <!-- TutorProfile -> CredentialDocument -->
    <path d="M 190 750 L 190 920" class="rel-line" />
    <text x="195" y="850" class="rel-text">1:N</text>

    <!-- TutoringClass -> LearningGoal, StudentProfile, TutorProfile -->
    <path d="M 1190 490 L 1310 490" class="rel-line" />
    <text x="1230" y="482" class="rel-text">based on</text>

    <!-- TutoringClass -> Session -->
    <path d="M 1610 500 L 1730 500" class="rel-line" />
    <text x="1650" y="492" class="rel-text">1:N</text>

    <!-- Session -> SessionNote -->
    <path d="M 1880 680 L 1880 780" class="rel-line" />
    <text x="1885" y="735" class="rel-text">1:N</text>

    <!-- Session -> Payment -->
    <path d="M 2030 520 L 2150 520" class="rel-line" />
    <text x="2070" y="512" class="rel-text">1:0..1</text>

    <!-- Review -> TutorProfile & StudentProfile -->
    <path d="M 1730 180 L 1380 180 L 1380 80 L 620 80 L 620 120" class="rel-line" />
    <text x="1150" y="72" class="rel-text">reviewed by Student / Parent</text>

    <!-- Notification -> User -->
    <path d="M 190 120 L 190 70 L 140 70 L 140 120 L 140 180" class="rel-line" />
  </svg>

  <!-- 1. USER TABLE -->
  <div class="table-card" style="top: 120px; left: 40px;">
    <div class="table-header hdr-blue">
      <span class="table-name">User</span>
      <span class="table-count">7 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>user_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">email</span><span class="col-right">VARCHAR(255)</span></div>
      <div class="col-row"><span class="col-left">password_hash</span><span class="col-right">VARCHAR(255)</span></div>
      <div class="col-row"><span class="col-left">full_name</span><span class="col-right">VARCHAR(150)</span></div>
      <div class="col-row"><span class="col-left">role</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">created_at</span><span class="col-right">TIMESTAMPTZ</span></div>
    </div>
  </div>

  <!-- 2. STUDENT PROFILE -->
  <div class="table-card" style="top: 120px; left: 470px;">
    <div class="table-header hdr-teal">
      <span class="table-name">StudentProfile</span>
      <span class="table-count">7 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>student_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>user_id</span><span class="col-right">UUID (UK)</span></div>
      <div class="col-row"><span class="col-left">grade_level</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">current_level</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">area</span><span class="col-right">VARCHAR(100)</span></div>
      <div class="col-row"><span class="col-left">learning_format</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">budget</span><span class="col-right">BIGINT</span></div>
    </div>
  </div>

  <!-- 3. PARENT STUDENT LINK -->
  <div class="table-card" style="top: 120px; left: 890px;">
    <div class="table-header hdr-teal">
      <span class="table-name">ParentStudentLink</span>
      <span class="table-count">5 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>link_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>parent_user_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>student_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">confirmed_at</span><span class="col-right">TIMESTAMPTZ</span></div>
    </div>
  </div>

  <!-- 4. TUTOR PROFILE -->
  <div class="table-card" style="top: 480px; left: 40px;">
    <div class="table-header hdr-purple">
      <span class="table-name">TutorProfile</span>
      <span class="table-count">9 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>user_id</span><span class="col-right">UUID (UK)</span></div>
      <div class="col-row"><span class="col-left">bio</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">qualification</span><span class="col-right">VARCHAR(200)</span></div>
      <div class="col-row"><span class="col-left">experience</span><span class="col-right">INT</span></div>
      <div class="col-row"><span class="col-left">teaching_method</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">area</span><span class="col-right">VARCHAR(100)</span></div>
      <div class="col-row"><span class="col-left">price</span><span class="col-right">BIGINT</span></div>
      <div class="col-row"><span class="col-left">verification_status</span><span class="col-right">VARCHAR(20)</span></div>
    </div>
  </div>

  <!-- 5. SUBJECT -->
  <div class="table-card" style="top: 760px; left: 470px;">
    <div class="table-header hdr-indigo">
      <span class="table-name">Subject</span>
      <span class="table-count">4 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>subject_id</span><span class="col-right">INT</span></div>
      <div class="col-row"><span class="col-left">name</span><span class="col-right">VARCHAR(100)</span></div>
      <div class="col-row"><span class="col-left">description</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">is_active</span><span class="col-right">BOOLEAN</span></div>
    </div>
  </div>

  <!-- 6. TUTOR SUBJECT (N-N) -->
  <div class="table-card" style="top: 520px; left: 470px;">
    <div class="table-header hdr-indigo">
      <span class="table-name">TutorSubject</span>
      <span class="table-count">3 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>tutor_subject_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>subject_id</span><span class="col-right">INT</span></div>
    </div>
  </div>

  <!-- 7. LEARNING GOAL -->
  <div class="table-card" style="top: 420px; left: 890px;">
    <div class="table-header hdr-teal">
      <span class="table-name">LearningGoal</span>
      <span class="table-count">9 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>goal_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>student_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>subject_id</span><span class="col-right">INT</span></div>
      <div class="col-row"><span class="col-left">current_level</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">target_level</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">timeframe</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">learning_format</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">budget</span><span class="col-right">BIGINT</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
    </div>
  </div>

  <!-- 8. CREDENTIAL DOCUMENT -->
  <div class="table-card" style="top: 920px; left: 40px;">
    <div class="table-header hdr-purple">
      <span class="table-name">CredentialDocument</span>
      <span class="table-count">6 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>credential_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>reviewed_by</span><span class="col-right">UUID (Admin)</span></div>
      <div class="col-row"><span class="col-left">document_type</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">file_url</span><span class="col-right">VARCHAR(500)</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
    </div>
  </div>

  <!-- 9. TUTORING CLASS (ENGAGEMENT) -->
  <div class="table-card" style="top: 420px; left: 1310px;">
    <div class="table-header hdr-amber">
      <span class="table-name">TutoringClass</span>
      <span class="table-count">7 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>class_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>student_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>goal_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">start_date</span><span class="col-right">DATE</span></div>
      <div class="col-row"><span class="col-left">end_date</span><span class="col-right">DATE</span></div>
    </div>
  </div>

  <!-- 10. SESSION -->
  <div class="table-card" style="top: 420px; left: 1730px;">
    <div class="table-header hdr-amber">
      <span class="table-name">Session</span>
      <span class="table-count">8 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>session_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>class_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>proposed_by</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>confirmed_by</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">start_time</span><span class="col-right">TIMESTAMPTZ</span></div>
      <div class="col-row"><span class="col-left">end_time</span><span class="col-right">TIMESTAMPTZ</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">cancel_reason</span><span class="col-right">VARCHAR(255)</span></div>
    </div>
  </div>

  <!-- 11. SESSION NOTE -->
  <div class="table-card" style="top: 780px; left: 1730px;">
    <div class="table-header hdr-amber">
      <span class="table-name">SessionNote</span>
      <span class="table-count">6 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>note_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>session_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">content_covered</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">homework</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">completion_level</span><span class="col-right">VARCHAR(50)</span></div>
    </div>
  </div>

  <!-- 12. REVIEW -->
  <div class="table-card" style="top: 120px; left: 1730px;">
    <div class="table-header hdr-rose">
      <span class="table-name">Review</span>
      <span class="table-count">6 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>review_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>tutor_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>student_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>reviewer_user_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">rating</span><span class="col-right">INT (1..5)</span></div>
      <div class="col-row"><span class="col-left">comment</span><span class="col-right">TEXT</span></div>
    </div>
  </div>

  <!-- 13. PAYMENT -->
  <div class="table-card" style="top: 420px; left: 2150px;">
    <div class="table-header hdr-rose">
      <span class="table-name">Payment</span>
      <span class="table-count">6 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>payment_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>session_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>payer_user_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">amount</span><span class="col-right">BIGINT</span></div>
      <div class="col-row"><span class="col-left">status</span><span class="col-right">VARCHAR(20)</span></div>
      <div class="col-row"><span class="col-left">paid_at</span><span class="col-right">TIMESTAMPTZ</span></div>
    </div>
  </div>

  <!-- 14. NOTIFICATION -->
  <div class="table-card" style="top: 1040px; left: 470px;">
    <div class="table-header hdr-blue">
      <span class="table-name">Notification</span>
      <span class="table-count">6 cols</span>
    </div>
    <div class="columns-list">
      <div class="col-row"><span class="col-left"><span class="pk-badge">PK</span>notification_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left"><span class="fk-badge">FK</span>user_id</span><span class="col-right">UUID</span></div>
      <div class="col-row"><span class="col-left">title</span><span class="col-right">VARCHAR(200)</span></div>
      <div class="col-row"><span class="col-left">message</span><span class="col-right">TEXT</span></div>
      <div class="col-row"><span class="col-left">type</span><span class="col-right">VARCHAR(50)</span></div>
      <div class="col-row"><span class="col-left">is_read</span><span class="col-right">BOOLEAN</span></div>
    </div>
  </div>

</div>

</body>
</html>
"""

html_path = os.path.join(BASE_DIR, "erd_render.html")
with open(html_path, "w", encoding="utf-8") as f:
    f.write(html_relational)

# Render với Chrome Headless ở tỷ lệ siêu nét 2.5x (cho độ phân giải ~ 4K 3840x2400)
out_png_path = os.path.join(DOCS_DIR, "edufit_erd_database_schema_4k.png")
cmd = [
    CHROME_PATH,
    "--headless=new",
    "--disable-gpu",
    f"--screenshot={out_png_path}",
    "--window-size=2500,1650",
    "--force-device-scale-factor=2",
    html_path
]
print("Rendering Relational ERD via Chrome Headless...")
subprocess.run(cmd, capture_output=True)
if os.path.exists(html_path):
    os.remove(html_path)

if os.path.exists(out_png_path):
    print(f"Rendered successfully: {out_png_path} ({os.path.getsize(out_png_path)} bytes)")
    shutil.copy(out_png_path, os.path.join(ARTIFACT_DIR, "edufit_erd_database_schema_4k.png"))

# ==============================================================================
# BƯỚC 3: TẠO SƠ ĐỒ THỰC THỂ QUAN HỆ MỨC QUAN NIỆM (CONCEPTUAL ERD) CHUẨN 4K
# ==============================================================================
html_conceptual = """<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<title>EduFit - Conceptual ERD</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&family=JetBrains+Mono:wght@600&display=swap');
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background-color: #FFFFFF;
    font-family: 'Plus Jakarta Sans', -apple-system, sans-serif;
    color: #0f172a;
    padding: 40px;
    width: 2200px;
    height: 1500px;
    position: relative;
  }
  .header {
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 16px;
    margin-bottom: 30px;
  }
  .header h1 { font-size: 32px; font-weight: 800; color: #0f172a; }
  .header p { font-size: 15px; color: #64748b; margin-top: 4px; }

  .canvas { position: relative; width: 100%; height: 1350px; }

  /* Entity Box */
  .entity {
    position: absolute;
    width: 220px;
    padding: 16px 20px;
    border-radius: 12px;
    background: #FFFFFF;
    border: 2px solid #0284c7;
    box-shadow: 0 4px 14px rgba(2, 132, 199, 0.12);
    text-align: center;
    font-weight: 700;
    font-size: 16px;
    color: #0369a1;
    z-index: 10;
  }
  .entity.user-box { border-color: #2563eb; color: #1e40af; box-shadow: 0 4px 14px rgba(37, 99, 235, 0.12); }
  .entity.tutor-box { border-color: #7e22ce; color: #6b21a8; box-shadow: 0 4px 14px rgba(126, 34, 206, 0.12); }
  .entity.action-box { border-color: #d97706; color: #b45309; box-shadow: 0 4px 14px rgba(217, 119, 6, 0.12); }
  .entity.eval-box { border-color: #e11d48; color: #be123c; box-shadow: 0 4px 14px rgba(225, 29, 72, 0.12); }

  /* Diamond Relationship */
  .relation {
    position: absolute;
    width: 140px;
    height: 60px;
    background: #f8fafc;
    border: 1.8px solid #94a3b8;
    border-radius: 30px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 700;
    color: #334155;
    z-index: 10;
    box-shadow: 0 2px 6px rgba(0,0,0,0.04);
  }

  svg.conn {
    position: absolute;
    top: 0; left: 0; width: 100%; height: 100%;
    z-index: 1; pointer-events: none;
  }
  .line { stroke: #94a3b8; stroke-width: 2; fill: none; }
</style>
</head>
<body>
<div class="header">
  <h1>EduFit — Conceptual Entity-Relationship Diagram (ERD)</h1>
  <p>Sơ đồ thực thể - mối quan hệ mức quan niệm theo đặc tả SRS EduFit — Thiết kế nền trắng chuẩn độ nét cao cho Canva</p>
</div>

<div class="canvas">
  <svg class="conn">
    <!-- User to Relations -->
    <path d="M 330 200 L 450 200" class="line" />
    <path d="M 590 200 L 710 200" class="line" />

    <path d="M 220 230 L 220 370" class="line" />
    <path d="M 220 430 L 220 570" class="line" />

    <path d="M 330 180 L 1200 180" class="line" />
    <path d="M 1340 180 L 1500 180" class="line" />

    <!-- StudentProfile -> LearningGoal -->
    <path d="M 820 230 L 820 370" class="line" />
    <path d="M 820 430 L 820 570" class="line" />

    <!-- TutorProfile -> TutorSubject -> Subject -->
    <path d="M 330 600 L 450 600" class="line" />
    <path d="M 590 600 L 710 770" class="line" />

    <!-- Subject -> LearningGoal -->
    <path d="M 820 740 L 820 630" class="line" />

    <!-- LearningGoal -> TutoringClass -->
    <path d="M 930 600 L 1050 600" class="line" />
    <path d="M 1190 600 L 1310 600" class="line" />

    <!-- TutoringClass -> Session -->
    <path d="M 1530 600 L 1650 600" class="line" />
    <path d="M 1790 600 L 1910 600" class="line" />

    <!-- Session -> SessionNote -->
    <path d="M 2020 630 L 2020 770" class="line" />
    <path d="M 2020 830 L 2020 970" class="line" />

    <!-- Session -> Payment -->
    <path d="M 2020 570 L 2020 430" class="line" />
    <path d="M 2020 370 L 2020 230" class="line" />
  </svg>

  <!-- Entities & Relations -->
  <div class="entity user-box" style="top: 170px; left: 110px;">User</div>
  <div class="relation" style="top: 170px; left: 450px;">links for</div>
  <div class="entity" style="top: 170px; left: 710px;">StudentProfile</div>

  <div class="relation" style="top: 370px; left: 150px;">identifies as</div>
  <div class="entity tutor-box" style="top: 570px; left: 110px;">TutorProfile</div>

  <div class="relation" style="top: 370px; left: 750px;">sets</div>
  <div class="entity" style="top: 570px; left: 710px;">LearningGoal</div>

  <div class="relation" style="top: 570px; left: 450px;">teaches</div>
  <div class="entity tutor-box" style="top: 740px; left: 710px;">Subject</div>

  <div class="relation" style="top: 570px; left: 1050px;">based on</div>
  <div class="entity action-box" style="top: 570px; left: 1310px;">TutoringClass</div>

  <div class="relation" style="top: 570px; left: 1650px;">includes</div>
  <div class="entity action-box" style="top: 570px; left: 1910px;">Session</div>

  <div class="relation" style="top: 770px; left: 1950px;">writes</div>
  <div class="entity action-box" style="top: 970px; left: 1910px;">SessionNote</div>

  <div class="relation" style="top: 370px; left: 1950px;">paid by</div>
  <div class="entity eval-box" style="top: 170px; left: 1910px;">Payment</div>

  <div class="relation" style="top: 150px; left: 1200px;">reviews</div>
  <div class="entity eval-box" style="top: 150px; left: 1500px;">Review</div>

  <div class="relation" style="top: 800px; left: 150px;">uploads</div>
  <div class="entity tutor-box" style="top: 950px; left: 110px;">CredentialDocument</div>

  <div class="relation" style="top: 170px; left: -100px; display:none;">receives</div>
  <div class="entity user-box" style="top: 350px; left: -100px; display:none;">Notification</div>
</div>

</body>
</html>
"""

html_path2 = os.path.join(BASE_DIR, "conceptual_render.html")
with open(html_path2, "w", encoding="utf-8") as f:
    f.write(html_conceptual)

out_png_path2 = os.path.join(DOCS_DIR, "edufit_erd_conceptual_4k.png")
cmd2 = [
    CHROME_PATH,
    "--headless=new",
    "--disable-gpu",
    f"--screenshot={out_png_path2}",
    "--window-size=2200,1500",
    "--force-device-scale-factor=2",
    html_path2
]
print("Rendering Conceptual ERD via Chrome Headless...")
subprocess.run(cmd2, capture_output=True)
if os.path.exists(html_path2):
    os.remove(html_path2)

if os.path.exists(out_png_path2):
    print(f"Rendered successfully: {out_png_path2} ({os.path.getsize(out_png_path2)} bytes)")
    shutil.copy(out_png_path2, os.path.join(ARTIFACT_DIR, "edufit_erd_conceptual_4k.png"))

print("All ERD image processing finished!")
