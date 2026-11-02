export interface NotificationItem {
  notificationId: string;
  type: string;
  title: string;
  shortText: string;
  targetType?: string;
  targetId?: string;
  aboutStudentId?: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface MarkReadResponse {
  notificationId: string;
  isRead: boolean;
}

export interface MarkAllReadResponse {
  count: number;
}
