// Payment Service Wrapper calling apiClient
import apiClient from "./apiClient";

export const paymentService = {
  // Initiates a Razorpay payment order (Sandbox or Live)
  createRazorpayOrder: async ({ amount, currency = "INR", receipt = null }) => {
    return apiClient.post("/payments", {
      amount,
      currency,
      receipt: receipt || `rcpt_${Date.now()}`
    });
  },

  // Verifies HMAC signature / mock signature of completed Razorpay payment
  verifyRazorpayPayment: async ({
    razorpayOrderId,
    razorpayPaymentId,
    razorpaySignature,
    dbOrderId = null,
    userId = null,
    amount = null,
    productIds = [],
    customerEmail = "",
    customerName = ""
  }) => {
    return apiClient.post("/payments/verify", {
      razorpayOrderId,
      razorpayPaymentId,
      razorpaySignature,
      dbOrderId,
      userId,
      amount,
      productIds,
      customerEmail,
      customerName
    });
  },

  // Legacy/Direct card mock fallback
  processPayment: async (amount, cardDetails) => {
    const response = await apiClient.post("/payments", {
      amount,
      cardNumber: cardDetails.number,
      cardExpiry: cardDetails.expiry,
      cardCvc: cardDetails.cvc,
      cardName: cardDetails.name
    });

    return {
      success: true,
      transactionId: response.transactionId || `txn_${Date.now()}`,
      amount,
      timestamp: new Date().toISOString(),
      paymentDetails: {
        type: cardDetails.number?.startsWith("4") ? "visa" : "mastercard",
        last4: cardDetails.number?.replace(/\s/g, "").slice(-4) || "4242"
      }
    };
  },

  // Verifies the status of a checkout payment transaction
  verifyPayment: async (transactionId) => {
    return apiClient.post("/payments/verify", { transactionId });
  }
};

export default paymentService;

