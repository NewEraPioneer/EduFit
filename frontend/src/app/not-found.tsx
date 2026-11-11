import { Mascot } from "@/shared/motion/Mascot";
import { Button } from "@/shared/ui/Button";

export default function NotFound() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col items-center justify-center gap-3 p-6 text-center">
      <Mascot className="size-24" />
      <h1 className="font-heading text-2xl font-bold text-ink">Không tìm thấy trang này</h1>
      <p className="text-sm text-muted">Địa chỉ có thể đã đổi. Hãy bắt đầu lại từ trang chủ.</p>
      <Button href="/">Về trang chủ</Button>
    </div>
  );
}
