"use client";

import { useEffect, useState } from "react";
import { Mascot, type MascotMood } from "./Mascot";

/** Mầm che mắt ở ô mật khẩu và vui lên khi người dùng gửi biểu mẫu. */
export function AuthMascot() {
  const [mood, setMood] = useState<MascotMood>("idle");

  useEffect(() => {
    const onFocus = (event: FocusEvent) => {
      const input = event.target as HTMLInputElement;
      if (input.tagName === "INPUT") setMood(input.type === "password" ? "shy" : "idle");
    };
    const onBlur = () => setMood("idle");
    const onSubmit = () => setMood("happy");
    document.addEventListener("focusin", onFocus);
    document.addEventListener("focusout", onBlur);
    document.addEventListener("submit", onSubmit);
    return () => {
      document.removeEventListener("focusin", onFocus);
      document.removeEventListener("focusout", onBlur);
      document.removeEventListener("submit", onSubmit);
    };
  }, []);

  return <Mascot mood={mood} className="size-16" />;
}
