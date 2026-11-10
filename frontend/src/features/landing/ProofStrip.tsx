import { ConceptIcon, type ConceptIconName } from "@/shared/ui/ConceptIcon";

const ITEMS: { icon: ConceptIconName; value: string; label: string }[] = [
  { icon: "cap", value: "Lớp 6-9", label: "Đúng chương trình THCS" },
  { icon: "route", value: "1 kèm 1", label: "Kế hoạch riêng cho từng em" },
  { icon: "shield", value: "100%", label: "Gia sư đã xác minh bằng cấp" },
  { icon: "comment", value: "Mỗi buổi", label: "Gia sư viết nhận xét" },
];

export function ProofStrip() {
  return (
    <section className="mx-auto max-w-6xl px-4 pb-8 sm:px-6">
      <ul className="grid grid-cols-2 gap-x-4 gap-y-6 border-y border-line py-6 md:grid-cols-4">
        {ITEMS.map((item) => (
          <li key={item.value} className="flex items-start gap-3">
            <ConceptIcon name={item.icon} className="mt-0.5 size-7 shrink-0 text-brand-strong" />
            <div>
              <div className="font-heading text-xl font-bold text-ink">{item.value}</div>
              <div className="text-sm text-muted">{item.label}</div>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
