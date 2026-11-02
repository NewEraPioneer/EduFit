"use client";

import React, { createContext, useContext, useState, useCallback } from "react";
import { CheckCircle2, AlertCircle, X, Info } from "lucide-react";

export type ToastType = "success" | "error" | "info";

interface Toast {
  id: string;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  showToast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, type: ToastType = "success") => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { id, type, message }]);

    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3500);
  }, []);

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none px-4 sm:px-0">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-center justify-between gap-3 p-3.5 rounded-2xl shadow-xl border text-xs font-semibold backdrop-blur-md transition-all animate-in slide-in-from-bottom-2 duration-200 ${
              toast.type === "success"
                ? "bg-emerald-900/95 text-white border-emerald-700/60"
                : toast.type === "error"
                ? "bg-rose-900/95 text-white border-rose-700/60"
                : "bg-neutral-900/95 text-white border-neutral-700/60"
            }`}
          >
            <div className="flex items-center gap-2.5">
              {toast.type === "success" && <CheckCircle2 className="size-4.5 text-emerald-400 shrink-0" />}
              {toast.type === "error" && <AlertCircle className="size-4.5 text-rose-400 shrink-0" />}
              {toast.type === "info" && <Info className="size-4.5 text-sky-400 shrink-0" />}
              <span>{toast.message}</span>
            </div>
            <button
              type="button"
              onClick={() => removeToast(toast.id)}
              className="p-1 rounded-lg hover:bg-white/20 transition-colors"
            >
              <X className="size-3.5 opacity-80" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    return {
      showToast: () => {},
    };
  }
  return ctx;
}
