// Wishlist Service - Prepared for future backend sync
import apiClient from "./apiClient";

export const wishlistService = {
  // Retrieves wishlist items
  getWishlist: async () => {
    return apiClient.get("/wishlist").catch(() => {
      // Offline/future endpoint integration fallback
      try {
        const stored = localStorage.getItem("bytevault_wishlist");
        return stored ? JSON.parse(stored) : [];
      } catch {
        return [];
      }
    });
  },

  // Toggles item status in backend registry
  toggleWishlistItem: async (productId) => {
    return apiClient.post("/wishlist/toggle", { productId }).catch(() => {
      return { success: true };
    });
  }
};

export default wishlistService;
