"use client";

import { useState, useCallback, useEffect } from "react";
import { verificationService, PendingQueueItem } from "@/services/verification";

export type KycQueueTab = "PENDING" | "APPROVED" | "REJECTED";

export function useAdminKycQueue(initialTab: KycQueueTab = "PENDING") {
  const [activeTab, setActiveTab] = useState<KycQueueTab>(initialTab);
  const [items, setItems] = useState<PendingQueueItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedItem, setSelectedItem] = useState<PendingQueueItem | null>(null);
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [isRevokeModalOpen, setIsRevokeModalOpen] = useState(false);

  const [prevTab, setPrevTab] = useState<KycQueueTab>(initialTab);
  if (activeTab !== prevTab) {
    setPrevTab(activeTab);
    setLoading(true);
  }

  const refreshQueue = useCallback(
    async (tabToLoad?: KycQueueTab) => {
      const tab = tabToLoad ?? activeTab;
      setLoading(true);
      try {
        const res = await verificationService.getVerifications(tab, 0, 50);
        setItems(res.items || []);
        setTotalElements(res.totalElements ?? (res.items?.length || 0));
        setTotalPages(res.totalPages ?? 1);
      } catch (err) {
        console.error("Lỗi khi tải danh sách hàng đợi KYC:", err);
        setItems([]);
        setTotalElements(0);
        setTotalPages(0);
      } finally {
        setLoading(false);
      }
    },
    [activeTab]
  );

  useEffect(() => {
    let ignore = false;
    verificationService
      .getVerifications(activeTab, 0, 50)
      .then((res) => {
        if (!ignore) {
          setItems(res.items || []);
          setTotalElements(res.totalElements ?? (res.items?.length || 0));
          setTotalPages(res.totalPages ?? 1);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!ignore) {
          console.error("Lỗi khi tải danh sách hàng đợi KYC:", err);
          setItems([]);
          setTotalElements(0);
          setTotalPages(0);
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [activeTab]);

  const handleTabChange = useCallback((newTab: KycQueueTab) => {
    setActiveTab(newTab);
  }, []);

  const handleOpenReview = useCallback((item: PendingQueueItem) => {
    setSelectedItem(item);
    setIsReviewModalOpen(true);
  }, []);

  const handleCloseReview = useCallback(() => {
    setIsReviewModalOpen(false);
  }, []);

  const handleReviewSuccess = useCallback(() => {
    setIsReviewModalOpen(false);
    void refreshQueue(activeTab);
  }, [activeTab, refreshQueue]);

  const handleOpenRevoke = useCallback((item: PendingQueueItem) => {
    setSelectedItem(item);
    setIsRevokeModalOpen(true);
  }, []);

  const handleCloseRevoke = useCallback(() => {
    setIsRevokeModalOpen(false);
  }, []);

  const handleRevokeSuccess = useCallback(() => {
    setIsRevokeModalOpen(false);
    // Tải lại dữ liệu tab hiện tại, hoặc chuyển sang tab REJECTED để xem hồ sơ đã thu hồi
    void refreshQueue(activeTab);
  }, [activeTab, refreshQueue]);

  return {
    activeTab,
    setActiveTab: handleTabChange,
    items,
    loading,
    totalElements,
    totalPages,
    selectedItem,
    isReviewModalOpen,
    isRevokeModalOpen,
    loadQueue: () => refreshQueue(activeTab),
    handleOpenReview,
    handleCloseReview,
    handleReviewSuccess,
    handleOpenRevoke,
    handleCloseRevoke,
    handleRevokeSuccess,
  };
}
