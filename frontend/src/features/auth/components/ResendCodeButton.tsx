"use client";

import React from "react";
import { Loader2, RotateCcw } from "lucide-react";

interface ResendCodeButtonProps {
  secondsLeft: number;
  isSending: boolean;
  onResend: () => void;
}

/** Nút "Gửi lại mã" bị khóa theo đếm ngược do backend quy định. */
export function ResendCodeButton({ secondsLeft, isSending, onResend }: ResendCodeButtonProps) {
  const waiting = secondsLeft > 0;
  return (
    <button
      id="resendCodeButton"
      type="button"
      onClick={onResend}
      disabled={waiting || isSending}
      className="inline-flex items-center gap-1.5 text-xs font-bold text-amber-600 hover:text-amber-700 hover:underline disabled:text-neutral-400 disabled:no-underline disabled:cursor-not-allowed cursor-pointer"
    >
      {isSending ? <Loader2 className="size-3.5 animate-spin" /> : <RotateCcw className="size-3.5" />}
      <span>{waiting ? `Gửi lại mã sau ${secondsLeft}s` : "Gửi lại mã"}</span>
    </button>
  );
}
