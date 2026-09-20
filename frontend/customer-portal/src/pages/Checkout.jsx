import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import CircularProgress from "@mui/material/CircularProgress";
import {
  CreditCard as CreditCardIcon,
  LocalShippingOutlined as LocalShippingIcon,
  CheckCircle as CheckCircleIcon,
  LockOutlined as LockIcon,
  SecurityOutlined as SecurityIcon,
  ArrowForward as ArrowForwardIcon,
  ArrowBack as ArrowBackIcon,
  Smartphone as UpiIcon,
  Bolt as FlashIcon,
  Check as CheckIcon,
  Close as CloseIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Input } from "../components/primitives/Input";
import { Price } from "../components/primitives/Price";
import { Chip } from "../components/primitives/Chip";
import { useCart } from "../store/CartContext";
import { useAuth } from "../store/AuthContext";
import { orderService } from "../services/orderService";
import { paymentService } from "../services/paymentService";
import { cartService } from "../services/cartService";

const StepBarWrapper = styled("div")(({ theme }) => ({
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  maxWidth: "520px",
  margin: "0 auto 40px auto",
  position: "relative",

  "&::after": {
    content: '""',
    position: "absolute",
    height: "2px",
    backgroundColor: theme.palette.border.default,
    left: "30px",
    right: "30px",
    top: "16px",
    zIndex: 1,
  }
}));

const StepPill = styled("div", {
  shouldForwardProp: (prop) => prop !== "active" && prop !== "completed",
})(({ theme, active, completed }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  gap: "6px",
  zIndex: 2,

  "& .circle": {
    width: "32px",
    height: "32px",
    borderRadius: "50%",
    backgroundColor: completed 
      ? theme.palette.status.success 
      : active 
      ? theme.palette.primary.main 
      : theme.palette.background.elevated,
    color: completed || active ? "#FFFFFF" : theme.palette.text.secondary,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "13px",
    fontWeight: 700,
    border: `2px solid ${completed ? theme.palette.status.success : active ? theme.palette.primary.main : theme.palette.border.default}`,
    transition: "all 0.2s ease",
  },

  "& .step-title": {
    fontSize: "11px",
    fontWeight: active || completed ? 700 : 500,
    textTransform: "uppercase",
    letterSpacing: "0.04em",
    color: active ? theme.palette.primary.main : theme.palette.text.secondary,
  }
}));

const SummaryItemRow = styled("div")(({ theme }) => ({
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  padding: "8px 0",
  fontSize: "13px",
  borderBottom: `1px solid ${theme.palette.border.default}`,
  "&:last-child": {
    borderBottom: "none",
  }
}));

const PaymentOptionCard = styled("div", {
  shouldForwardProp: (prop) => prop !== "selected",
})(({ theme, selected }) => ({
  border: `2px solid ${selected ? theme.palette.primary.main : theme.palette.border.default}`,
  backgroundColor: selected ? `${theme.palette.primary.main}08` : "#FFFFFF",
  borderRadius: "12px",
  padding: "16px",
  cursor: "pointer",
  transition: "all 0.2s ease",
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  marginBottom: "12px",
  "&:hover": {
    borderColor: theme.palette.primary.main,
    backgroundColor: `${theme.palette.primary.main}05`,
  }
}));

