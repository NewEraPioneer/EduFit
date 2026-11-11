"use client";

import React from "react";
import { Sparkles, Bot, Info } from "lucide-react";

export interface AiExplanationBadgeProps {
  explanation: string;
  aiGenerated?: boolean;
}

export function AiExplanationBadge({ explanation, aiGenerated = false }: AiExplanationBadgeProps) {
  return (
    <div className="relative overflow-hidden p-4 sm:p-5 rounded-2xl bg-action/10 border border-action-line/90 shadow-2xs space-y-2">
      <div className="flex items-center justify-between">
        <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-action-soft text-action-strong text-xs font-medium">
          <Sparkles className="size-3 text-action-strong" />
          <span>{aiGenerated ? "AI Grounded Match Explanation" : "Phân tích mức độ tương thích"}</span>
        </div>
        {aiGenerated && (
          <span className="text-xs text-action-strong font-medium flex items-center gap-1">
            <Bot className="size-3" /> Sinh bởi AI Model
          </span>
        )}
      </div>

      <p className="text-xs text-ink leading-relaxed font-medium">
        {explanation}
      </p>
    </div>
  );
}
