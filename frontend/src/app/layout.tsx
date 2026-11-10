import type { Metadata } from "next";
import { Be_Vietnam_Pro, Familjen_Grotesk, Literata } from "next/font/google";
import { AuthProvider } from "@/context/AuthContext";
import { ThemeProvider } from "@/context/ThemeContext";
import { ToastProvider } from "@/shared/ui/Toast";
import "./globals.css";

// Cả ba font đều có trong danh mục Fontshare, giấy phép SIL OFL, đủ dấu tiếng Việt.
const heading = Familjen_Grotesk({
  variable: "--font-heading",
  subsets: ["latin", "vietnamese"],
  display: "swap",
});

const body = Be_Vietnam_Pro({
  variable: "--font-sans",
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "700"],
  display: "swap",
});

const voice = Literata({
  variable: "--font-voice",
  subsets: ["latin", "vietnamese"],
  style: "italic",
  display: "swap",
});

export const metadata: Metadata = {
  title: "EduFit - Học đúng cách, tiến bộ rõ ràng",
  description: "Tìm gia sư hợp mục tiêu. Học theo kế hoạch riêng. Xem kết quả sau mỗi buổi học.",
  icons: {
    icon: [
      { url: "/icon.png", sizes: "32x32", type: "image/png" },
      { url: "/edufit-mark.png", sizes: "192x192", type: "image/png" },
    ],
    shortcut: "/favicon.ico",
    apple: [
      { url: "/apple-icon.png", sizes: "180x180", type: "image/png" },
    ],
  },
};

/**
 * RootLayout: Khung xương giao diện chung của toàn bộ ứng dụng EduFit
 * 
 * Tích hợp:
 * - Font: Familjen Grotesk cho tiêu đề và số, Be Vietnam Pro cho nội dung, Literata nghiêng cho lời của gia sư.
 * - <AuthProvider>: Cung cấp trạng thái phiên đăng nhập (Stateful Session) xuyên suốt mọi trang.
 */
export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="vi"
      data-scroll-behavior="smooth"
      className={`${heading.variable} ${body.variable} ${voice.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              try {
                const t = localStorage.getItem('edufit_theme') || 'system';
                const isDark = t === 'dark' || (t === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
                if (isDark) {
                  document.documentElement.classList.add('dark');
                  document.documentElement.setAttribute('data-theme', 'dark');
                  document.documentElement.style.colorScheme = 'dark';
                }
              } catch (e) {}
            `,
          }}
        />
      </head>
      <body
        className="min-h-full flex flex-col font-sans bg-paper text-ink"
        suppressHydrationWarning
      >
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              {children}
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
