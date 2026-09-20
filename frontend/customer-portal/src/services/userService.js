// User Service Wrapper calling apiClient
import apiClient from "./apiClient";

export const userService = {
  // Returns all shipping addresses
  getAddresses: async (_userId) => {
    return apiClient.get("/users/addresses");
  },

  // Inserts a new shipping address
  addAddress: async (_userId, address) => {
    return apiClient.post("/users/addresses", address);
  },

  // Removes a shipping address
  deleteAddress: async (_userId, addressId) => {
    return apiClient.delete(`/users/addresses/${addressId}`);
  },

  // Updates profile configuration settings
  updateProfile: async (_userId, profileData) => {
    return apiClient.put("/users/profile", profileData);
  }
};

export default userService;
