import { SessionBillingPanel } from "@/features/payment/components/SessionBillingPanel";
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <main className="mx-auto max-w-4xl space-y-5 pb-8">
      <h1 className="text-2xl font-bold">Chi tiết học phí buổi học</h1>
      <SessionBillingPanel sessionId={id} role="PARENT" />
    </main>
  );
}
