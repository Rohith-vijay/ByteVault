import apiClient from "./apiClient";

export const adminVendorService = {
  // Get pending vendors awaiting review
  getPendingVendors: async () => {
    try {
      const res = await apiClient.get("/users/admin/vendors/pending");
      return res?.data !== undefined ? res.data : res;
    } catch (err) {
      console.warn("[adminVendorService] Failed to fetch pending vendors:", err.message);
      throw err;
    }
  },

  // Get all vendors (optional status filter)
  getAllVendors: async (status = "") => {
    try {
      const query = status ? `?status=${status}` : "";
      const res = await apiClient.get(`/users/admin/vendors${query}`);
      return res?.data !== undefined ? res.data : res;
    } catch (err) {
      console.warn("[adminVendorService] Failed to fetch vendors:", err.message);
      throw err;
    }
  },

  // Get vendor by ID
  getVendorById: async (id) => {
    const res = await apiClient.get(`/users/admin/vendors/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  // Approve vendor
  approveVendor: async (id) => {
    const res = await apiClient.post(`/users/admin/vendors/${id}/approve`, {});
    return res?.data !== undefined ? res.data : res;
  },

  // Reject vendor
  rejectVendor: async (id, reason) => {
    const res = await apiClient.post(`/users/admin/vendors/${id}/reject`, { reason });
    return res?.data !== undefined ? res.data : res;
  },

  // Suspend vendor
  suspendVendor: async (id, reason) => {
    const res = await apiClient.post(`/users/admin/vendors/${id}/suspend`, { reason });
    return res?.data !== undefined ? res.data : res;
  },

  // Reactivate vendor
  reactivateVendor: async (id) => {
    const res = await apiClient.post(`/users/admin/vendors/${id}/reactivate`, {});
    return res?.data !== undefined ? res.data : res;
  }
};

export default adminVendorService;
