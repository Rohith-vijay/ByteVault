import React, { useState, useEffect } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import {
  FolderSpecialOutlined as VaultIcon,
  ReceiptLongOutlined as OrdersIcon,
  PersonOutlineOutlined as PersonIcon,
  HomeOutlined as AddressIcon,
  CloudDownloadOutlined as DownloadIcon,
  CheckCircle as CheckIcon,
  Bolt as BoltIcon,
  KeyOutlined as KeyIcon,
  ContentCopy as CopyIcon,
  DashboardOutlined as OverviewIcon,
  HeadsetMicOutlined as SupportIcon,
  Add as AddIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { IconButton } from "../components/primitives/IconButton";
import { Chip } from "../components/primitives/Chip";
import { Skeleton } from "../components/primitives/Skeleton";
import { Input } from "../components/primitives/Input";
import { useAuth } from "../store/AuthContext";
import { orderService } from "../services/orderService";
import { downloadService } from "../services/downloadService";
import { supportService } from "../services/supportService";

const AccountLayout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "260px 1fr",
  gap: theme.spacing(8),
  paddingTop: theme.spacing(8),
  paddingBottom: theme.spacing(20),

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
  },
}));

const NavSidebar = styled("aside")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  gap: "6px",
  backgroundColor: "#070B16",
  padding: theme.spacing(4),
  borderRadius: "16px",
  border: "1px solid rgba(255, 255, 255, 0.1)",
  color: "#FFFFFF",
  height: "fit-content",
}));

const NavTabButton = styled("button", {
  shouldForwardProp: (prop) => prop !== "active",
})(({ active }) => ({
  display: "flex",
  alignItems: "center",
  gap: "12px",
  padding: "12px 16px",
  borderRadius: "10px",
  border: "none",
  cursor: "pointer",
  fontSize: "13px",
  fontWeight: 600,
  textAlign: "left",
  width: "100%",
  backgroundColor: active ? "rgba(124, 58, 237, 0.2)" : "transparent",
  color: active ? "#FFFFFF" : "rgba(255, 255, 255, 0.65)",
  borderLeft: active ? "3px solid #7C3AED" : "3px solid transparent",
  transition: "all 0.15s ease",

  "&:hover": {
    backgroundColor: active ? "rgba(124, 58, 237, 0.25)" : "rgba(255, 255, 255, 0.06)",
    color: "#FFFFFF",
  }
}));

const VaultCanvas = styled("div")(({ theme }) => ({
  backgroundColor: "#070B16",
  color: "#FFFFFF",
  borderRadius: "20px",
  padding: theme.spacing(8),
  border: "1px solid rgba(255, 255, 255, 0.12)",
  position: "relative",
  overflow: "hidden",
}));

const VaultItemCard = styled("div")(({ theme }) => ({
  backgroundColor: "#0F172A",
  borderRadius: "14px",
  border: "1px solid rgba(255, 255, 255, 0.1)",
  padding: theme.spacing(6),
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(4),
  transition: "border-color 0.2s ease, transform 0.2s ease",

  "&:hover": {
    borderColor: "rgba(124, 58, 237, 0.4)",
    transform: "translateY(-2px)",
  }
}));

const OrderCard = styled(Card)(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(4),
  padding: theme.spacing(6),
  borderRadius: "16px",
  backgroundColor: "#FFFFFF",
  border: `1px solid ${theme.palette.border.default}`,
}));

const TimelineTrack = styled("div")(({ theme }) => ({
  display: "flex",
  justifyContent: "space-between",
  position: "relative",
  padding: "16px 0",
  "&::after": {
    content: '""',
    position: "absolute",
    height: "2px",
    backgroundColor: theme.palette.border.default,
    left: "10%",
    right: "10%",
    top: "23px",
    zIndex: 1,
  }
}));

const TimelineStep = styled("div", {
  shouldForwardProp: (prop) => prop !== "active" && prop !== "completed",
})(({ theme, active, completed }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  gap: "6px",
  zIndex: 2,
  width: "25%",

  "& .dot": {
    width: "16px",
    height: "16px",
    borderRadius: "50%",
    backgroundColor: completed 
      ? theme.palette.status.success 
      : active 
      ? theme.palette.primary.main 
      : theme.palette.background.elevated,
    border: `2px solid #FFFFFF`,
    boxShadow: "0 1px 4px rgba(0,0,0,0.1)",
  },

  "& .step-label": {
    fontSize: "11px",
    fontWeight: active || completed ? 700 : 500,
    color: active ? theme.palette.primary.main : theme.palette.text.secondary,
    textTransform: "uppercase",
    letterSpacing: "0.04em",
  }
}));

