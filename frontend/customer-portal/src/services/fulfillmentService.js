// Fulfillment Service Wrapper calling apiClient
import apiClient from "./apiClient";

export const fulfillmentService = {
  // Returns all digital product assets unlocked by user orders
  getEntitlements: async () => {
    return apiClient.get("/fulfillments/my-entitlements");
  },

  // Requests temporary secure download links for a specific product
  getDownload: async (productId) => {
    return apiClient.get(`/downloads/${productId}`);
  }
};

export default fulfillmentService;
