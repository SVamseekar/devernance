"use client";

import { createContext, useContext, useState, ReactNode, useMemo } from "react";
import { usePathname } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { AiSystem, DataContract, DriftEvent, AuditEvent, DataContractStatus, ReleaseDecision } from "@/lib/types";
import { api } from "@/lib/api";
import { allowMockFallback } from "@/lib/live-mode";
import { MOCK_SYSTEMS, MOCK_CONTRACTS, MOCK_DRIFT_EVENTS, MOCK_AUDIT_EVENTS } from "@/lib/mock-data";
import { useSystems } from "@/hooks/use-systems";
import { useContracts } from "@/hooks/use-contracts";
import { useAuditEvents } from "@/hooks/use-audit-events";
import { isLiveEntityId, isMockEntityId } from "@/lib/ids";

interface DashboardContextType {
  // Selected state
  selectedSystem: AiSystem | null;
  setSelectedSystem: (s: AiSystem | null) => void;
  selectedContract: DataContract | null;
  setSelectedContract: (c: DataContract | null) => void;

  allSystems: AiSystem[];

  // Drift Events
  driftEvents: DriftEvent[];
  allAudits: AuditEvent[];
  acknowledgeDrift: (id: string, note: string) => Promise<void>;
  resolveDrift: (id: string, note: string) => Promise<void>;
  contractsList: DataContract[];

  openSystemDetails: (id: string) => void;
  openContractDetails: (id: string) => void;
}

const DashboardContext = createContext<DashboardContextType | undefined>(undefined);