export const Account = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();

  const currentTab = searchParams.get("tab") || "overview";

  const [orders, setOrders] = useState([]);
  const [downloads, setDownloads] = useState([]);
  const [supportTickets, setSupportTickets] = useState([]);
  const [addresses, setAddresses] = useState([]);
  const [loadingOrders, setLoadingOrders] = useState(true);
  const [loadingDownloads, setLoadingDownloads] = useState(true);
  const [loadingTickets, setLoadingTickets] = useState(true);
  const [loadingAddresses, setLoadingAddresses] = useState(true);
  const [copiedKey, setCopiedKey] = useState(null);

  // Support ticket state
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [isCreatingTicket, setIsCreatingTicket] = useState(false);
  const [newTicket, setNewTicket] = useState({ subject: "", description: "", category: "GENERAL_INQUIRY", priority: "MEDIUM" });
  const [ticketReply, setTicketReply] = useState("");

  const fetchTickets = async () => {
    setLoadingTickets(true);
    try {
      const list = await supportService.getMyTickets();
      setSupportTickets(list || []);
    } catch (e) {
      console.warn("Failed fetching support tickets", e);
    } finally {
      setLoadingTickets(false);
    }
  };

  useEffect(() => {
    const fetchOrders = async () => {
      setLoadingOrders(true);
      try {
        const list = await orderService.getOrders();
        const raw = Array.isArray(list) ? list : (list?.content || list?.data || []);
        setOrders(Array.isArray(raw) ? raw : []);
      } catch (e) {
        console.warn("Failed fetching orders from backend:", e);
        setOrders([]);
      } finally {
        setLoadingOrders(false);
      }
    };

    const fetchDownloads = async () => {
      setLoadingDownloads(true);
      try {
        const dls = await downloadService.getDownloads();
        const raw = Array.isArray(dls) ? dls : (dls?.data || []);
        const mapped = (Array.isArray(raw) ? raw : []).map(d => ({
          ...d,
          id: d.id || d.productId,
          productId: d.productId || d.id,
          title: d.title || d.productName || "Digital Asset Package",
          version: d.version || "1.0.0",
          fileSize: d.fileSize || "10.4 MB",
          purchaseDate: d.purchaseDate || (d.grantedAt ? new Date(d.grantedAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" }) : "Active"),
          licenseKey: d.licenseKey || "BV-PRO-2026-9874-AC41"
        }));
        setDownloads(mapped);
      } catch (e) {
        console.warn("Failed fetching downloads", e);
        setDownloads([]);
      } finally {
        setLoadingDownloads(false);
      }
    };

    const fetchAddresses = async () => {
      setLoadingAddresses(true);
      try {
        // Always use user-scoped key for proper isolation
        if (user?.id) {
          const addrKey = `bytevault_addresses_${user.id}`;
          const stored = JSON.parse(localStorage.getItem(addrKey) || "[]");
          setAddresses(stored);
        } else {
          setAddresses([]);
        }
      } catch (e) {
        console.warn("Failed fetching addresses", e);
        setAddresses([]);
      } finally {
        setLoadingAddresses(false);
      }
    };

    fetchOrders();
    fetchDownloads();
    fetchTickets();
    fetchAddresses();
  }, [user?.id]); // Re-fetch when user changes (login/logout)

  const handleCreateTicketSubmit = async (e) => {
    e.preventDefault();
    try {
      const created = await supportService.createTicket({
        ...newTicket,
        userEmail: user?.email || "customer@bytevault.com"
      });
      setSupportTickets([created, ...supportTickets]);
      setIsCreatingTicket(false);
      setNewTicket({ subject: "", description: "", category: "GENERAL_INQUIRY", priority: "MEDIUM" });
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: "Support ticket opened successfully!", type: "success" } }));
    } catch (err) {
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: err.message || "Failed to create ticket", type: "error" } }));
    }
  };

  const handleSendTicketReply = async (ticketId) => {
    if (!ticketReply.trim()) return;
    try {
      const res = await supportService.addMessage(ticketId, {
        senderEmail: user?.email || "customer@bytevault.com",
        message: ticketReply,
        isAdminReply: false
      });
      const updatedTickets = supportTickets.map(t => {
        if (t.id === ticketId) {
          return { ...t, messages: [...(t.messages || []), res] };
        }
        return t;
      });
      setSupportTickets(updatedTickets);
      if (selectedTicket && selectedTicket.id === ticketId) {
        setSelectedTicket({ ...selectedTicket, messages: [...(selectedTicket.messages || []), res] });
      }
      setTicketReply("");
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: "Reply sent to support agent!", type: "success" } }));
    } catch (err) {
      window.dispatchEvent(new CustomEvent("bytevault_toast", { detail: { message: err.message || "Failed to send message", type: "error" } }));
    }
  };

  const handleTabSelect = (tabName) => {
    setSearchParams({ tab: tabName });
  };

  const handleCopyKey = (keyText, id) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(keyText);
      setCopiedKey(id);
      setTimeout(() => setCopiedKey(null), 2000);
    }
  };

  const handleDownloadFile = async (dl) => {
    try {
      const prodId = dl.productId || dl.id;
      const res = await downloadService.downloadFile(prodId);
      if (res?.downloadUrl) {
        const a = document.createElement("a");
        a.href = res.downloadUrl;
        a.setAttribute("download", res?.fileName || `${(dl.title || 'digital_asset').toLowerCase().replace(/[^a-z0-9]/g, '_')}_package.zip`);
        a.target = "_blank";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);

        window.dispatchEvent(
          new CustomEvent("bytevault_toast", {
            detail: { message: `Downloaded signed release package for ${dl.title || 'product'}!`, type: "success" }
          })
        );
      } else {
        throw new Error("Download URL could not be generated by fulfillment service.");
      }
    } catch (e) {
      console.warn("Download error", e);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Could not generate download release.", type: "error" }
        })
      );
    }
  };

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC" }}>
      <Container maxWidth="xxl">
        <Box mb={6} display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
          <div>
            <Box display="flex" alignItems="center" gap={2} mb={1}>
              <h1 style={{ fontSize: "28px", fontWeight: 800, margin: 0, color: theme.palette.text.primary }}>
                {user?.role === "VENDOR" ? "Vendor Management Console" : user?.role === "ADMIN" ? "Administrator Console" : "Customer Enterprise Dashboard"}
              </h1>
              <Chip 
                label={user?.role === "VENDOR" ? "VENDOR" : user?.role === "ADMIN" ? "ADMIN" : "CUSTOMER"} 
                color={user?.role === "VENDOR" ? "primary" : "secondary"} 
                size="xs" 
                uppercase 
              />
            </Box>
            <span style={{ fontSize: "14px", color: theme.palette.text.secondary }}>
              Signed in as <strong>{user?.email || "customer@bytevault.com"}</strong>
            </span>
          </div>
          <Box display="flex" gap={2}>
            <Button variant="secondary" size="sm" onClick={() => navigate("/catalog")}>
              Browse Marketplace
            </Button>
          </Box>
        </Box>

        <AccountLayout>
          {/* Dark Navigation Sidebar */}
          <NavSidebar>
            <Box p={2} mb={2}>
              <div style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.4)", textTransform: "uppercase", letterSpacing: "0.08em" }}>
                MANAGEMENT CONSOLE
              </div>
            </Box>

            <NavTabButton active={currentTab === "overview"} onClick={() => handleTabSelect("overview")}>
              <OverviewIcon style={{ fontSize: "18px" }} />
              <span>Console Overview</span>
            </NavTabButton>

            {user?.role === "VENDOR" && (
              <NavTabButton active={currentTab === "publisher"} onClick={() => handleTabSelect("publisher")}>
                <OverviewIcon style={{ fontSize: "18px", color: "#A78BFA" }} />
                <span>Vendor Studio</span>
              </NavTabButton>
            )}

            {user?.role === "ADMIN" && (
              <NavTabButton active={false} onClick={() => navigate("/admin")}>
                <OverviewIcon style={{ fontSize: "18px", color: "#3B82F6" }} />
                <span>Admin Console ↗</span>
              </NavTabButton>
            )}

            <NavTabButton active={currentTab === "downloads"} onClick={() => handleTabSelect("downloads")}>
              <VaultIcon style={{ fontSize: "18px", color: "#60A5FA" }} />
              <span>My Digital Vault ({(downloads || []).length})</span>
            </NavTabButton>

            <NavTabButton active={currentTab === "orders"} onClick={() => handleTabSelect("orders")}>
              <OrdersIcon style={{ fontSize: "18px" }} />
              <span>Orders ({(orders || []).length})</span>
            </NavTabButton>

            <NavTabButton active={currentTab === "support"} onClick={() => handleTabSelect("support")}>
              <SupportIcon style={{ fontSize: "18px", color: "#A78BFA" }} />
              <span>Support Tickets ({(supportTickets || []).length})</span>
            </NavTabButton>

            <NavTabButton active={currentTab === "profile"} onClick={() => handleTabSelect("profile")}>
              <PersonIcon style={{ fontSize: "18px" }} />
              <span>Security & Profile</span>
            </NavTabButton>

            <NavTabButton active={currentTab === "addresses"} onClick={() => handleTabSelect("addresses")}>
              <AddressIcon style={{ fontSize: "18px" }} />
              <span>Saved Addresses</span>
            </NavTabButton>
          </NavSidebar>

          {/* Main Content Pane */}
          <div>
            {/* 1. OVERVIEW TAB */}
            {currentTab === "overview" && (
              <Box display="flex" flexDirection="column" gap={6}>
                {/* Stats Matrix */}
                <Grid container spacing={6}>
                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <Box display="flex" alignItems="center" gap={2} mb={1}>
                        <VaultIcon style={{ color: "#7C3AED", fontSize: "24px" }} />
                        <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>Active Licenses</span>
                      </Box>
                      <h2 style={{ margin: 0, fontSize: "28px", fontWeight: 800 }}>{downloads.length}</h2>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <Box display="flex" alignItems="center" gap={2} mb={1}>
                        <OrdersIcon style={{ color: "#2563EB", fontSize: "24px" }} />
                        <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>Total Orders</span>
                      </Box>
                      <h2 style={{ margin: 0, fontSize: "28px", fontWeight: 800 }}>{orders.length}</h2>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <Box display="flex" alignItems="center" gap={2} mb={1}>
                        <BoltIcon style={{ color: "#10B981", fontSize: "24px" }} />
                        <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>Security Status</span>
                      </Box>
                      <h2 style={{ margin: 0, fontSize: "24px", fontWeight: 800, color: theme.palette.status.success }}>Verified</h2>
                    </Card>
                  </Grid>
                </Grid>

                {/* Quick Digital Vault Preview */}
                <Card padding={8} radius="xl" elevation="card">
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                    <div>
                      <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700 }}>Recent Digital Releases</h3>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>Available for immediate signed download</span>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => handleTabSelect("downloads")}>
                      Open Full Vault
                    </Button>
                  </Box>

                  {downloads.slice(0, 2).map(dl => (
                    <Box key={dl.id} p={4} mb={2} backgroundColor={theme.palette.background.elevated} borderRadius="12px" display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
                      <Box display="flex" alignItems="center" gap={3}>
                        <img src={dl.image} alt={dl.title} style={{ width: "48px", height: "48px", objectFit: "cover", borderRadius: "8px" }} />
                        <div>
                          <strong style={{ fontSize: "14px", display: "block" }}>{dl.title}</strong>
                          <span style={{ fontSize: "12px", color: theme.palette.text.muted }}>Version: {dl.version || "v2.0"} · {dl.fileSize || "89 MB"}</span>
                        </div>
                      </Box>
                      <Button variant="primary" size="sm" onClick={() => handleDownloadFile(dl)} leftIcon={<DownloadIcon />}>
                        Download Signed
                      </Button>
                    </Box>
                  ))}
                </Card>
              </Box>
            )}

            {/* 2. MY DIGITAL VAULT TAB (Deep Navy Atmosphere) */}
            {currentTab === "downloads" && (
              <VaultCanvas className="bv-tech-grid bv-glow-vault">
                <Box mb={6} display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
                  <div>
                    <Box mb={1}>
                      <Chip label="CRYPTOGRAPHIC VAULT" color="primary" variant="filled" uppercase />
                    </Box>
                    <h2 style={{ fontSize: "26px", fontWeight: 800, margin: "0 0 6px 0", color: "#FFFFFF" }}>
                      My Personal Digital Vault
                    </h2>
                    <span style={{ fontSize: "13px", color: "rgba(255, 255, 255, 0.7)" }}>
                      {downloads.length} Verified digital entitlements bound to your developer signature.
                    </span>
                  </div>
                  <Box display="flex" gap={2}>
                    <Chip label="256-Bit Signed" color="success" variant="filled" uppercase />
                  </Box>
                </Box>

                {loadingDownloads ? (
                  <Skeleton variant="rectangular" height={240} radius="lg" />
                ) : downloads.length === 0 ? (
                  <Box p={8} textAlign="center" color="rgba(255, 255, 255, 0.7)">
                    <VaultIcon style={{ fontSize: "48px", color: "#7C3AED", marginBottom: "16px" }} />
                    <h3>Your Digital Vault is Empty</h3>
                    <p style={{ maxWidth: "420px", margin: "0 auto 24px auto", fontSize: "14px" }}>
                      Explore commercial UI kits, Rust backend architectures, and coding scripts in our catalog.
                    </p>
                    <Button variant="primary" onClick={() => navigate("/catalog?type=digital")}>
                      Browse Digital Blueprints
                    </Button>
                  </Box>
                ) : (
                  <Box display="flex" flexDirection="column" gap={4}>
                    {downloads.map((dl) => (
                      <VaultItemCard key={dl.id}>
                        <Box display="flex" justifyContent="space-between" alignItems="flex-start" flexWrap="wrap" gap={3}>
                          <Box display="flex" gap={4} alignItems="center">
                            <img 
                              src={dl.image} 
                              alt={dl.title} 
                              style={{ width: "64px", height: "64px", objectFit: "cover", borderRadius: "10px", border: "1px solid rgba(255, 255, 255, 0.15)" }} 
                            />
                            <div>
                              <div style={{ display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" }}>
                                <Chip label={dl.version || "v2.0.1"} color="primary" size="xs" uppercase />
                                <span style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>{dl.fileSize || "89.4 MB"} · ZIP</span>
                              </div>
                              <h4 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "#FFFFFF" }}>{dl.title}</h4>
                              <div style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.6)", marginTop: "4px" }}>
                                Purchased on {dl.purchaseDate || "August 2026"} · Commercial License Active
                              </div>
                            </div>
                          </Box>

                          <Box display="flex" gap={2}>
                            <Button 
                              variant="primary" 
                              size="sm" 
                              onClick={() => handleDownloadFile(dl)}
                              leftIcon={<DownloadIcon style={{ fontSize: "16px" }} />}
                            >
                              Download Asset
                            </Button>
                          </Box>
                        </Box>

                        {/* License Key Strip */}
                        <Box 
                          p={3} 
                          backgroundColor="rgba(255, 255, 255, 0.04)" 
                          borderRadius="8px" 
                          border="1px solid rgba(255, 255, 255, 0.08)"
                          display="flex" 
                          justifyContent="space-between" 
                          alignItems="center"
                          fontSize="12px"
                        >
                          <Box display="flex" alignItems="center" gap={1.5} color="rgba(255, 255, 255, 0.7)">
                            <KeyIcon style={{ fontSize: "16px", color: "#F59E0B" }} />
                            <span>License Signature:</span>
                            <code style={{ color: "#C7D2FE", background: "rgba(255, 255, 255, 0.08)", padding: "2px 6px", borderRadius: "4px" }}>
                              {dl.licenseKey || "BV-PRO-2026-9874-AC41"}
                            </code>
                          </Box>
                          <IconButton 
                            size="sm" 
                            variant="ghost" 
                            onClick={() => handleCopyKey(dl.licenseKey || "BV-PRO-2026-9874-AC41", dl.id)}
                            style={{ color: "#FFFFFF" }}
                          >
                            {copiedKey === dl.id ? <CheckIcon style={{ color: "#10B981", fontSize: "16px" }} /> : <CopyIcon style={{ fontSize: "16px" }} />}
                          </IconButton>
                        </Box>
                      </VaultItemCard>
                    ))}
                  </Box>
                )}
              </VaultCanvas>
            )}

            {/* 3. ORDERS TAB */}
            {currentTab === "orders" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <h3 style={{ fontSize: "20px", fontWeight: 700, margin: 0 }}>Order History & Fulfillment</h3>

                {loadingOrders ? (
                  <Skeleton variant="rectangular" height={200} radius="lg" />
                ) : orders.length === 0 ? (
                  <Card padding={8} textAlign="center">
                    <h4>No past orders located</h4>
                    <p style={{ color: theme.palette.text.secondary, marginBottom: "20px" }}>You haven't checked out any orders yet.</p>
                    <Button variant="primary" onClick={() => navigate("/catalog")}>Browse Marketplace</Button>
                  </Card>
                ) : (
                  orders.map((ord) => {
                    const isDigital = ord.orderType === "DIGITAL" || ord.items?.every(i => i.isDigital || (i.productType || i.type || "").toUpperCase() === "DIGITAL");
                    const isPhysical = ord.orderType === "PHYSICAL" || ord.items?.some(i => !i.isDigital && (i.productType || i.type || "").toUpperCase() === "PHYSICAL");
                    const isPaid = ord.status === "PAID" || ord.status === "DELIVERED" || ord.status === "FULFILLED" || ord.status === "COMPLETED";
                    const isPending = ord.status === "PENDING" || ord.status === "PAYMENT_PENDING";
                    const chipColor = isPaid ? "success" : isPending ? "warning" : "primary";
                    const orderTotal = Number(ord.totalAmount != null ? ord.totalAmount : (ord.totals?.total != null ? ord.totals.total : 0));

                    return (
                      <OrderCard key={ord.id} elevation="subtle">
                        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
                          <div>
                            <div style={{ fontSize: "12px", color: theme.palette.text.muted, marginBottom: "2px" }}>
                              ORDER #{ord.id} · {ord.createdAt ? new Date(ord.createdAt).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "Recent"}
                            </div>
                            <strong style={{ fontSize: "18px", color: theme.palette.text.primary }}>₹{orderTotal.toFixed(2)}</strong>
                          </div>

                          <Box display="flex" gap={2} alignItems="center">
                            <Chip 
                              label={ord.status || "PENDING"} 
                              color={chipColor} 
                              variant="filled" 
                              uppercase 
                            />
                            {isDigital && isPaid && (
                              <Button
                                variant="primary"
                                size="xs"
                                onClick={() => handleTabSelect("downloads")}
                                leftIcon={<VaultIcon style={{ fontSize: "14px" }} />}
                              >
                                Digital Vault
                              </Button>
                            )}
                          </Box>
                        </Box>

                        {/* Interactive Milestone Timeline */}
                        {isPhysical && (
                          <Box my={2} p={4} backgroundColor={theme.palette.background.elevated} borderRadius="12px">
                            <div style={{ fontSize: "12px", fontWeight: 700, marginBottom: "8px", color: theme.palette.text.secondary }}>
                              CARRIER TRACKING TIMELINE
                            </div>
                            <TimelineTrack>
                              <TimelineStep completed={true}><div className="dot" /><span className="step-label">Confirmed</span></TimelineStep>
                              <TimelineStep completed={isPaid}><div className="dot" /><span className="step-label">Processing</span></TimelineStep>
                              <TimelineStep active={ord.status === "SHIPPED"} completed={ord.status === "DELIVERED"}><div className="dot" /><span className="step-label">Shipped</span></TimelineStep>
                              <TimelineStep completed={ord.status === "DELIVERED"}><div className="dot" /><span className="step-label">Delivered</span></TimelineStep>
                            </TimelineTrack>
                          </Box>
                        )}

                        <Box display="flex" flexDirection="column" gap={2} mt={1}>
                          {ord.items?.map((item, idx) => {
                            const itemName = item.productName || item.title || "Product";
                            const itemUnitPrice = Number(item.unitPrice != null ? item.unitPrice : (item.price != null ? item.price : 0));
                            const itemQty = Number(item.quantity) || 1;
                            const itemSubtotal = item.subtotal != null ? Number(item.subtotal) : (itemUnitPrice * itemQty);

                            return (
                              <Box key={idx} display="flex" justifyContent="space-between" alignItems="center" fontSize="13px" p={2} backgroundColor={theme.palette.background.elevated} borderRadius="8px">
                                <Box display="flex" alignItems="center" gap={2}>
                                  <span>{itemName} <strong style={{ color: theme.palette.text.secondary }}>(x{itemQty})</strong></span>
                                </Box>
                                <span style={{ fontWeight: 700, color: theme.palette.text.primary }}>₹{itemSubtotal.toFixed(2)}</span>
                              </Box>
                            );
                          })}
                        </Box>
                      </OrderCard>
                    );
                  })
                )}
              </Box>
            )}

            {/* SUPPORT TICKETS TAB (Rule 18 & 49) */}
            {currentTab === "support" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2}>
                  <div>
                    <h3 style={{ fontSize: "20px", fontWeight: 700, margin: 0 }}>Support Tickets & Inquiries</h3>
                    <span style={{ fontSize: "13px", color: theme.palette.text.secondary }}>
                      Track resolution for digital licenses, order tracking, and platform questions.
                    </span>
                  </div>
                  <Button 
                    variant="primary" 
                    size="sm" 
                    onClick={() => setIsCreatingTicket(!isCreatingTicket)}
                    leftIcon={<AddIcon />}
                  >
                    {isCreatingTicket ? "Cancel Ticket" : "Raise Support Ticket"}
                  </Button>
                </Box>

                {/* Create Ticket Form */}
                {isCreatingTicket && (
                  <Card padding={8} radius="xl" elevation="card">
                    <h4 style={{ margin: "0 0 16px 0", fontSize: "16px", fontWeight: 700 }}>Open a New Support Ticket</h4>
                    <form onSubmit={handleCreateTicketSubmit}>
                      <Grid container spacing={4}>
                        <Grid item xs={12} sm={8}>
                          <Input 
                            label="Ticket Subject" 
                            placeholder="e.g. Issue downloading React UI Kit archive"
                            value={newTicket.subject}
                            onChange={(e) => setNewTicket({ ...newTicket, subject: e.target.value })}
                            fullWidth
                            required
                          />
                        </Grid>
                        <Grid item xs={12} sm={4}>
                          <Input 
                            label="Category" 
                            value={newTicket.category}
                            onChange={(e) => setNewTicket({ ...newTicket, category: e.target.value })}
                            fullWidth
                            required
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <Input 
                            label="Detailed Description" 
                            placeholder="Provide details about your order number, product SKU, or question..."
                            value={newTicket.description}
                            onChange={(e) => setNewTicket({ ...newTicket, description: e.target.value })}
                            multiline
                            rows={3}
                            fullWidth
                            required
                          />
                        </Grid>
                      </Grid>
                      <Box mt={4} display="flex" justifyContent="flex-end" gap={2}>
                        <Button type="button" variant="secondary" onClick={() => setIsCreatingTicket(false)}>
                          Cancel
                        </Button>
                        <Button type="submit" variant="primary">
                          Submit Support Ticket
                        </Button>
                      </Box>
                    </form>
                  </Card>
                )}

                {/* Tickets List */}
                {loadingTickets ? (
                  <Skeleton variant="rectangular" height={160} radius="lg" />
                ) : supportTickets.length === 0 ? (
                  <Card padding={8} textAlign="center">
                    <h4>No active support tickets</h4>
                    <p style={{ color: theme.palette.text.secondary, marginBottom: "20px" }}>
                      Need help with an order, digital license, or vendor question?
                    </p>
                    <Button variant="primary" onClick={() => setIsCreatingTicket(true)}>
                      Open Support Ticket
                    </Button>
                  </Card>
                ) : (
                  <Box display="flex" flexDirection="column" gap={4}>
                    {supportTickets.map((t) => (
                      <Card key={t.id} padding={6} radius="lg" elevation="subtle">
                        <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
                          <div>
                            <div style={{ display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" }}>
                              <Chip label={`#${t.id}`} size="xs" />
                              <Chip 
                                label={t.status} 
                                color={t.status === "RESOLVED" || t.status === "CLOSED" ? "success" : t.status === "OPEN" ? "warning" : "primary"} 
                                size="xs" 
                              />
                              <span style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                                {t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "Recent"}
                              </span>
                            </div>
                            <strong style={{ fontSize: "15px", display: "block" }}>{t.subject}</strong>
                            <p style={{ margin: "4px 0 0 0", fontSize: "13px", color: theme.palette.text.secondary }}>
                              {t.description}
                            </p>
                          </div>
                          <Button 
                            variant="secondary" 
                            size="xs" 
                            onClick={() => setSelectedTicket(selectedTicket?.id === t.id ? null : t)}
                          >
                            {selectedTicket?.id === t.id ? "Close Thread" : "View & Reply"}
                          </Button>
                        </Box>

                        {/* Interactive Message Thread */}
                        {selectedTicket?.id === t.id && (
                          <Box mt={4} pt={4} borderTop={`1px solid ${theme.palette.border.default}`}>
                            <div style={{ fontSize: "12px", fontWeight: 700, marginBottom: "12px", color: theme.palette.text.secondary }}>
                              CONVERSATION THREAD
                            </div>
                            <Box display="flex" flexDirection="column" gap={2} mb={3} maxHeight="240px" overflow="auto">
                              {(t.messages || []).map((m, idx) => (
                                <Box 
                                  key={idx} 
                                  p={3} 
                                  borderRadius="8px"
                                  backgroundColor={m.isAdminReply ? "rgba(124, 58, 237, 0.08)" : theme.palette.background.elevated}
                                  border={`1px solid ${m.isAdminReply ? "rgba(124, 58, 237, 0.25)" : theme.palette.border.default}`}
                                >
                                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "2px" }}>
                                    <strong style={{ fontSize: "11px", color: m.isAdminReply ? "#7C3AED" : theme.palette.text.primary }}>
                                      {m.isAdminReply ? "ByteVault Agent" : "You (" + (m.senderEmail || user?.email) + ")"}
                                    </strong>
                                    <span style={{ fontSize: "10px", color: theme.palette.text.muted }}>
                                      {m.createdAt ? new Date(m.createdAt).toLocaleTimeString() : "Just now"}
                                    </span>
                                  </div>
                                  <div style={{ fontSize: "13px", color: theme.palette.text.primary }}>
                                    {m.message}
                                  </div>
                                </Box>
                              ))}
                            </Box>

                            <Box display="flex" gap={2}>
                              <input 
                                type="text" 
                                placeholder="Type a reply to the support team..." 
                                value={ticketReply}
                                onChange={(e) => setTicketReply(e.target.value)}
                                style={{
                                  flex: 1,
                                  padding: "8px 12px",
                                  borderRadius: "8px",
                                  border: `1px solid ${theme.palette.border.default}`,
                                  fontSize: "13px",
                                  outline: "none"
                                }}
                                onKeyDown={(e) => {
                                  if (e.key === "Enter") handleSendTicketReply(t.id);
                                }}
                              />
                              <Button variant="primary" size="xs" onClick={() => handleSendTicketReply(t.id)}>
                                Reply
                              </Button>
                            </Box>
                          </Box>
                        )}
                      </Card>
                    ))}
                  </Box>
                )}
              </Box>
            )}

            {/* PUBLISHER STUDIO TAB (Developer/Seller Role) */}
            {currentTab === "publisher" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Grid container spacing={6}>
                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Active Blueprints
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "28px", fontWeight: 800, color: "#7C3AED" }}>4</h2>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Total Vault Downloads
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "28px", fontWeight: 800, color: "#2563EB" }}>1,428</h2>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Publisher Rating
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "28px", fontWeight: 800, color: "#10B981" }}>4.9 ★</h2>
                    </Card>
                  </Grid>
                </Grid>

                {/* Submit New Blueprint Panel */}
                <Card padding={8} radius="xl" elevation="card">
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={2}>
                    <div>
                      <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700 }}>Submit Engineering Blueprint</h3>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Publish a production-grade template or architecture to the ByteVault marketplace.
                      </span>
                    </div>
                    <Chip label="Vendor Portal" color="primary" size="xs" uppercase />
                  </Box>

                  <form onSubmit={(e) => {
                    e.preventDefault();
                    window.dispatchEvent(new CustomEvent("bytevault_toast", {
                      detail: { message: "Blueprint submitted successfully! Auditing queue initialized.", type: "success" }
                    }));
                  }}>
                    <Grid container spacing={4}>
                      <Grid item xs={12} sm={8}>
                        <Input label="Blueprint Title" placeholder="e.g. Go gRPC Event Stream Architecture" fullWidth required />
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Input label="Target Price (USD)" placeholder="49.00" type="number" fullWidth required />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <Input label="Asset Category" defaultValue="Software & Coding" fullWidth required />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <Input label="Release Version" placeholder="v1.0.0" fullWidth required />
                      </Grid>
                      <Grid item xs={12}>
                        <Input label="Technical Architecture Summary" placeholder="Describe frameworks, tests, migrations included in the package..." multiline rows={3} fullWidth required />
                      </Grid>
                    </Grid>
                    <Box mt={4} display="flex" justifyContent="flex-end">
                      <Button type="submit" variant="primary">
                        Submit Blueprint for Audit
                      </Button>
                    </Box>
                  </form>
                </Card>
              </Box>
            )}

            {/* 4. PROFILE & SETTINGS TAB */}
            {currentTab === "profile" && (
              <Card padding={8} radius="xl" elevation="card">
                <h3 style={{ fontSize: "20px", fontWeight: 700, margin: "0 0 20px 0" }}>Profile & Security Preferences</h3>
                <Box display="flex" flexDirection="column" gap={4} maxWidth="480px">
                  <Input label="Full Name" defaultValue={user?.name || user?.username || ""} fullWidth />
                  <Input label="Email Address" defaultValue={user?.email || ""} fullWidth disabled />
                  <Input label="Organization / GitHub Handle" defaultValue={user?.organization || ""} fullWidth />
                  <Box mt={2}>
                    <Button variant="primary">Save Changes</Button>
                  </Box>
                </Box>
              </Card>
            )}

            {/* 5. SAVED ADDRESSES TAB */}
            {currentTab === "addresses" && (
              <Card padding={8} radius="xl" elevation="card">
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                  <div>
                    <h3 style={{ fontSize: "20px", fontWeight: 700, margin: 0 }}>Saved Delivery Addresses</h3>
                    <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>Manage delivery addresses for physical hardware gear.</span>
                  </div>
                </Box>
                {addresses.length === 0 ? (
                  <Box p={6} textAlign="center" color={theme.palette.text.secondary} border={`1px dashed ${theme.palette.border.default}`} borderRadius="12px">
                    <AddressIcon style={{ fontSize: "40px", color: theme.palette.text.muted, marginBottom: "8px" }} />
                    <h4 style={{ margin: "0 0 4px 0", color: theme.palette.text.primary }}>No Saved Addresses</h4>
                    <p style={{ fontSize: "13px", margin: 0 }}>You have not added any physical delivery addresses to your account yet.</p>
                  </Box>
                ) : (
                  <Grid container spacing={4}>
                    {addresses.map((addr) => (
                      <Grid item xs={12} sm={6} key={addr.id}>
                        <Box p={4} border={`1px solid ${theme.palette.border.default}`} borderRadius="12px">
                          <Box display="flex" justifyContent="space-between" mb={2}>
                            <strong>{addr.name || "Delivery Address"}</strong>
                            {addr.isDefault && <Chip label="Default" color="primary" size="xs" />}
                          </Box>
                          <div style={{ fontSize: "13px", color: theme.palette.text.secondary, lineHeight: 1.5 }}>
                            {addr.street} <br />
                            {addr.city}, {addr.state} {addr.zip} <br />
                            {addr.country || "United States"}
                          </div>
                        </Box>
                      </Grid>
                    ))}
                  </Grid>
                )}
              </Card>
            )}
          </div>
        </AccountLayout>
      </Container>
    </Box>
  );
};

export default Account;