export const Checkout = () => {
  const theme = useTheme();
  const navigate = useNavigate();

  const { cartItems, clearCart, removeItems } = useCart();
  const { user } = useAuth();

  // Read the items the user selected in Cart.jsx via sessionStorage
  const checkoutItems = React.useMemo(() => {
    try {
      const stored = sessionStorage.getItem("bytevault_checkout_items");
      if (stored) {
        const parsed = JSON.parse(stored);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch {}
    // Fallback: use all cart items if nothing was stored
    return cartItems;
  }, []); // eslint-disable-line

  // Use cartService to calculate totals for selected items
  const totals = React.useMemo(() => {
    return cartService.calculateTotals(checkoutItems);
  }, [checkoutItems]);

  const isOnlyDigital = !totals.hasPhysical;
  const [step, setStep] = React.useState(isOnlyDigital ? 2 : 1);
  React.useEffect(() => {
    setStep(isOnlyDigital ? 2 : 1);
  }, [isOnlyDigital]);

  const [loading, setLoading] = React.useState(false);
  const [checkoutError, setCheckoutError] = React.useState(null);

  // Address — start empty (no pre-filled stub addresses)
  const [name, setName] = React.useState("");
  const [street, setStreet] = React.useState("");
  const [city, setCity] = React.useState("");
  const [stateCode, setStateCode] = React.useState("");
  const [zip, setZip] = React.useState("");

  // Payment Selection
  const [paymentMode, setPaymentMode] = React.useState("RAZORPAY_SANDBOX");
  const [cardNumber, setCardNumber] = React.useState("4242 •••• •••• 4242");
  const [cardExpiry, setCardExpiry] = React.useState("12/28");
  const [cardCvc, setCardCvc] = React.useState("888");
  const [upiId, setUpiId] = React.useState("developer@razorpay");
  const [customKey, setCustomKey] = React.useState("rzp_test_sandbox_active");

  // Razorpay Sandbox Simulation Modal State
  const [simulatorOpen, setSimulatorOpen] = React.useState(false);
  const [simStep, setSimStep] = React.useState("IDLE");
  const [simLog, setSimLog] = React.useState([]);
  const [simOrderId, setSimOrderId] = React.useState("");
  const [simPaymentId, setSimPaymentId] = React.useState("");
  const [dbOrderId, setDbOrderId] = React.useState("");
  const [persistedOrder, setPersistedOrder] = React.useState(null);

  const addLog = (msg) => {
    setSimLog((prev) => [...prev, `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  const handleFillTestCard = () => {
    setCardNumber("4242 •••• •••• 4242");
    setCardExpiry("12/28");
    setCardCvc("888");
  };

  // Main Checkout Orchestrator - Creates Persisted Order in PostgreSQL FIRST
  const handleInitiateRazorpayCheckout = async () => {
    setLoading(true);
    setCheckoutError(null);
    setSimulatorOpen(true);
    setSimStep("CREATING_ORDER");
    setSimLog([]);
    addLog(`Creating persisted order in Order Service for amount: ₹${totals.total.toFixed(2)}`);

    try {
      // 1. Create Order on Backend Order Service FIRST
      const orderPayload = {
        items: checkoutItems.map((item) => ({
          productId: item.productId || item.id,
          quantity: Number(item.quantity) || 1
        })),
        shippingAddress: isOnlyDigital ? "" : `${name || user?.name || "Customer"}, ${street}, ${city}, ${stateCode} ${zip}, IN`,
        customerEmail: user?.email || "customer@bytevault.com",
        customerName: name || user?.name || "Customer"
      };

      const created = await orderService.createOrder(orderPayload);
      if (!created || !created.id) {
        throw new Error("Failed to create order: backend returned invalid order payload");
      }

      const realOrderId = created.id;
      setDbOrderId(realOrderId);
      setPersistedOrder(created);
      addLog(`Order persisted in PostgreSQL with UUID: ${realOrderId} (Status: ${created.status || "PENDING"})`);

      // 2. Create Payment Order Session via Payment Gateway with receipt = dbOrderId
      addLog(`Initiating payment gateway session for order: ${realOrderId}`);
      let paymentOrderRes;
      try {
        paymentOrderRes = await paymentService.createRazorpayOrder({
          amount: totals.total,
          currency: "INR",
          receipt: realOrderId
        });
      } catch (err) {
        if (import.meta.env.VITE_USE_MOCK_API === "false") {
          throw err;
        }
        // Fallback for standalone mock/development
        paymentOrderRes = {
          orderId: `order_mock_${Math.random().toString(36).substring(2, 9)}`,
          amount: Math.round(totals.total * 100),
          currency: "INR",
          key: customKey || "rzp_test_default"
        };
      }

      const generatedOrderId = paymentOrderRes?.orderId || `order_mock_${Date.now()}`;
      setSimOrderId(generatedOrderId);
      const generatedPaymentId = `pay_test_${Math.random().toString(36).substring(2, 10)}`;
      setSimPaymentId(generatedPaymentId);

      addLog(`Payment order session initialized: ${generatedOrderId}`);
      addLog(`Using Gateway Key: ${customKey || "rzp_test_sandbox_mode"}`);
      setSimStep("AWAITING_AUTH");
    } catch (err) {
      setSimStep("FAILED");
      addLog(`Order creation failed: ${err.message}`);
      setCheckoutError(err.message || "Failed to create order in Order Service.");
    } finally {
      setLoading(false);
    }
  };

  // Called when user clicks "Authorize Payment" in the Sandbox Simulation Modal
  const handleSimulatePaymentAuth = async () => {
    setSimStep("VERIFYING_SIGNATURE");
    addLog(`Payment authorized by test client: ${simPaymentId}`);
    addLog("Sending HMAC-SHA256 signature and dbOrderId to payment-service for verification...");

    try {
      const mockSignature = `sig_mock_${Math.random().toString(36).substring(2, 12)}_hmac_sha256`;

      // 2. Call backend verification endpoint with real dbOrderId
      const verifyRes = await paymentService.verifyRazorpayPayment({
        razorpayOrderId: simOrderId,
        razorpayPaymentId: simPaymentId,
        razorpaySignature: mockSignature,
        dbOrderId: dbOrderId,
        userId: user?.id,
        amount: totals.total,
        productIds: checkoutItems.map((i) => i.productId || i.id),
        customerEmail: user?.email || "customer@bytevault.com",
        customerName: name || user?.name || "Customer"
      });

      addLog(`Payment verified (Status: ${verifyRes?.status || "SUCCESS"}). Order transitioned to PAID in Order Service.`);
      setSimStep("SUCCESS");
      addLog(`Order #${dbOrderId} successfully confirmed and persisted!`);

      try {
        sessionStorage.removeItem("bytevault_checkout_items");
      } catch {}

      setTimeout(() => {
        setSimulatorOpen(false);
        const checkedOutIds = checkoutItems.map((i) => i.id || i.productId);
        if (removeItems) {
          removeItems(checkedOutIds);
        } else {
          clearCart();
        }
        navigate(`/order-confirmation/${dbOrderId}`);
      }, 1200);
    } catch (err) {
      setSimStep("FAILED");
      addLog(`Payment verification failed: ${err.message}`);
      setCheckoutError(err.message || "Payment verification failed.");
    }
  };

  if (checkoutItems.length === 0 && !simulatorOpen) {
    navigate("/cart");
    return null;
  }

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC", minHeight: "100vh" }}>
      <Container maxWidth="xxl">
        {/* Step Indicator */}
        <StepBarWrapper>
          {!isOnlyDigital && (
            <StepPill active={step === 1} completed={step > 1}>
              <div className="circle">{step > 1 ? "✓" : "1"}</div>
              <span className="step-title">Shipping</span>
            </StepPill>
          )}
          <StepPill active={step === 2} completed={step > 2}>
            <div className="circle">{step > 2 ? "✓" : isOnlyDigital ? "1" : "2"}</div>
            <span className="step-title">Payment</span>
          </StepPill>
          <StepPill active={step === 3} completed={false}>
            <div className="circle">{isOnlyDigital ? "2" : "3"}</div>
            <span className="step-title">Confirm</span>
          </StepPill>
        </StepBarWrapper>

        <Grid container spacing={8}>
          {/* Main Checkout Action Form */}
          <Grid item xs={12} lg={7}>
            {checkoutError && (
              <Box mb={4} p={4} backgroundColor="#FEF2F2" border="1px solid #EF4444" borderRadius="12px" color="#EF4444">
                <strong>Checkout Error:</strong> {checkoutError}
              </Box>
            )}

            {/* STEP 1: SHIPPING ADDRESS */}
            {step === 1 && !isOnlyDigital && (
              <Card padding={8} radius="xl" elevation="card">
                <Box display="flex" alignItems="center" gap={2} mb={4}>
                  <LocalShippingIcon style={{ color: theme.palette.primary.main, fontSize: "24px" }} />
                  <div>
                    <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700 }}>Shipping Address</h3>
                    <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>Where should we deliver your physical gear?</span>
                  </div>
                </Box>

                <Box display="flex" flexDirection="column" gap={4}>
                  <Input
                    label="Full Name"
                    placeholder="Enter your full name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    fullWidth
                    required
                  />
                  <Input
                    label="Street Address"
                    placeholder="742 Evergreen Terrace, Suite 400"
                    value={street}
                    onChange={(e) => setStreet(e.target.value)}
                    fullWidth
                    required
                  />
                  <Grid container spacing={4}>
                    <Grid item xs={12} sm={5}>
                      <Input
                        label="City"
                        placeholder="San Francisco"
                        value={city}
                        onChange={(e) => setCity(e.target.value)}
                        fullWidth
                        required
                      />
                    </Grid>
                    <Grid item xs={6} sm={3}>
                      <Input
                        label="State"
                        placeholder="CA"
                        value={stateCode}
                        onChange={(e) => setStateCode(e.target.value)}
                        fullWidth
                        required
                      />
                    </Grid>
                    <Grid item xs={6} sm={4}>
                      <Input
                        label="Postal Zip"
                        placeholder="94107"
                        value={zip}
                        onChange={(e) => setZip(e.target.value)}
                        fullWidth
                        required
                      />
                    </Grid>
                  </Grid>

                  <Box display="flex" justifyContent="flex-end" mt={4}>
                    <Button
                      variant="primary"
                      size="lg"
                      onClick={() => setStep(2)}
                      disabled={!name || !street || !city}
                      rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                    >
                      Continue to Payment
                    </Button>
                  </Box>
                </Box>
              </Card>
            )}

            {/* STEP 2: PAYMENT METHOD & RAZORPAY SANDBOX SETTINGS */}
            {step === 2 && (
              <Card padding={8} radius="xl" elevation="card">
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                  <Box display="flex" alignItems="center" gap={2}>
                    <FlashIcon style={{ color: "#3B82F6", fontSize: "28px" }} />
                    <div>
                      <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700 }}>Razorpay Sandbox & Payment Gateway</h3>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>Simulate instant digital authorization and test API keys</span>
                    </div>
                  </Box>
                  <Chip label="TEST MODE" color="info" size="sm" variant="filled" uppercase />
                </Box>

                {/* Razorpay Option Selection */}
                <PaymentOptionCard
                  selected={paymentMode === "RAZORPAY_SANDBOX"}
                  onClick={() => setPaymentMode("RAZORPAY_SANDBOX")}
                >
                  <Box display="flex" alignItems="center" gap={3}>
                    <Box p={2} backgroundColor="#EEF2FF" borderRadius="8px">
                      <FlashIcon style={{ color: "#4F46E5" }} />
                    </Box>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: "14px" }}>Razorpay Sandbox Simulation (Recommended)</div>
                      <div style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Live HMAC-SHA256 signature verification with simulated Test Cards & UPI
                      </div>
                    </div>
                  </Box>
                  {paymentMode === "RAZORPAY_SANDBOX" && <CheckCircleIcon style={{ color: "#4F46E5" }} />}
                </PaymentOptionCard>

                <PaymentOptionCard
                  selected={paymentMode === "TEST_CARD"}
                  onClick={() => setPaymentMode("TEST_CARD")}
                >
                  <Box display="flex" alignItems="center" gap={3}>
                    <Box p={2} backgroundColor="#F0FDF4" borderRadius="8px">
                      <CreditCardIcon style={{ color: "#16A34A" }} />
                    </Box>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: "14px" }}>Test Credit/Debit Card</div>
                      <div style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Standard Visa / Mastercard test sandbox card details
                      </div>
                    </div>
                  </Box>
                  {paymentMode === "TEST_CARD" && <CheckCircleIcon style={{ color: "#16A34A" }} />}
                </PaymentOptionCard>

                <PaymentOptionCard
                  selected={paymentMode === "TEST_UPI"}
                  onClick={() => setPaymentMode("TEST_UPI")}
                >
                  <Box display="flex" alignItems="center" gap={3}>
                    <Box p={2} backgroundColor="#FEF3C7" borderRadius="8px">
                      <UpiIcon style={{ color: "#D97706" }} />
                    </Box>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: "14px" }}>Simulated UPI / VPA</div>
                      <div style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Instant test virtual payment address authorization
                      </div>
                    </div>
                  </Box>
                  {paymentMode === "TEST_UPI" && <CheckCircleIcon style={{ color: "#D97706" }} />}
                </PaymentOptionCard>

                {/* Subform based on payment mode */}
                <Box mt={4} p={4} backgroundColor="#F8FAFC" borderRadius="12px" border="1px solid #E2E8F0">
                  {paymentMode === "RAZORPAY_SANDBOX" && (
                    <Box display="flex" flexDirection="column" gap={3}>
                      <Input
                        label="Razorpay Test API Key"
                        placeholder="rzp_test_..."
                        value={customKey}
                        onChange={(e) => setCustomKey(e.target.value)}
                        fullWidth
                        helperText="Default sandbox key active. Enter your Razorpay Dashboard Key ID if desired."
                      />
                      <Box display="flex" gap={2} alignItems="center" fontSize="12px" color="#64748B">
                        <SecurityIcon style={{ fontSize: "16px", color: "#3B82F6" }} />
                        <span>Simulates Razorpay order creation, client callback, and HMAC signature verification.</span>
                      </Box>
                    </Box>
                  )}

                  {paymentMode === "TEST_CARD" && (
                    <Box display="flex" flexDirection="column" gap={4}>
                      <Box display="flex" justifyContent="space-between" alignItems="center">
                        <span style={{ fontSize: "13px", fontWeight: 600 }}>Test Credentials</span>
                        <Button variant="outline" size="sm" onClick={handleFillTestCard}>
                          Autofill Test Card
                        </Button>
                      </Box>
                      <Input
                        label="Card Number"
                        value={cardNumber}
                        onChange={(e) => setCardNumber(e.target.value)}
                        fullWidth
                        required
                      />
                      <Grid container spacing={4}>
                        <Grid item xs={6}>
                          <Input
                            label="Expiry Date"
                            value={cardExpiry}
                            onChange={(e) => setCardExpiry(e.target.value)}
                            fullWidth
                            required
                          />
                        </Grid>
                        <Grid item xs={6}>
                          <Input
                            label="Security CVC"
                            value={cardCvc}
                            onChange={(e) => setCardCvc(e.target.value)}
                            fullWidth
                            required
                          />
                        </Grid>
                      </Grid>
                    </Box>
                  )}

                  {paymentMode === "TEST_UPI" && (
                    <Box display="flex" flexDirection="column" gap={3}>
                      <Input
                        label="Test UPI VPA ID"
                        placeholder="developer@razorpay"
                        value={upiId}
                        onChange={(e) => setUpiId(e.target.value)}
                        fullWidth
                      />
                      <span style={{ fontSize: "12px", color: "#64748B" }}>Use success@razorpay for instant successful authorization.</span>
                    </Box>
                  )}
                </Box>

                <Box display="flex" justifyContent="space-between" alignItems="center" mt={6}>
                  {!isOnlyDigital && (
                    <Button variant="ghost" size="sm" onClick={() => setStep(1)} leftIcon={<ArrowBackIcon style={{ fontSize: "15px" }} />}>
                      Back to Shipping
                    </Button>
                  )}
                  <Button
                    variant="primary"
                    size="lg"
                    style={{ marginLeft: "auto" }}
                    onClick={() => setStep(3)}
                    rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                  >
                    Review Order
                  </Button>
                </Box>
              </Card>
            )}

            {/* STEP 3: REVIEW & PLACE ORDER VIA RAZORPAY */}
            {step === 3 && (
              <Card padding={8} radius="xl" elevation="card">
                <Box display="flex" alignItems="center" gap={2} mb={6}>
                  <SecurityIcon style={{ color: theme.palette.status.success, fontSize: "28px" }} />
                  <div>
                    <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700 }}>Final Order Confirmation</h3>
                    <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>Verify authorization coordinates before placing order</span>
                  </div>
                </Box>

                {isOnlyDigital ? (
                  <Box mb={4} p={4} backgroundColor={theme.palette.background.elevated} borderRadius="10px">
                    <div style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted, marginBottom: "4px" }}>
                      Fulfillment Method
                    </div>
                    <div style={{ fontSize: "14px", fontWeight: 600, color: "#10B981" }}>
                      Instant Digital Vault Delivery · No Physical Shipping Required
                    </div>
                  </Box>
                ) : (
                  <Box mb={4} p={4} backgroundColor={theme.palette.background.elevated} borderRadius="10px">
                    <div style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted, marginBottom: "4px" }}>
                      Shipping Destination
                    </div>
                    <div style={{ fontSize: "14px", fontWeight: 600 }}>{name || user?.name || "Customer"}</div>
                    <div style={{ fontSize: "13px", color: theme.palette.text.secondary }}>
                      {street ? `${street}, ${city}, ${stateCode} ${zip}` : "No address entered"}
                    </div>
                  </Box>
                )}

                <Box mb={6} p={4} backgroundColor={theme.palette.background.elevated} borderRadius="10px">
                  <div style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted, marginBottom: "4px" }}>
                    Payment Mode
                  </div>
                  <div style={{ fontSize: "14px", fontWeight: 600, display: "flex", alignItems: "center", gap: "8px" }}>
                    <FlashIcon style={{ color: "#3B82F6", fontSize: "18px" }} />
                    Razorpay Gateway (Sandbox Simulation)
                  </div>
                  <div style={{ fontSize: "13px", color: theme.palette.text.secondary, marginTop: "4px" }}>
                    Key: <code style={{ backgroundColor: "#E2E8F0", padding: "2px 6px", borderRadius: "4px" }}>{customKey}</code> · Instant signature entitlement upon confirmation
                  </div>
                </Box>

                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Button variant="ghost" size="sm" onClick={() => setStep(2)} leftIcon={<ArrowBackIcon style={{ fontSize: "15px" }} />}>
                    Back to Payment
                  </Button>
                  <Button
                    variant="primary"
                    size="lg"
                    state={loading ? "loading" : "default"}
                    onClick={handleInitiateRazorpayCheckout}
                    disabled={loading}
                    rightIcon={<CheckCircleIcon style={{ fontSize: "18px" }} />}
                  >
                    Place Order &amp; Pay · ₹{totals.total.toFixed(2)}
                  </Button>
                </Box>
              </Card>
            )}
          </Grid>

          {/* Right Column: Order Summary Breakdown */}
          <Grid item xs={12} lg={5}>
            <Card padding={8} radius="xl" elevation="subtle">
              <h3 style={{ fontSize: "16px", fontWeight: 700, margin: "0 0 16px 0" }}>
                Order Items ({checkoutItems.length})
              </h3>

              <Box display="flex" flexDirection="column" gap={1} mb={4}>
                {checkoutItems.map((item) => (
                  <SummaryItemRow key={item.id}>
                    <Box display="flex" alignItems="center" gap={2} maxWidth="70%">
                      <img src={item.image} alt={item.title} style={{ width: "36px", height: "36px", objectFit: "cover", borderRadius: "6px" }} />
                      <div>
                        <div style={{ fontSize: "13px", fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {item.title}
                        </div>
                        <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                          Qty: {item.quantity} · {item.type}
                        </div>
                      </div>
                    </Box>
                    <span style={{ fontWeight: 600 }}>₹{(item.price * item.quantity).toFixed(2)}</span>
                  </SummaryItemRow>
                ))}
              </Box>

              <Divider style={{ margin: "16px 0" }} />

              <Box display="flex" justifyContent="space-between" fontSize="13px" mb={1}>
                <span style={{ color: theme.palette.text.secondary }}>Subtotal</span>
                <span>₹{totals.subtotal.toFixed(2)}</span>
              </Box>
              <Box display="flex" justifyContent="space-between" fontSize="13px" mb={1}>
                <span style={{ color: theme.palette.text.secondary }}>Shipping</span>
                <span>{totals.shipping === 0 ? "FREE" : `₹${totals.shipping.toFixed(2)}`}</span>
              </Box>
              {totals.discount > 0 && (
                <Box display="flex" justifyContent="space-between" fontSize="13px" mb={1} color={theme.palette.status.success}>
                  <span>Discount</span>
                  <span>-₹{totals.discount.toFixed(2)}</span>
                </Box>
              )}
              <Box display="flex" justifyContent="space-between" fontSize="13px" mb={2}>
                <span style={{ color: theme.palette.text.secondary }}>Tax</span>
                <span>₹{totals.tax.toFixed(2)}</span>
              </Box>

              <Divider style={{ margin: "16px 0" }} />

              <Box display="flex" justifyContent="space-between" alignItems="baseline" mb={4}>
                <span style={{ fontSize: "16px", fontWeight: 700 }}>Total Due</span>
                <Price amount={totals.total} size="md" />
              </Box>

              <Box p={3} backgroundColor={theme.palette.background.elevated} borderRadius="8px" fontSize="12px" color={theme.palette.text.secondary} display="flex" alignItems="center" gap={1.5}>
                <LockIcon style={{ fontSize: "16px", color: theme.palette.accent.main }} />
                <span>100% Encrypted Financial Transaction</span>
              </Box>
            </Card>
          </Grid>
        </Grid>
      </Container>

      {/* RAZORPAY SANDBOX SIMULATION MODAL */}
      <Dialog
        open={simulatorOpen}
        onClose={() => simStep !== "VERIFYING_SIGNATURE" && setSimulatorOpen(false)}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          style: {
            borderRadius: "16px",
            overflow: "hidden",
            boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)"
          }
        }}
      >
        {/* Razorpay Header */}
        <Box p={4} backgroundColor="#0C2340" color="#FFFFFF" display="flex" justifyContent="space-between" alignItems="center">
          <Box display="flex" alignItems="center" gap={2}>
            <Box p={1} backgroundColor="#2563EB" borderRadius="6px">
              <FlashIcon style={{ color: "#FFFFFF", fontSize: "20px" }} />
            </Box>
            <div>
              <div style={{ fontSize: "16px", fontWeight: 800, letterSpacing: "-0.01em" }}>Razorpay Sandbox Simulator</div>
              <div style={{ fontSize: "11px", color: "#93C5FD" }}>Test API Integration · Secure Payment Checkout</div>
            </div>
          </Box>
          <Chip label="SANDBOX" size="sm" style={{ backgroundColor: "#1E3A8A", color: "#93C5FD", fontWeight: 700 }} />
        </Box>

        <DialogContent style={{ padding: "24px", backgroundColor: "#F8FAFC" }}>
          {/* Amount Badge */}
          <Box p={3} mb={4} backgroundColor="#EFF6FF" borderRadius="12px" border="1px solid #BFDBFE" display="flex" justifyContent="space-between" alignItems="center">
            <div>
              <div style={{ fontSize: "12px", color: "#1E40AF", fontWeight: 600 }}>Total Transaction Amount</div>
              <div style={{ fontSize: "22px", fontWeight: 800, color: "#1E3A8A" }}>₹{totals.total.toFixed(2)} INR</div>
            </div>
            <div style={{ textAlign: "right", fontSize: "11px", color: "#64748B" }}>
              <div>Order: <strong>{simOrderId || "Initializing..."}</strong></div>
              <div>Key: <code>{customKey || "rzp_test_default"}</code></div>
            </div>
          </Box>

          {/* Simulation Stages */}
          {simStep === "CREATING_ORDER" && (
            <Box textAlign="center" py={6}>
              <CircularProgress size={40} style={{ color: "#3B82F6", marginBottom: "16px" }} />
              <div style={{ fontSize: "15px", fontWeight: 600 }}>Creating Razorpay Order...</div>
              <div style={{ fontSize: "12px", color: "#64748B", marginTop: "4px" }}>Communicating with Payment Microservice / Gateway</div>
            </Box>
          )}

          {simStep === "AWAITING_AUTH" && (
            <Box>
              <div style={{ fontSize: "14px", fontWeight: 700, marginBottom: "12px" }}>Choose Sandbox Authorization Outcome:</div>
              <Box display="flex" flexDirection="column" gap={3}>
                <Button
                  variant="primary"
                  size="lg"
                  fullWidth
                  onClick={handleSimulatePaymentAuth}
                  leftIcon={<CheckIcon />}
                  style={{ backgroundColor: "#10B981", borderColor: "#10B981" }}
                >
                  Simulate Payment Success (200 OK)
                </Button>
                <Button
                  variant="outline"
                  size="md"
                  fullWidth
                  onClick={() => {
                    setSimStep("FAILED");
                    addLog("Payment failed: Customer cancelled / bank timeout simulated.");
                    setCheckoutError("Simulated payment transaction was declined.");
                  }}
                  leftIcon={<CloseIcon />}
                  style={{ color: "#EF4444", borderColor: "#FCA5A5" }}
                >
                  Simulate Card Decline / Failure
                </Button>
              </Box>
            </Box>
          )}

          {simStep === "VERIFYING_SIGNATURE" && (
            <Box textAlign="center" py={6}>
              <CircularProgress size={40} style={{ color: "#10B981", marginBottom: "16px" }} />
              <div style={{ fontSize: "15px", fontWeight: 600 }}>Verifying Cryptographic HMAC Signature...</div>
              <div style={{ fontSize: "12px", color: "#64748B", marginTop: "4px" }}>Synchronizing with order-service & granting digital entitlements</div>
            </Box>
          )}

          {simStep === "SUCCESS" && (
            <Box textAlign="center" py={6}>
              <CheckCircleIcon style={{ fontSize: "56px", color: "#10B981", marginBottom: "12px" }} />
              <div style={{ fontSize: "18px", fontWeight: 800, color: "#065F46" }}>Payment Authorized & Verified!</div>
              <div style={{ fontSize: "13px", color: "#047857", marginTop: "4px" }}>Redirecting to order confirmation...</div>
            </Box>
          )}

          {simStep === "FAILED" && (
            <Box textAlign="center" py={4}>
              <Box p={3} mb={3} backgroundColor="#FEF2F2" borderRadius="8px" border="1px solid #F87171" color="#B91C1C" fontSize="13px">
                Payment simulation terminated or failed. Check console log below.
              </Box>
              <Button variant="outline" size="sm" onClick={() => setSimulatorOpen(false)}>
                Close Simulator & Retry
              </Button>
            </Box>
          )}

          {/* Real-time Simulator Audit Logs */}
          <Box mt={4} p={3} backgroundColor="#0F172A" borderRadius="8px" color="#38BDF8" fontFamily="monospace" fontSize="11px" maxHeight="140px" overflow="auto">
            <div style={{ color: "#94A3B8", fontWeight: 700, marginBottom: "4px" }}>&gt; RAZORPAY SANDBOX EXECUTION LOG:</div>
            {simLog.map((logLine, idx) => (
              <div key={idx} style={{ lineHeight: 1.5 }}>{logLine}</div>
            ))}
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default Checkout;
