import Link from "next/link";
import { EduFitLogo } from "@/shared/ui/EduFitLogo";

const LINKS = [
  { href: "/#cach-hoat-dong", label: "Cách hoạt động" },
  { href: "/tutors", label: "Danh bạ gia sư" },
  { href: "/login", label: "Đăng nhập" },
  { href: "/register", label: "Đăng ký" },
];

/** Footer rèm: nằm yên phía sau, phần nội dung cuộn lên để lộ footer. */
export function CurtainFooter() {
  return (
    <footer className="sticky bottom-0 z-0 bg-night text-white">
      <div className="mx-auto flex max-w-6xl flex-col justify-between gap-6 px-4 py-10 sm:flex-row sm:items-end sm:px-6">
        <div className="flex flex-col gap-2">
          <div className="w-fit rounded-xl bg-white px-2.5 py-1.5">
            <EduFitLogo href="/" size="md" />
          </div>
          <p className="text-sm text-white/75">Đúng gia sư. Đúng mục tiêu. Thấy rõ tiến bộ.</p>
        </div>
        <div className="flex flex-col gap-3 sm:items-end">
          <nav aria-label="Chân trang" className="flex flex-wrap gap-x-5 gap-y-1">
            {LINKS.map((link) => (
              <Link key={link.href} href={link.href} className="py-2 text-sm text-white/85 hover:text-white hover:underline">
                {link.label}
              </Link>
            ))}
          </nav>
          <p className="text-xs text-white/70">
            © 2026 EduFit.{" "}
            <a href="https://www.flaticon.com/uicons" target="_blank" rel="noreferrer" className="underline">
              Uicons by Flaticon
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}
