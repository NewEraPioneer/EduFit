import { CtaSection } from "@/features/landing/CtaSection";
import { CurtainFooter } from "@/features/landing/CurtainFooter";
import { HeroSection } from "@/features/landing/HeroSection";
import { LandingHeader } from "@/features/landing/LandingHeader";
import { MatchSection } from "@/features/landing/MatchSection";
import { ProgressSection } from "@/features/landing/ProgressSection";
import { ProofStrip } from "@/features/landing/ProofStrip";
import { StepsBento } from "@/features/landing/StepsBento";

export default function HomePage() {
  return (
    <div className="font-sans text-ink">
      <main className="relative z-10 rounded-b-[32px] bg-paper">
        <LandingHeader />
        <HeroSection />
        <ProofStrip />
        <StepsBento />
        <MatchSection />
        <ProgressSection />
        <CtaSection />
      </main>
      <CurtainFooter />
    </div>
  );
}
