// Support Service - Handles support tickets and replies for Customer, Vendor, and Admin
import apiClient from "./apiClient";

export const supportService = {
  getMyTickets: async () => {
    return apiClient.get("/support/tickets/my");
  },

  getTicketById: async (id) => {
    return apiClient.get(`/support/tickets/${id}`);
  },

  createTicket: async (ticketData) => {
    return apiClient.post("/support/tickets", ticketData);
  },

  addMessage: async (ticketId, messageData) => {
    return apiClient.post(`/support/tickets/${ticketId}/messages`, messageData);
  },

  getAllTicketsAdmin: async () => {
    return apiClient.get("/support/tickets/admin");
  },

  updateTicketStatus: async (ticketId, status) => {
    return apiClient.put(`/support/tickets/${ticketId}/status?status=${status}`);
  }
};

export default supportService;
