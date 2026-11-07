"use client";

import React from "react";
import { KeyRound } from "lucide-react";

interface OtpCodeInputProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
  disabled?: boolean;
}

/**
 * Ô nhập mã OTP 6 số. Dùng một input duy nhất để dán mã, tự điền từ SMS/email (one-time-code)
 * và trình đọc màn hình hoạt động tự nhiên.
 */
export function OtpCodeInput({ id, value, onChange, error, disabled }: OtpCodeInputProps) {
  const errorId = `${id}-error`;
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-semibold text-neutral-700 mb-1.5">
        Mã xác thực 6 số
      </label>
      <div className="relative">
        <KeyRound className="size-4 text-neutral-400 absolute left-3.5 top-1/2 -translate-y-1/2" aria-hidden="true" />
        <input
          id={id}
          name="code"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          autoFocus
          disabled={disabled}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="••••••"
          aria-invalid={Boolean(error)}
          aria-describedby={error ? errorId : undefined}
          className="w-full pl-10 pr-4 py-3 text-lg font-bold tracking-[0.5em] text-center bg-stone-50 border border-stone-200 rounded-2xl focus:bg-white focus:outline-hidden focus:border-amber-500 focus:ring-2 focus:ring-amber-500/20 transition-all text-neutral-900 placeholder:text-neutral-300 disabled:opacity-60"
        />
      </div>
      {error && (
        <p id={errorId} className="text-[11px] text-rose-600 font-medium mt-1 pl-1">
          {error}
        </p>
      )}
    </div>
  );
}
