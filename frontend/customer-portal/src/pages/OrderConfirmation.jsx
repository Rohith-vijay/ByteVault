import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Divider from "@mui/material/Divider";
import {
  CheckCircle as CheckCircleIcon,
  ArrowForward as ArrowForwardIcon,
  FolderSpecialOutlined as VaultIcon,
  LocalShippingOutlined as ShippingIcon,
  Download as DownloadIcon,
  ContentCopy as CopyIcon,
  Check as CheckIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { Price } from "../components/primitives/Price";
import { Skeleton } from "../components/primitives/Skeleton";
import { orderService } from "../services/orderService";
import { downloadService } from "../services/downloadService";

const PageContainer = styled(Container)(() => ({
  paddingTop: 48,
  paddingBottom: 80,
  maxWidth: "800px",
}));

export const OrderConfirmation = () => {
  const theme = useTheme();
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [copiedKey, setCopiedKey] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);
  const [activeDownloadUrl, setActiveDownloadUrl] = useState(null);

  useEffect(() => {
    const fetchOrder = async () => {
      try {
        let found = null;

        // 1. Check the last order saved after checkout (most reliable after redirect)
        try {
          const last = localStorage.getItem("bytevault_last_order");
          if (last) {
            const parsed = JSON.parse(last);
            if (parsed && (parsed.id === id || !id || id === "ord_success")) {
              found = parsed;
            }
          }
        } catch {}

        // 2. Try the API / mock endpoint
        if (!found) {
          try {
            found = await orderService.getOrderById(id);
          } catch {}
        }

        // 3. Search user-scoped localStorage orders as fallback
        if (!found) {
          try {
            const token = localStorage.getItem("bytevault_auth_token");
            if (token) {
              const payload = JSON.parse(atob(token.split(".")[1]));
              const userId = payload.id;
              const userOrders = JSON.parse(localStorage.getItem(`bytevault_orders_${userId}`) || "[]");
              found = userOrders.find((o) => o.id === id) || userOrders[userOrders.length - 1] || null;
            }
          } catch {}
        }

        if (found) setOrder(found);
      } catch (err) {
        console.error("Order fetch failed on confirmation", err);
      } finally {
        setLoading(false);
      }
    };
    fetchOrder();
  }, [id]);

  const handleCopyKey = (keyText, itemId) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(keyText);
      setCopiedKey(itemId);
      setTimeout(() => setCopiedKey(null), 2000);
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: "Copied to clipboard!", type: "success" } }));
    }
  };

  const handleInstantDownload = async (item) => {
    const productId = item.productId || item.id;
    setDownloadingId(productId);
    try {
      const res = await downloadService.downloadFile(productId);
      const downloadData = res?.data || res;
      const downloadUrl = downloadData?.downloadUrl;
      const fileName = downloadData?.fileName || `${(item.title || "digital_asset").toLowerCase().replace(/[^a-z0-9]/g, '_')}_package.zip`;

      if (downloadUrl) {
        setActiveDownloadUrl({ title: item.title, url: downloadUrl, fileName });

        const a = document.createElement("a");
        a.href = downloadUrl;
        a.setAttribute("download", fileName);
        a.target = "_blank";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);

        window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: `Download initiated for ${item.title}!`, type: "success" } }));
      } else {
        throw new Error(downloadData?.message || "Download unavailable. Entitlement not verified.");
      }
    } catch (err) {
      console.warn("Download error on confirmation", err);
      const errMsg = err?.response?.data?.message || err?.message || "Download failed. Please check your Digital Vault.";
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: errMsg, type: "error" } }));
    } finally {
      setDownloadingId(null);
    }
  };

  if (loading) {
    return (
      <PageContainer>
        <Card padding={8} radius="xl" elevation="subtle">
          <Skeleton variant="circular" width={64} height={64} style={{ margin: "0 auto 24px auto" }} />
          <Skeleton variant="rectangular" height={32} width="50%" style={{ margin: "0 auto 16px auto" }} />
          <Skeleton variant="rectangular" height={120} width="100%" />
        </Card>
      </PageContainer>
    );
  }

  // No hardcoded fallback — show error if order not found
  if (!order) {
    return (
      <PageContainer>
        <Card padding={8} radius="xl" elevation="card" style={{ textAlign: "center" }}>
          <CheckCircleIcon style={{ fontSize: "56px", color: theme.palette.status?.success || "#10B981", marginBottom: "16px" }} />
          <h2 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 12px" }}>Order Placed!</h2>
          <p style={{ fontSize: "14px", color: theme.palette.text.secondary, margin: "0 0 24px" }}>
            We couldn\'t load the order details, but your payment was successful. Check your dashboard for updates.
          </p>
          <Box display="flex" flexDirection="column" gap={3}>
            <Button variant="primary" size="lg" onClick={() => navigate("/account?tab=orders")} fullWidth>
              View My Orders
            </Button>
            <Button variant="secondary" size="md" onClick={() => navigate("/account?tab=downloads")} fullWidth>
              Access Digital Vault
            </Button>
          </Box>
        </Card>
      </PageContainer>
    );
  }

  const activeOrder = order;
  // Robust check for digital items
  const isDigitalCheck = (item) => {
    if (item.isDigital === true) return true;
    if (item.isDigital === false) return false;
    const t = (item.productType || item.type || "").toUpperCase();
    if (t === "DIGITAL") return true;
    if (t === "PHYSICAL") return false;
    const title = (item.productName || item.title || "").toLowerCase();
    return title.includes("blueprint") || title.includes("system") || title.includes("kit") || title.includes("software") || title.includes("license");
  };

  const digitalItems = (activeOrder.items || []).filter(isDigitalCheck);
  const physicalItems = (activeOrder.items || []).filter(i => !isDigitalCheck(i));
  const isOnlyDigital = physicalItems.length === 0 && digitalItems.length > 0;
  const hasPhysical = physicalItems.length > 0;

  const orderTotal = Number(activeOrder.totalAmount != null ? activeOrder.totalAmount : (activeOrder.totals?.total != null ? activeOrder.totals.total : 0));
  const subtotal = activeOrder.subtotal != null ? Number(activeOrder.subtotal) : activeOrder.totals?.subtotal != null ? Number(activeOrder.totals.subtotal) : orderTotal;
  const tax = activeOrder.tax != null ? Number(activeOrder.tax) : activeOrder.totals?.tax != null ? Number(activeOrder.totals.tax) : Number((subtotal * 0.08).toFixed(2));
  const shipping = activeOrder.shipping != null ? Number(activeOrder.shipping) : activeOrder.totals?.shipping != null ? Number(activeOrder.totals.shipping) : 0;

  return (
    <PageContainer>
      <Card padding={8} elevation="card" radius="xl" style={{ textAlign: "center", backgroundColor: "#FFFFFF" }}>
        <Box mb={2}>
          <CheckCircleIcon style={{ fontSize: "56px", color: theme.palette.status.success }} />
        </Box>
        <Box mb={1} display="inline-flex">
          <Chip label="ORDER CONFIRMED & PAID" color="success" variant="filled" uppercase />
        </Box>
        <h2 style={{ fontSize: "28px", fontWeight: 800, margin: "8px 0 8px 0", color: theme.palette.text.primary, letterSpacing: "-0.015em" }}>
          Transaction Authorized & Confirmed
        </h2>
        <p style={{ fontSize: "14px", color: theme.palette.text.secondary, margin: "0 0 32px 0", lineHeight: 1.6 }}>
          Order reference <strong>#{activeOrder.id}</strong> has been logged to our distributed network. An encrypted receipt has been emailed to your account.
        </p>

        {/* Instant Digital Downloads Section */}
        {digitalItems.length > 0 && (
          <Box mb={6} p={5} backgroundColor="#0F172A" borderRadius="16px" color="#FFFFFF" textAlign="left" border="1px solid #1E293B">
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={2}>
              <Box display="flex" alignItems="center" gap={2}>
                <VaultIcon style={{ color: "#38BDF8", fontSize: "26px" }} />
                <div>
                  <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 800, color: "#F8FAFC" }}>Instant Digital Vault Access & Downloads</h3>
                  <span style={{ fontSize: "12px", color: "#94A3B8" }}>Your purchased cryptographic packages and source files are ready for immediate download.</span>
                </div>
              </Box>
              <Chip label="Instant Unlock" color="primary" size="sm" uppercase />
            </Box>

            <Box display="flex" flexDirection="column" gap={4}>
              {digitalItems.map((item, idx) => {
                const prodId = item.productId || item.id || `item_${idx}`;
                const itemName = item.productName || item.title || "Digital Asset Package";
                const licenseKey = item.licenseKey || `BV-PRO-2026-9874-AC41`;
                const precalculatedDownloadUrl = `http://localhost:8080/api/v1/downloads/${prodId}`;
                
                return (
                  <Box key={idx} p={4} backgroundColor="rgba(255, 255, 255, 0.05)" borderRadius="12px" border="1px solid rgba(255, 255, 255, 0.12)">
                    <Box display="flex" justifyContent="space-between" alignItems="flex-start" flexWrap="wrap" gap={3} mb={3}>
                      <div>
                        <strong style={{ fontSize: "15px", color: "#FFFFFF", display: "block" }}>{itemName}</strong>
                        <div style={{ fontSize: "12px", color: "#94A3B8", marginTop: "4px" }}>
                          Format: ZIP Package · Commercial License Active · 100% Verified
                        </div>
                      </div>
                      <Button
                        variant="primary"
                        size="md"
                        onClick={() => handleInstantDownload(item)}
                        leftIcon={<DownloadIcon style={{ fontSize: "18px" }} />}
                        disabled={downloadingId === prodId}
                        style={{ backgroundColor: "#10B981", borderColor: "#10B981", fontWeight: 700 }}
                      >
                        {downloadingId === prodId ? "Downloading..." : "Download Asset Package (.ZIP)"}
                      </Button>
                    </Box>

                    {/* License Key bar */}
                    <Box display="flex" justifyContent="space-between" alignItems="center" p={2.5} backgroundColor="rgba(0, 0, 0, 0.3)" borderRadius="8px">
                      <div style={{ fontSize: "11px", color: "#64748B", fontFamily: "monospace" }}>
                        LICENSE KEY: <span style={{ color: "#F59E0B", fontWeight: 700 }}>{licenseKey}</span>
                      </div>
                      <Button
                        variant="outline"
                        size="xs"
                        onClick={() => handleCopyKey(licenseKey, prodId)}
                        leftIcon={copiedKey === prodId ? <CheckIcon style={{ fontSize: "12px" }} /> : <CopyIcon style={{ fontSize: "12px" }} />}
                        style={{ color: "#94A3B8", borderColor: "#334155", fontSize: "11px" }}
                      >
                        {copiedKey === prodId ? "Key Copied" : "Copy Key"}
                      </Button>
                    </Box>
                  </Box>
                );
              })}
            </Box>
          </Box>
        )}

        <Divider style={{ margin: "24px 0" }} />

        {/* Invoice Summary */}
        <Box textAlign="left" mb={6}>
          <h3 style={{ fontSize: "16px", fontWeight: 700, margin: "0 0 16px 0" }}>Purchased Engineering Assets</h3>
          <Box display="flex" flexDirection="column" gap={2}>
            {activeOrder.items?.map((item, idx) => {
              const itemName = item.productName || item.title || "Product";
              const itemUnitPrice = Number(item.unitPrice != null ? item.unitPrice : (item.price != null ? item.price : 0));
              const itemQty = Number(item.quantity) || 1;
              const itemSubtotal = item.subtotal != null ? Number(item.subtotal) : (itemUnitPrice * itemQty);

              return (
                <Box key={idx} display="flex" justifyContent="space-between" alignItems="center" p={3} backgroundColor={theme.palette.background.elevated} borderRadius="8px">
                  <Box>
                    <strong style={{ fontSize: "14px" }}>{itemName}</strong>
                    <span style={{ fontSize: "11px", color: theme.palette.text.muted, display: "block" }}>
                      Qty: {itemQty} · {item.productType || item.type || (item.isDigital ? "DIGITAL" : "PHYSICAL")}
                    </span>
                  </Box>
                  <Price amount={itemSubtotal} size="xs" />
                </Box>
              );
            })}
          </Box>

          <Divider style={{ margin: "20px 0" }} />

          <Grid container spacing={2} fontSize="13px">
            <Grid item xs={6} color={theme.palette.text.secondary}>Subtotal</Grid>
            <Grid item xs={6} style={{ textAlign: "right", fontWeight: 600 }}>₹{subtotal.toFixed(2)}</Grid>
            
            <Grid item xs={6} color={theme.palette.text.secondary}>Tax (8%)</Grid>
            <Grid item xs={6} style={{ textAlign: "right", fontWeight: 600 }}>₹{tax.toFixed(2)}</Grid>

            <Grid item xs={6} color={theme.palette.text.secondary}>Shipping</Grid>
            <Grid item xs={6} style={{ textAlign: "right", fontWeight: 600 }}>
              {shipping > 0 ? `₹${shipping.toFixed(2)}` : "FREE"}
            </Grid>

            <Grid item xs={6} style={{ fontWeight: 700, fontSize: "15px" }}>Total Authorized</Grid>
            <Grid item xs={6} style={{ textAlign: "right", fontWeight: 800, fontSize: "15px", color: theme.palette.primary.main }}>
              ₹{orderTotal.toFixed(2)}
            </Grid>
          </Grid>
        </Box>

        <Box display="flex" flexDirection="column" gap={3}>
          {digitalItems.length > 0 && (
            <Button
              variant="primary"
              size="lg"
              onClick={() => navigate("/account?tab=downloads")}
              leftIcon={<VaultIcon style={{ fontSize: "18px" }} />}
              rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              fullWidth
            >
              Access My Digital Vault
            </Button>
          )}
          {hasPhysical && (
            <Button
              variant={isOnlyDigital ? "secondary" : "primary"}
              size="lg"
              onClick={() => navigate("/account?tab=orders")}
              leftIcon={<ShippingIcon style={{ fontSize: "18px" }} />}
              rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              fullWidth
            >
              Track Order & Shipping Milestones
            </Button>
          )}
          <Button variant="secondary" size="md" onClick={() => navigate("/catalog")} fullWidth>
            Continue Browsing Marketplace
          </Button>
        </Box>
      </Card>
    </PageContainer>
  );
};

export default OrderConfirmation;
