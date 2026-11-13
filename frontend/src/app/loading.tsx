import { Skeleton } from "@/shared/ui/Skeleton";

export default function Loading() {
  return (
    <div role="status" aria-label="Đang tải" className="mx-auto w-full max-w-6xl space-y-4 p-6">
      <Skeleton className="h-9 w-64" />
      <Skeleton className="h-40" />
      <Skeleton className="h-64" />
    </div>
  );
}