export function DashboardProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const pollAudits = pathname === "/audit" || pathname.startsWith("/audit/");

  // Selected drawers
  const [selectedSystem, setSelectedSystem] = useState<AiSystem | null>(null);
  const [selectedContract, setSelectedContract] = useState<DataContract | null>(null);

  // Live API (BFF → Dell/local Spring). Fall back to mocks only when API is offline.
  const { data: apiSystems, isSuccess: systemsOk, isError: systemsError } = useSystems();
  const { data: apiContracts, isSuccess: contractsOk, isError: contractsError } = useContracts();
  const { data: apiAudits, isSuccess: auditsOk, isError: auditsError } = useAuditEvents(
    undefined,
    { refetchInterval: pollAudits ? 15_000 : false },
  );

  const liveSystems =
    systemsOk &&
    !systemsError &&
    Array.isArray(apiSystems) &&
    apiSystems.length > 0 &&
    isLiveEntityId(apiSystems[0]?.id)
      ? apiSystems
      : null;

  const liveContracts =
    contractsOk &&
    !contractsError &&
    Array.isArray(apiContracts) &&
    (apiContracts.length === 0 || isLiveEntityId(apiContracts[0]?.id))
      ? apiContracts
      : null;

  const liveAudits =
    auditsOk &&
    !auditsError &&
    Array.isArray(apiAudits) &&
    (apiAudits.length === 0 || !isMockEntityId(apiAudits[0]?.id))
      ? apiAudits
      : null;

  const [localDriftEvents, setLocalDriftEvents] = useState<DriftEvent[]>(() => (allowMockFallback() ? MOCK_DRIFT_EVENTS : []));

  async function setDriftStatus(eventId: string, status: "ACKNOWLEDGED" | "RESOLVED", note: string) {
    if (liveContracts) {
      const event = (queryClient.getQueriesData<DriftEvent[]>({ queryKey: ["drift-events"] })
        .flatMap(([, data]) => data ?? []))
        .find((e) => e.id === eventId);
      if (!event) return;
      await api.contracts.updateDriftEvent(event.contractId, eventId, status, note);
      await queryClient.invalidateQueries({ queryKey: ["drift-events", event.contractId] });
      await queryClient.invalidateQueries({ queryKey: ["contracts"] });
      await queryClient.invalidateQueries({ queryKey: ["systems"] });
      return;
    }
    setLocalDriftEvents((p) =>
      p.map((e) => (e.id === eventId ? { ...e, status, resolutionNote: note, updatedAt: new Date().toISOString() } : e)),
    );
  }

  const acknowledgeDrift = (id: string, note: string) => setDriftStatus(id, "ACKNOWLEDGED", note);
  const resolveDrift = (id: string, note: string) => setDriftStatus(id, "RESOLVED", note);

  const baseSystems = liveSystems ?? (allowMockFallback() ? MOCK_SYSTEMS : (apiSystems ?? []));
  const baseContracts = liveContracts ?? (allowMockFallback() ? MOCK_CONTRACTS : (apiContracts ?? []));
  // Local drift mock only when not on live contracts (demo offline mode).
  const driftEvents = liveContracts ? [] : localDriftEvents;

  const allAudits = useMemo(
    () => liveAudits ?? (allowMockFallback() ? MOCK_AUDIT_EVENTS : []),
    [liveAudits],
  );

  // Recalculate contract status dynamically based on resolved drift events (demo path)
  const calculatedContracts = useMemo(() => {
    return baseContracts.map((contract) => {
      if (liveContracts) {
        // Trust API status when live
        return contract;
      }
      const events = driftEvents.filter((e) => e.contractId === contract.id && e.status !== "RESOLVED");
      const hasBreach = events.some((e) => e.severity === "BREACH");
      const hasWarning = events.some((e) => e.severity === "WARNING");
      const status = (hasBreach ? "BREACH" : hasWarning ? "WARNING" : "HEALTHY") as DataContractStatus;
      return { ...contract, status };
    });
  }, [baseContracts, driftEvents, liveContracts]);

  // Recalculate system release decision based on contract status and manual overrides
  const allSystems = useMemo(() => {
    return baseSystems.map((sys) => {
      // Live API already carries gate fields — only recompute for demo/mock.
      if (liveSystems && isLiveEntityId(sys.id)) {
        return sys;
      }

      const linkedContracts = calculatedContracts.filter((c) => c.systemId === sys.id);
      const hasBreaches = linkedContracts.some((c) => c.status === "BREACH");
      const hasWarnings = linkedContracts.some((c) => c.status === "WARNING");
      const dataContractStatus = (hasBreaches ? "BREACH" : hasWarnings ? "WARNING" : "HEALTHY") as DataContractStatus;

      // Clean resolved gaps from lists
      let openGaps = [...(sys.openGaps ?? [])];
      if (!hasBreaches) {
        openGaps = openGaps.filter(
          (g) =>
            !g.toLowerCase().includes("phi redaction") &&
            !g.toLowerCase().includes("denial_reason_category") &&
            !g.toLowerCase().includes("diagnosis_code_icd11")
        );
      }

      let releaseDecision = sys.releaseDecision;
      if (dataContractStatus === "BREACH" || sys.evalScore < 75) {
        releaseDecision = "blocked" as ReleaseDecision;
      } else if (dataContractStatus === "WARNING" || sys.evalScore < 85 || openGaps.length > 0) {
        releaseDecision = "review" as ReleaseDecision;
      } else {
        releaseDecision = "pass" as ReleaseDecision;
      }

      return {
        ...sys,
        dataContractStatus,
        releaseDecision,
        openGaps,
      };
    });
  }, [baseSystems, calculatedContracts, liveSystems]);

  // Update selected drawers with recalculated values
  const updatedSelectedSystem = selectedSystem
    ? allSystems.find((s) => s.id === selectedSystem.id) || selectedSystem
    : null;

  const updatedSelectedContract = selectedContract
    ? calculatedContracts.find((c) => c.id === selectedContract.id) || selectedContract
    : null;

  function openSystemDetails(id: string) {
    const found = allSystems.find((s) => s.id === id);
    if (found) {
      setSelectedSystem(found);
      setSelectedContract(null);
    }
  }

  function openContractDetails(id: string) {
    const found = calculatedContracts.find((c) => c.id === id);
    if (found) {
      setSelectedContract(found);
      setSelectedSystem(null);
    }
  }

  return (
    <DashboardContext.Provider
      value={{
        selectedSystem: updatedSelectedSystem,
        setSelectedSystem,
        selectedContract: updatedSelectedContract,
        setSelectedContract,
        allSystems,
        driftEvents,
        acknowledgeDrift,
        resolveDrift,
        contractsList: calculatedContracts,
        openSystemDetails,
        openContractDetails,
        allAudits,
      }}
    >
      {children}
    </DashboardContext.Provider>
  );
}

export function useDashboard() {
  const ctx = useContext(DashboardContext);
  if (!ctx) throw new Error("useDashboard must be used within DashboardProvider");
  return ctx;
}
