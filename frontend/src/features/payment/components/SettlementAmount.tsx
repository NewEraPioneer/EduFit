"use client";
import { money, settlement } from "../api/paymentApi";

/** Số tiền của một lần tất toán: chỉ một dòng khi trả hết hoặc hoàn hết, hai dòng khi chia. */
export function SettlementAmount({
  entry,
  className = "",
}: {
  entry: { available_delta: number; held_delta: number };
  className?: string;
}) {
  const { paid, refund } = settlement(entry);
  if (paid > 0 && refund > 0)
    return (
      <div className={`whitespace-nowrap text-sm font-medium ${className}`}>
        <p>Thanh toán {money(paid)}</p>
        <p className="text-success-strong">Hoàn +{money(refund)}</p>
      </div>
    );
  return (
    <p
      className={`whitespace-nowrap text-sm font-medium ${
        refund > 0 ? "text-success-strong" : ""
      } ${className}`}
    >
      {refund > 0 ? `+${money(refund)}` : `-${money(paid)}`}
    </p>
  );
}
