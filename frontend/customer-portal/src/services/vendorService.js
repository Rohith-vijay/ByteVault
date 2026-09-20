import apiClient from "./apiClient";

export const vendorService = {
  // Get authenticated vendor's profile & approval status
  getMyVendorProfile: async () => {
    try {
      const res = await apiClient.get("/users/me/vendor-profile");
      return res?.data !== undefined ? res.data : res;
    } catch (err) {
      console.warn("[vendorService] Failed to fetch vendor profile:", err.message);
      throw err;
    }
  },

  // Update store details
  updateMyVendorProfile: async (data) => {
    const res = await apiClient.put("/users/me/vendor-profile", data);
    return res?.data !== undefined ? res.data : res;
  },

  // Public storefront lookup
  getStoreBySlug: async (slug) => {
    const res = await apiClient.get(`/users/stores/${slug}`);
    return res?.data !== undefined ? res.data : res;
  }
};

export default vendorService;
