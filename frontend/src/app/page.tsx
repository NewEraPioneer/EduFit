import Link from "next/link";
import Image from "next/image";
import {
  Check,
  CheckCircle2,
  Calendar,
  Sparkles,
  ArrowRight,
  Target,
  Users2,
  TrendingUp,
  Award,
  Clock,
  Star,
} from "lucide-react";
import HeroIllustration from "@/components/HeroIllustration";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-stone-50 text-neutral-900 font-sans selection:bg-orange-200">
      {/* 1. Header / Navbar */}
      <header className="sticky top-0 z-50 bg-stone-50/90 backdrop-blur-md border-b border-stone-200/60">
        <div className="max-w-6xl mx-auto px-6 h-20 flex justify-between items-center">
          {/* Logo EduFit */}
          <EduFitLogo href="/" size="lg" />

          {/* Navigation links */}
          <nav className="hidden md:flex items-center gap-8">
            <a
              href="#cach-hoat-dong"
              className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
            >
              Cách hoạt động
            </a>
            <Link
              href="/tutors"
              className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
            >
              Danh bạ gia sư
            </Link>
            <a
              href="#tien-bo"
              className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
            >
              Tiến bộ
            </a>
          </nav>

          {/* Right Action */}
          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="text-sm font-semibold text-neutral-700 hover:text-neutral-900 px-4 py-2.5 rounded-full border border-stone-300 bg-white hover:bg-stone-100 transition-colors"
            >
              Đăng nhập
            </Link>
            <Link
              href="/register"
              className="text-sm font-semibold bg-orange-700 hover:bg-orange-800 text-white px-5 py-2.5 rounded-full transition shadow-sm hover:shadow"
            >
              Đăng ký
            </Link>
          </div>
        </div>
      </header>

      {/* 2. Hero Section */}
      <section className="max-w-6xl mx-auto px-6 pt-12 pb-16 lg:pt-16 lg:pb-20">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          {/* Left Column */}
          <div className="lg:col-span-7 flex flex-col items-start gap-6 animate-fade-in-up">
            {/* Target pill */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 bg-stone-100 border border-stone-200/60 rounded-full">
              <span className="size-2 bg-emerald-500 rounded-full animate-pulse" />
              <span className="text-zinc-800 text-xs font-semibold">
                Dành cho học sinh lớp 6-9
              </span>
            </div>

            {/* Main Title */}
            <h1 className="font-heading text-5xl sm:text-6xl font-medium tracking-tight text-neutral-900 leading-[1.08]">
              Học đúng cách.
              <br />
              <span className="text-neutral-900">Tiến bộ rõ ràng.</span>
            </h1>

            {/* Subheading */}
            <p className="text-neutral-700 text-base sm:text-lg leading-relaxed max-w-lg">
              EduFit giúp bạn tìm gia sư hợp mục tiêu, học theo kế hoạch riêng và
              nhìn thấy mình tốt lên sau mỗi buổi học.
            </p>

            {/* CTAs */}
            <div className="flex flex-wrap items-center gap-3.5 pt-1">
              <Link
                href="/register"
                className="px-6 py-3.5 bg-orange-700 hover:bg-orange-800 text-white text-sm font-semibold rounded-full shadow-sm btn-interactive"
              >
                Đăng ký ngay
              </Link>
              <Link
                href="/login"
                className="px-6 py-3.5 bg-white hover:bg-stone-100 text-neutral-800 border border-stone-300 text-sm font-semibold rounded-full shadow-sm btn-interactive"
              >
                Tôi đã có tài khoản
              </Link>
            </div>

            {/* Micro value props */}
            <div className="flex flex-wrap items-center gap-5 pt-3">
              <div className="flex items-center gap-1.5 text-neutral-600 text-xs font-medium">
                <Check className="size-4 text-emerald-600" />
                <span>Gia sư xác minh</span>
              </div>
              <div className="flex items-center gap-1.5 text-neutral-600 text-xs font-medium">
                <Check className="size-4 text-emerald-600" />
                <span>Có giải thích matching</span>
              </div>
              <div className="flex items-center gap-1.5 text-neutral-600 text-xs font-medium">
                <Check className="size-4 text-emerald-600" />
                <span>Theo dõi tiến bộ</span>
              </div>
            </div>
          </div>

          {/* Right Column: Hero Graphic & Floating Widget */}
          <div className="lg:col-span-5 relative flex justify-center">
            <div className="w-full max-w-[380px] flex flex-col gap-4">
              {/* Graphic Illustration Box (Minh họa học sinh chinh phục mục tiêu) */}
              <div className="relative w-full rounded-2xl overflow-hidden border border-stone-200/80 shadow-md group bg-[#FEFAF2] flex items-center justify-center py-6 px-3">
                <div className="transform transition-transform duration-500 group-hover:scale-105">
                  <HeroIllustration />
                </div>
                {/* Floating badge */}
                <div className="absolute top-3 right-3 bg-white/95 backdrop-blur-md px-3 py-1 rounded-full shadow-xs border border-stone-200/50 flex items-center gap-1.5 text-[11px] font-semibold text-emerald-700">
                  <CheckCircle2 className="size-3.5 text-emerald-600" /> Chuẩn THCS Lớp 6-9
                </div>
                <div className="absolute top-3 left-3 bg-white/95 backdrop-blur-md px-3 py-1 rounded-full shadow-xs border border-stone-200/50 flex items-center gap-1.5 text-[11px] font-semibold text-sky-700">
                  <Target className="size-3.5 text-sky-600" /> Lộ trình 1-1
                </div>
              </div>

              {/* Floating Goal Card */}
              <div className="w-full p-5 bg-white/95 backdrop-blur-md rounded-2xl border border-stone-200/80 shadow-md flex flex-col gap-3.5 animate-float-delayed hover:shadow-xl transition-all duration-300">
                <div className="flex justify-between items-center">
                  <span className="text-zinc-800 text-xs font-semibold">
                    Mục tiêu của Minh
                  </span>
                  <span className="px-2.5 py-0.5 bg-orange-300 rounded-full text-neutral-900 text-xs font-semibold">
                    Lớp 9
                  </span>
                </div>
                <div className="text-neutral-900 text-base font-medium leading-snug">
                  Đạt 8+ điểm Toán trong kỳ thi vào lớp 10
                </div>
                {/* Progress bar */}
                <div className="w-full h-2.5 bg-stone-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-emerald-500 rounded-full transition-all duration-500"
                    style={{ width: "68%" }}
                  />
                </div>
                <div className="flex justify-between items-center text-xs">
                  <span className="text-neutral-500 font-normal">
                    8 / 12 cột mốc
                  </span>
                  <span className="text-zinc-800 font-semibold">68%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 3. Stats Bar */}
      <section className="max-w-6xl mx-auto px-6 mb-16">
        <div className="bg-white/80 backdrop-blur-md border border-stone-200/70 rounded-2xl px-8 py-6 grid grid-cols-2 md:grid-cols-4 gap-6 shadow-sm card-interactive">
          <div className="flex flex-col gap-1">
            <div className="text-neutral-900 text-xl font-semibold font-heading">
              Lớp 6-9
            </div>
            <div className="text-neutral-500 text-xs">Đúng chương trình THCS</div>
          </div>
          <div className="flex flex-col gap-1">
            <div className="text-neutral-900 text-xl font-semibold font-heading">
              1-1
            </div>
            <div className="text-neutral-500 text-xs">Kế hoạch học cá nhân</div>
          </div>
          <div className="flex flex-col gap-1">
            <div className="text-neutral-900 text-xl font-semibold font-heading">
              100%
            </div>
            <div className="text-neutral-500 text-xs">Gia sư được xác minh</div>
          </div>
          <div className="flex flex-col gap-1">
            <div className="text-neutral-900 text-xl font-semibold font-heading">
              Rõ ràng
            </div>
            <div className="text-neutral-500 text-xs">Tiến bộ có minh chứng</div>
          </div>
        </div>
      </section>

      {/* 4. 3 Bước để học đúng hướng */}
      <section id="cach-hoat-dong" className="max-w-6xl mx-auto px-6 py-16">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 mb-10">
          <div>
            <div className="text-orange-600 text-xs font-semibold tracking-wider uppercase mb-2">
              Bắt đầu không cần phức tạp
            </div>
            <h2 className="font-heading text-3xl sm:text-4xl font-medium text-neutral-900">
              Chỉ 3 bước để học đúng hướng.
            </h2>
          </div>
          <p className="text-neutral-500 text-xs sm:text-sm">
            Bạn có thể cập nhật mục tiêu bất cứ lúc nào.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {/* Step 1 */}
          <div className="bg-white p-7 rounded-2xl border border-stone-200/70 shadow-xs flex flex-col justify-between h-72 card-interactive group">
            <div className="flex justify-between items-center">
              <span className="text-neutral-400 text-sm font-semibold">01</span>
              <div className="size-11 bg-sky-100 text-sky-600 rounded-2xl flex justify-center items-center group-hover:scale-110 transition-transform duration-300">
                <Target className="size-5" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <h3 className="text-zinc-900 text-lg font-semibold group-hover:text-sky-600 transition-colors">
                Nói điều bạn muốn đạt
              </h3>
              <p className="text-neutral-600 text-sm leading-relaxed">
                Chọn môn, lớp và mục tiêu - ví dụ củng cố kiến thức hoặc ôn thi lớp 10.
              </p>
            </div>
          </div>

          {/* Step 2 */}
          <div className="bg-white p-7 rounded-2xl border border-stone-200/70 shadow-xs flex flex-col justify-between h-72 card-interactive group">
            <div className="flex justify-between items-center">
              <span className="text-neutral-400 text-sm font-semibold">02</span>
              <div className="size-11 bg-orange-100 text-orange-600 rounded-2xl flex justify-center items-center group-hover:scale-110 transition-transform duration-300">
                <Users2 className="size-5" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <h3 className="text-zinc-900 text-lg font-semibold group-hover:text-orange-600 transition-colors">
                Nhận gợi ý có lý do
              </h3>
              <p className="text-neutral-600 text-sm leading-relaxed">
                EduFit ghép bạn với gia sư phù hợp về lịch, mục tiêu và ngân sách.
              </p>
            </div>
          </div>

          {/* Step 3 */}
          <div className="bg-white p-7 rounded-2xl border border-stone-200/70 shadow-xs flex flex-col justify-between h-72 card-interactive group">
            <div className="flex justify-between items-center">
              <span className="text-neutral-400 text-sm font-semibold">03</span>
              <div className="size-11 bg-emerald-100 text-emerald-600 rounded-2xl flex justify-center items-center group-hover:scale-110 transition-transform duration-300">
                <TrendingUp className="size-5" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <h3 className="text-zinc-900 text-lg font-semibold group-hover:text-emerald-600 transition-colors">
                Học và nhìn thấy tiến bộ
              </h3>
              <p className="text-neutral-600 text-sm leading-relaxed">
                Theo dõi buổi học, quiz và từng cột mốc đã hoàn thành trên một nơi.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 5. Gia sư hợp với cách bạn học (Matching) */}
      <section id="gia-su" className="bg-stone-100 py-20 border-y border-stone-200/60">
        <div className="max-w-6xl mx-auto px-6">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
            {/* Left Content */}
            <div className="lg:col-span-5 flex flex-col gap-5">
              <div className="text-sky-600 text-xs font-semibold tracking-wide uppercase">
                Không chỉ là một danh sách
              </div>
              <h2 className="font-heading text-3xl sm:text-4xl font-medium text-neutral-900 leading-tight">
                Gia sư hợp với
                <br />
                cách bạn học.
              </h2>
              <p className="text-neutral-700 text-base leading-relaxed">
                Mỗi gợi ý đều nói rõ vì sao phù hợp, để bạn và gia đình dễ chọn hơn.
              </p>

              <div className="flex flex-col gap-3 py-2">
                <div className="flex items-center gap-3">
                  <div className="size-7 bg-white rounded-lg border border-stone-200/60 flex items-center justify-center text-emerald-600 shadow-xs">
                    <Check className="size-4" />
                  </div>
                  <span className="text-zinc-800 text-sm font-medium">
                    Đúng Toán lớp 9
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="size-7 bg-white rounded-lg border border-stone-200/60 flex items-center justify-center text-emerald-600 shadow-xs">
                    <Check className="size-4" />
                  </div>
                  <span className="text-zinc-800 text-sm font-medium">
                    Trùng 3 khung giờ rảnh
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="size-7 bg-white rounded-lg border border-stone-200/60 flex items-center justify-center text-emerald-600 shadow-xs">
                    <Check className="size-4" />
                  </div>
                  <span className="text-zinc-800 text-sm font-medium">
                    Có kinh nghiệm ôn thi lớp 10
                  </span>
                </div>
              </div>

              <div>
                <Link
                  href="/tutors"
                  className="inline-flex items-center gap-2 px-6 py-3.5 bg-sky-600 hover:bg-sky-700 text-white text-sm font-semibold rounded-full transition shadow-sm active:scale-95"
                >
                  Khám phá gia sư
                </Link>
              </div>
            </div>

            {/* Right Tutor Card */}
            <div className="lg:col-span-7 flex justify-center">
              <div className="w-full max-w-md shadow-md rounded-2xl overflow-hidden border border-stone-200/80 card-interactive">
                {/* Header Match Badge */}
                <div className="bg-neutral-900 text-white px-5 py-3.5 flex justify-between items-center text-xs">
                  <span className="font-medium">
                    Gợi ý phù hợp nhất cho mục tiêu của bạn
                  </span>
                  <span className="text-orange-300 font-semibold">
                    94% phù hợp
                  </span>
                </div>

                {/* Card Content */}
                <div className="bg-white p-6 sm:p-7 flex flex-col gap-5">
                  <div className="flex items-center gap-4">
                    <Image
                      src="/images/tutor-lan.jpg"
                      alt="Cô Nguyễn Hoàng Lan"
                      width={56}
                      height={56}
                      className="size-14 rounded-2xl object-cover border-2 border-stone-200 shadow-xs"
                    />
                    <div className="flex-1">
                      <h4 className="text-neutral-900 text-lg font-semibold">
                        Cô Nguyễn Hoàng Lan
                      </h4>
                      <p className="text-neutral-500 text-xs">
                        Toán 8-9 · 6 năm kinh nghiệm
                      </p>
                    </div>
                    <div className="px-2.5 py-1 bg-emerald-50 text-emerald-800 rounded-full flex items-center gap-1.5 text-xs font-semibold border border-emerald-200/50">
                      <Award className="size-3 text-emerald-600" />
                      <span>Đã xác minh</span>
                    </div>
                  </div>

                  {/* Quote */}
                  <div className="bg-stone-50 border border-stone-200/60 p-4 rounded-xl text-neutral-700 text-sm leading-relaxed italic">
                    &ldquo;Giúp học sinh hiểu bản chất, luyện từng dạng bài và tự tin trước kỳ thi.&rdquo;
                  </div>

                  {/* Tutor Metrics */}
                  <div className="flex justify-between items-center border-t border-b border-stone-100 py-3">
                    <div>
                      <div className="text-neutral-900 text-base font-semibold flex items-center gap-1">
                        <Star className="size-4 text-amber-500 fill-amber-500" />
                        4.9 / 5
                      </div>
                      <div className="text-neutral-500 text-xs">Đánh giá</div>
                    </div>
                    <div className="text-right">
                      <div className="text-neutral-900 text-base font-semibold">
                        128
                      </div>
                      <div className="text-neutral-500 text-xs">Buổi đã dạy</div>
                    </div>
                  </div>

                  {/* Availability & Action */}
                  <div className="flex justify-between items-center pt-1">
                    <div className="text-sky-600 text-xs font-medium flex items-center gap-1.5">
                      <Clock className="size-3.5" /> Còn trống: T3, T5, T7
                    </div>
                    <Link
                      href="/tutors"
                      className="px-5 py-2.5 bg-stone-100 hover:bg-stone-200 text-neutral-900 text-sm font-semibold rounded-full transition"
                    >
                      Xem hồ sơ
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 6. Dashboard Preview & Dấu mốc */}
      <section id="tien-bo" className="max-w-6xl mx-auto px-6 py-20">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          {/* Left Mini Dashboard Widget */}
          <div className="lg:col-span-6 flex justify-center">
            <div className="w-full max-w-md bg-white p-6 rounded-2xl border border-stone-200/80 shadow-md flex flex-col gap-4">
              <div className="flex justify-between items-center">
                <div>
                  <div className="text-neutral-900 text-lg font-semibold">
                    Chào Minh 👋
                  </div>
                  <div className="text-neutral-500 text-xs">
                    Tuần này bạn đang tiến bộ tốt.
                  </div>
                </div>
                <div className="size-9 bg-stone-100 rounded-xl flex justify-center items-center text-neutral-600">
                  <Sparkles className="size-4 text-amber-500" />
                </div>
              </div>

              {/* Goal Highlight Box */}
              <div className="p-4 bg-neutral-900 text-white rounded-xl flex flex-col gap-2.5">
                <div className="flex justify-between items-center text-xs">
                  <span className="font-semibold tracking-wider uppercase text-neutral-300">
                    Mục tiêu đang tập trung
                  </span>
                  <span className="text-orange-300 font-semibold">68%</span>
                </div>
                <div className="text-sm font-semibold">
                  Đạt 8+ điểm Toán thi vào lớp 10
                </div>
                <div className="w-full h-2 bg-white/20 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-emerald-500 rounded-full"
                    style={{ width: "68%" }}
                  />
                </div>
              </div>

              {/* 3 Metric Pills */}
              <div className="grid grid-cols-3 gap-2.5">
                <div className="p-3 bg-stone-50 border border-stone-100 rounded-xl flex flex-col gap-1">
                  <div className="size-6 bg-emerald-100 text-emerald-600 rounded-lg flex items-center justify-center">
                    <Check className="size-3.5" />
                  </div>
                  <div className="text-neutral-900 text-base font-semibold">
                    4 / 5
                  </div>
                  <div className="text-neutral-500 text-[11px]">Cột mốc</div>
                </div>
                <div className="p-3 bg-stone-50 border border-stone-100 rounded-xl flex flex-col gap-1">
                  <div className="size-6 bg-sky-100 text-sky-600 rounded-lg flex items-center justify-center">
                    <TrendingUp className="size-3.5" />
                  </div>
                  <div className="text-neutral-900 text-base font-semibold">
                    8.4
                  </div>
                  <div className="text-neutral-500 text-[11px]">Điểm quiz</div>
                </div>
                <div className="p-3 bg-stone-50 border border-stone-100 rounded-xl flex flex-col gap-1">
                  <div className="size-6 bg-orange-100 text-orange-600 rounded-lg flex items-center justify-center">
                    <Calendar className="size-3.5" />
                  </div>
                  <div className="text-neutral-900 text-base font-semibold">
                    6
                  </div>
                  <div className="text-neutral-500 text-[11px]">Ngày liên tiếp</div>
                </div>
              </div>

              {/* Next session card */}
              <div className="p-3 bg-stone-50 border border-stone-100 rounded-xl flex items-center gap-3">
                <div className="size-11 bg-orange-300 rounded-xl flex flex-col justify-center items-center text-neutral-900">
                  <span className="text-[10px] font-semibold">T5</span>
                  <span className="text-base font-bold leading-none">18</span>
                </div>
                <div className="flex-1">
                  <div className="text-neutral-900 text-sm font-semibold">
                    Hàm số bậc nhất
                  </div>
                  <div className="text-neutral-500 text-xs">
                    19:00 - 20:30 · Cô Lan
                  </div>
                </div>
                <button className="px-3 py-1.5 bg-stone-200/80 hover:bg-stone-300 text-neutral-800 text-xs font-semibold rounded-full transition">
                  Chi tiết
                </button>
              </div>
            </div>
          </div>

          {/* Right Text */}
          <div className="lg:col-span-6 flex flex-col gap-5">
            <div className="text-emerald-600 text-xs font-semibold tracking-wide uppercase">
              Biết mình đang ở đâu
            </div>
            <h2 className="font-heading text-3xl sm:text-4xl font-medium text-neutral-900 leading-tight">
              Mỗi buổi học đều để lại một dấu mốc.
            </h2>
            <p className="text-neutral-700 text-base leading-relaxed">
              Không còn học trong mơ hồ. Bạn thấy mục tiêu, điểm quiz, cột mốc và
              buổi học tiếp theo ngay trên dashboard.
            </p>

            <div className="p-4 bg-stone-100 border border-stone-200/70 rounded-xl flex items-start gap-3 mt-2">
              <Sparkles className="size-5 text-purple-600 mt-0.5 shrink-0" />
              <div className="flex flex-col gap-1">
                <div className="text-zinc-800 text-xs font-semibold">
                  AI gợi ý, gia sư quyết định
                </div>
                <div className="text-neutral-600 text-xs leading-relaxed">
                  Mọi nhận xét học tập đều được gia sư xem và xác nhận trước khi
                  gửi đến bạn.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 7. Gold CTA Banner */}
      <section className="max-w-6xl mx-auto px-6 pb-20">
        <div className="relative bg-gradient-to-r from-amber-300 via-orange-300 to-amber-400 rounded-3xl p-10 sm:p-14 text-center overflow-hidden shadow-sm flex flex-col items-center justify-center gap-4">
          {/* Decorative shapes */}
          <div className="size-32 bg-sky-300/80 rounded-full absolute -bottom-10 -left-10 pointer-events-none blur-xs" />
          <div className="size-36 bg-pink-400/70 rounded-full absolute -top-10 -right-10 pointer-events-none blur-xs" />

          <div className="text-zinc-800 text-xs font-semibold tracking-wider uppercase">
            Sẵn sàng học rõ ràng hơn?
          </div>
          <h2 className="font-heading text-3xl sm:text-5xl font-medium text-neutral-900 max-w-xl">
            Bắt đầu từ mục tiêu của bạn.
          </h2>
          <p className="text-neutral-800 text-base max-w-md">
            Chia sẻ môn học và điều bạn muốn đạt. EduFit sẽ giúp bạn tìm bước tiếp
            theo phù hợp.
          </p>
          <div className="pt-2">
            <Link
              href="/register"
              className="inline-flex items-center gap-2 px-7 py-3.5 bg-orange-700 hover:bg-orange-800 text-white text-sm font-semibold rounded-full transition shadow-md hover:shadow-lg active:scale-95"
            >
              Bắt đầu miễn phí
              <ArrowRight className="size-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* 8. Footer */}
      <footer className="border-t border-stone-200/80 py-10 bg-stone-50">
        <div className="max-w-6xl mx-auto px-6 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-6 text-xs text-neutral-500">
          <div>
            <div className="mb-2">
              <EduFitLogo href="/" size="md" />
            </div>
            <p>Đúng gia sư - Đúng mục tiêu - Thấy rõ tiến bộ</p>
          </div>
          <div className="flex flex-col sm:items-end gap-2">
            <div className="flex flex-wrap gap-4 text-neutral-700">
              <a href="#" className="hover:text-neutral-900 transition">
                Về EduFit
              </a>
              <span>·</span>
              <a href="#" className="hover:text-neutral-900 transition">
                Trợ giúp
              </a>
              <span>·</span>
              <a href="#" className="hover:text-neutral-900 transition">
                Điều khoản
              </a>
              <span>·</span>
              <a href="#" className="hover:text-neutral-900 transition">
                Quyền riêng tư
              </a>
            </div>
            <div>© 2026 EduFit. Mọi quyền được bảo lưu.</div>
          </div>
        </div>
      </footer>
    </div>
  );
}
