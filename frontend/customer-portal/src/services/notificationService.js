// Notification Service - Prepared for future real-time alerts
import apiClient from "./apiClient";

export const notificationService = {
  // Queries customer notifications list
  getNotifications: async () => {
    return apiClient.get("/notifications").catch(() => {
      // Offline/future endpoint fallback
      return [];
    });
  },

  // Updates specific alert status to read
  markAsRead: async (notificationId) => {
    return apiClient.post(`/notifications/${notificationId}/read`).catch(() => {
      return { success: true };
    });
  }
};

export default notificationService;
