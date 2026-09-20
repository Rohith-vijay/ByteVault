// Order Service wrapper calling apiClient
import apiClient from "./apiClient";

export const orderService = {
  // Queries all user orders from API client
  getOrders: async () => {
    return apiClient.get("/orders");
  },

  // Submits a new order to the API client matching backend CreateOrderRequest
  createOrder: async (payloadOrUserId, items, shippingAddress, paymentDetails, shippingMethod, customerEmail, customerName) => {
    let payload;
    if (typeof payloadOrUserId === "object" && payloadOrUserId !== null) {
      const p = payloadOrUserId;
      payload = {
        items: (p.items || []).map(item => ({
          productId: item.productId || item.id,
          quantity: Number(item.quantity) || 1
        })),
        shippingAddress: typeof p.shippingAddress === "object" && p.shippingAddress !== null
          ? JSON.stringify(p.shippingAddress)
          : (p.shippingAddress || ""),
        customerEmail: p.customerEmail || "",
        customerName: p.customerName || ""
      };
    } else {
      payload = {
        items: (items || []).map(item => ({
          productId: item.productId || item.id,
          quantity: Number(item.quantity) || 1
        })),
        shippingAddress: typeof shippingAddress === "object" && shippingAddress !== null
          ? JSON.stringify(shippingAddress)
          : (shippingAddress || ""),
        customerEmail: customerEmail || "",
        customerName: customerName || ""
      };
    }
    return apiClient.post("/orders", payload);
  },

  // Queries all user orders from API client
  getOrdersByUser: async (_userId) => {
    return apiClient.get("/orders");
  },

  // Queries specific order details directly from backend
  getOrderById: async (orderId) => {
    return apiClient.get(`/orders/${orderId}`);
  }
};

export default orderService;

