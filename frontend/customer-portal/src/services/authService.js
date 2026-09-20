// Authentication Service Wrapper calling apiClient
import apiClient from "./apiClient";

export const authService = {
  // Logs in user by sending credentials to API
  login: async (email, password) => {
    return apiClient.post("/auth/login", { email, password });
  },

  // Registers a new user with chosen role
  register: async ({ name, fullName, email, password, role, storeName, storeDescription, businessTaxId, payoutInfo, supportEmail }) => {
    return apiClient.post("/auth/register", { 
      fullName: fullName || name, 
      email, 
      password, 
      role,
      storeName,
      storeDescription,
      businessTaxId,
      payoutInfo,
      supportEmail
    });
  },


  // Refreshes the JWT session token
  refresh: async (refreshToken) => {
    return apiClient.post("/auth/refresh", { refreshToken });
  },

  // Logs out the user session
  logout: async (refreshToken) => {
    return apiClient.post("/auth/logout", { refreshToken });
  },

  // Request password reset dispatch
  requestPasswordReset: async (email) => {
    return apiClient.post("/auth/forgot-password", { email });
  },

  // Set new password with signature token
  resetPassword: async (token, newPassword) => {
    return apiClient.post("/auth/reset-password", { token, newPassword });
  },

  // Validates local session token or returns profile refresh
  verifySession: async () => {
    return apiClient.get("/users/me");
  }
};

export default authService;
