import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import {
  AdminPanelSettingsOutlined as AdminIcon,
  People as UsersIcon,
  VerifiedUserOutlined as VendorIcon,
  HourglassEmptyOutlined as PendingIcon,
  CheckCircle as ApproveIcon,
  HighlightOffOutlined as RejectIcon,
  BlockOutlined as SuspendIcon,
  RestartAltOutlined as ReactivateIcon,
  HeadsetMicOutlined as SupportIcon,
  StorefrontOutlined as StoreIcon,
  Inventory2Outlined as InventoryIcon,
  GavelOutlined as ModerationIcon,
  Close as CloseIcon,
  Refresh as RefreshIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { Skeleton } from "../components/primitives/Skeleton";
import { useAuth } from "../store/AuthContext";
import { adminVendorService } from "../services/adminVendorService";
import { productService } from "../services/productService";
import { supportService } from "../services/supportService";

const Layout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "260px 1fr",
  gap: theme.spacing(8),
  paddingTop: theme.spacing(8),
  paddingBottom: theme.spacing(16),

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
  },
}));

const Sidebar = styled("aside")(({ theme }) => ({
  backgroundColor: "#070B16",
  padding: theme.spacing(4),
  borderRadius: "16px",
  border: "1px solid rgba(255, 255, 255, 0.1)",
  color: "#FFFFFF",
  height: "fit-content",
  display: "flex",
  flexDirection: "column",
  gap: "6px",
}));

const TabBtn = styled("button", {
  shouldForwardProp: (prop) => prop !== "active",
})(({ active }) => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  padding: "12px 16px",
  borderRadius: "10px",
  border: "none",
  background: active ? "rgba(124, 58, 237, 0.18)" : "transparent",
  color: active ? "#FFFFFF" : "rgba(255, 255, 255, 0.65)",
  borderLeft: active ? "3px solid #7C3AED" : "3px solid transparent",
  fontWeight: active ? 600 : 500,
  fontSize: "14px",
  cursor: "pointer",
  textAlign: "left",
  transition: "all 0.15s ease",
  width: "100%",

  "&:hover": {
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    color: "#FFFFFF",
  },
}));

const TableWrapper = styled("div")(({ theme }) => ({
  overflowX: "auto",
  border: `1px solid ${theme.palette.border.default}`,
  borderRadius: "12px",
  backgroundColor: "#FFFFFF",

  "& table": {
    width: "100%",
    borderCollapse: "collapse",
    textAlign: "left",
    fontSize: "13px",
  },
  "& th": {
    backgroundColor: theme.palette.background.elevated,
    padding: "12px 16px",
    fontWeight: 700,
    color: theme.palette.text.secondary,
    borderBottom: `1px solid ${theme.palette.border.default}`,
  },
  "& td": {
    padding: "14px 16px",
    borderBottom: `1px solid ${theme.palette.border.default}`,
    color: theme.palette.text.primary,
  }
}));

const ModalBackdrop = styled("div")({
  position: "fixed",
  inset: 0,
  backgroundColor: "rgba(0, 0, 0, 0.75)",
  backdropFilter: "blur(6px)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  zIndex: 9999,
  padding: "16px",
});

const ModalBox = styled("div")(({ theme }) => ({
  backgroundColor: "#0B1020",
  color: "#FFFFFF",
  border: "1px solid rgba(255, 255, 255, 0.15)",
  borderRadius: "16px",
  padding: theme.spacing(6),
  maxWidth: "580px",
  width: "100%",
  boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.75)",
  maxHeight: "90vh",
  overflowY: "auto",
}));

export const AdminDashboard = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState("pending-vendors"); // "pending-vendors" | "all-vendors" | "moderation" | "overview" | "users" | "support"

  // Vendor state
  const [pendingVendors, setPendingVendors] = useState([]);
  const [allVendors, setAllVendors] = useState([]);
  const [vendorFilter, setVendorFilter] = useState("ALL");
  const [loadingVendors, setLoadingVendors] = useState(true);
  const [selectedVendor, setSelectedVendor] = useState(null);

  // Vendor Modal actions state
  const [actionModal, setActionModal] = useState({ open: false, type: null, vendor: null, reason: "" });
  const [actionLoading, setActionLoading] = useState(false);

  // Product Moderation state
  const [products, setProducts] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [productFilter, setProductFilter] = useState("ALL");
  const [takedownModal, setTakedownModal] = useState({ open: false, product: null, reason: "" });
  const [takedownLoading, setTakedownLoading] = useState(false);

  // Users state
  const [userList] = useState([
    { id: "u1", name: "Alex Rivera", email: "customer@bytevault.com", role: "CUSTOMER", status: "ACTIVE" },
    { id: "u2", name: "Jane Smith", email: "admin@bytevault.com", role: "ADMIN", status: "ACTIVE" },
    { id: "u3", name: "Marcus Vance", email: "vendor@bytevault.com", role: "VENDOR", status: "APPROVED" },
  ]);

  // Support state
  const [supportTickets, setSupportTickets] = useState([]);

  const fetchVendorData = async () => {
    setLoadingVendors(true);
    try {
      const [pendingList, fullList] = await Promise.all([
        adminVendorService.getPendingVendors().catch(() => []),
        adminVendorService.getAllVendors().catch(() => [])
      ]);
      setPendingVendors(Array.isArray(pendingList) ? pendingList : []);
      setAllVendors(Array.isArray(fullList) ? fullList : []);
    } catch (err) {
      console.warn("Failed fetching vendor lists:", err);
    } finally {
      setLoadingVendors(false);
    }
  };

  const fetchProductData = async () => {
    setLoadingProducts(true);
    try {
      const list = await productService.getAllProductsAdmin();
      setProducts(Array.isArray(list) ? list : []);
    } catch (err) {
      console.warn("Failed fetching admin products:", err);
    } finally {
      setLoadingProducts(false);
    }
  };

  useEffect(() => {
    fetchVendorData();
    fetchProductData();
    const fetchTickets = async () => {
      try {
        const list = await supportService.getAllTicketsAdmin();
        setSupportTickets(Array.isArray(list) ? list : []);
      } catch (e) {
        console.warn("Failed fetching support tickets", e);
      }
    };
    fetchTickets();
  }, []);

  const handleApproveVendor = async (vendorId) => {
    setActionLoading(true);
    try {
      await adminVendorService.approveVendor(vendorId);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Vendor application APPROVED. Seller privileges activated.", type: "success" }
        })
      );
      setActionModal({ open: false, type: null, vendor: null, reason: "" });
      setSelectedVendor(null);
      await fetchVendorData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to approve vendor.", type: "error" }
        })
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleRejectVendor = async (vendorId, reason) => {
    setActionLoading(true);
    try {
      await adminVendorService.rejectVendor(vendorId, reason);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Vendor application REJECTED.", type: "info" }
        })
      );
      setActionModal({ open: false, type: null, vendor: null, reason: "" });
      setSelectedVendor(null);
      await fetchVendorData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to reject vendor.", type: "error" }
        })
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleSuspendVendor = async (vendorId, reason) => {
    setActionLoading(true);
    try {
      await adminVendorService.suspendVendor(vendorId, reason);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Vendor account SUSPENDED. Product management disabled.", type: "warning" }
        })
      );
      setActionModal({ open: false, type: null, vendor: null, reason: "" });
      setSelectedVendor(null);
      await fetchVendorData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to suspend vendor.", type: "error" }
        })
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleReactivateVendor = async (vendorId) => {
    setActionLoading(true);
    try {
      await adminVendorService.reactivateVendor(vendorId);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Vendor account REACTIVATED.", type: "success" }
        })
      );
      setActionModal({ open: false, type: null, vendor: null, reason: "" });
      setSelectedVendor(null);
      await fetchVendorData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to reactivate vendor.", type: "error" }
        })
      );
    } finally {
      setActionLoading(false);
    }
  };

  // Product Moderation Actions
  const handleTakedownProduct = async () => {
    if (!takedownModal.reason) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Moderation reason is required.", type: "error" }
        })
      );
      return;
    }

    setTakedownLoading(true);
    try {
      await productService.takedownProduct(takedownModal.product.id, takedownModal.reason);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Product taken down from public catalog.", type: "warning" }
        })
      );
      setTakedownModal({ open: false, product: null, reason: "" });
      await fetchProductData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to take down product.", type: "error" }
        })
      );
    } finally {
      setTakedownLoading(false);
    }
  };

  const handleReactivateProduct = async (id) => {
    try {
      await productService.reactivateProduct(id);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Product reactivated and visible in live catalog.", type: "success" }
        })
      );
      await fetchProductData();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to reactivate product.", type: "error" }
        })
      );
    }
  };

  const renderStatusBadge = (status) => {
    switch (status) {
      case "APPROVED":
      case "PUBLISHED":
        return <Chip label={status} color="success" size="xs" uppercase />;
      case "PENDING_APPROVAL":
      case "DRAFT":
        return <Chip label={status.replace("_", " ")} color="warning" size="xs" uppercase />;
      case "SUSPENDED":
      case "DEACTIVATED":
        return <Chip label={status} color="error" size="xs" uppercase />;
      case "REJECTED":
      case "ARCHIVED":
        return <Chip label={status} color="neutral" size="xs" uppercase />;
      default:
        return <Chip label={status || "UNKNOWN"} color="neutral" size="xs" uppercase />;
    }
  };

  const filteredAllVendors = allVendors.filter(v => {
    if (vendorFilter === "ALL") return true;
    return v.status === vendorFilter;
  });

  const filteredProducts = products.filter(p => {
    if (productFilter === "ALL") return true;
    return (p.status || "PUBLISHED") === productFilter;
  });

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC", minHeight: "100vh" }}>
      <Container maxWidth="xxl">
        {/* Header Strip */}
        <Box mb={6} display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
          <div>
            <Box display="flex" alignItems="center" gap={2} mb={1}>
              <h1 style={{ fontSize: "28px", fontWeight: 800, margin: 0, color: theme.palette.text.primary }}>
                Marketplace Administration & Operations
              </h1>
              <Chip label="ADMINISTRATOR" color="accent" size="xs" uppercase />
            </Box>
            <span style={{ fontSize: "14px", color: theme.palette.text.secondary }}>
              Operator: <strong>{user?.name || "Marketplace Admin"}</strong> ({user?.email})
            </span>
          </div>
          <Box display="flex" gap={2}>
            <Button variant="secondary" size="sm" onClick={() => { fetchVendorData(); fetchProductData(); }} leftIcon={<RefreshIcon style={{ fontSize: "16px" }} />}>
              Refresh Records
            </Button>
            <Button variant="secondary" size="sm" onClick={() => navigate("/catalog")}>
              Marketplace Storefront
            </Button>
          </Box>
        </Box>

        <Layout>
          {/* Sidebar */}
          <Sidebar>
            <Box p={2} mb={1}>
              <div style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.4)", textTransform: "uppercase", letterSpacing: "0.08em" }}>
                GOVERNANCE & VENDORS
              </div>
            </Box>
            <TabBtn active={activeTab === "pending-vendors"} onClick={() => setActiveTab("pending-vendors")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <PendingIcon style={{ fontSize: "18px", color: "#F59E0B" }} />
                <span>Pending Approvals</span>
              </Box>
              {pendingVendors.length > 0 && (
                <span style={{ 
                  backgroundColor: "#F59E0B", 
                  color: "#000000", 
                  fontSize: "11px", 
                  fontWeight: 800, 
                  padding: "2px 8px", 
                  borderRadius: "10px" 
                }}>
                  {pendingVendors.length}
                </span>
              )}
            </TabBtn>
            <TabBtn active={activeTab === "all-vendors"} onClick={() => setActiveTab("all-vendors")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <VendorIcon style={{ fontSize: "18px", color: "#10B981" }} />
                <span>Vendor Directory</span>
              </Box>
              <span style={{ fontSize: "12px", opacity: 0.6 }}>{allVendors.length}</span>
            </TabBtn>
            <TabBtn active={activeTab === "moderation"} onClick={() => setActiveTab("moderation")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <ModerationIcon style={{ fontSize: "18px", color: "#EC4899" }} />
                <span>Marketplace Catalog</span>
              </Box>
              <span style={{ fontSize: "12px", opacity: 0.6 }}>{products.length}</span>
            </TabBtn>
            <TabBtn active={activeTab === "overview"} onClick={() => setActiveTab("overview")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <AdminIcon style={{ fontSize: "18px" }} />
                <span>Platform Metrics</span>
              </Box>
            </TabBtn>
            <TabBtn active={activeTab === "users"} onClick={() => setActiveTab("users")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <UsersIcon style={{ fontSize: "18px" }} />
                <span>User Accounts</span>
              </Box>
            </TabBtn>
            <TabBtn active={activeTab === "support"} onClick={() => setActiveTab("support")}>
              <Box display="flex" alignItems="center" gap={1.5}>
                <SupportIcon style={{ fontSize: "18px", color: "#3B82F6" }} />
                <span>Support Tickets</span>
              </Box>
              {supportTickets.length > 0 && (
                <span style={{ fontSize: "12px", opacity: 0.6 }}>{supportTickets.length}</span>
              )}
            </TabBtn>
          </Sidebar>

          {/* Main Workspace */}
          <div>
            {/* TAB 1: PENDING VENDOR APPROVALS QUEUE */}
            {activeTab === "pending-vendors" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Card padding={6} radius="lg" elevation="subtle">
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={2}>
                    <div>
                      <h2 style={{ margin: "0 0 4px 0", fontSize: "20px", fontWeight: 800, color: theme.palette.text.primary }}>
                        Pending Vendor Applications ({pendingVendors.length})
                      </h2>
                      <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                        Review seller applications before granting product publishing authorization.
                      </p>
                    </div>
                  </Box>

                  {loadingVendors ? (
                    <Box display="flex" flexDirection="column" gap={3}>
                      <Skeleton variant="rectangular" height={50} width="100%" />
                      <Skeleton variant="rectangular" height={50} width="100%" />
                    </Box>
                  ) : pendingVendors.length === 0 ? (
                    <Box textAlign="center" py={12} px={4} backgroundColor="#F8FAFC" borderRadius="12px" border="1px dashed #CBD5E1">
                      <ApproveIcon style={{ fontSize: "48px", color: "#10B981", marginBottom: "12px" }} />
                      <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", fontWeight: 700, color: theme.palette.text.primary }}>
                        All caught up! No pending vendor applications
                      </h3>
                      <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                        When a user registers as a vendor, their store onboarding application will appear here for review.
                      </p>
                    </Box>
                  ) : (
                    <TableWrapper>
                      <table>
                        <thead>
                          <tr>
                            <th>Store Name</th>
                            <th>Applicant Email</th>
                            <th>Description</th>
                            <th>Tax / Business ID</th>
                            <th>Submitted</th>
                            <th>Status</th>
                            <th style={{ textAlign: "right" }}>Review Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {pendingVendors.map((v) => (
                            <tr key={v.id || v.userId}>
                              <td>
                                <strong style={{ color: theme.palette.text.primary }}>{v.storeName}</strong>
                                <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                                  slug: {v.storeSlug}
                                </div>
                              </td>
                              <td>{v.email || v.supportEmail || "—"}</td>
                              <td style={{ maxWidth: "220px", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                                {v.storeDescription || "No description provided"}
                              </td>
                              <td>{v.businessTaxId || "—"}</td>
                              <td>
                                {v.createdAt ? new Date(v.createdAt).toLocaleDateString() : "Recent"}
                              </td>
                              <td>{renderStatusBadge(v.status)}</td>
                              <td style={{ textAlign: "right" }}>
                                <Box display="flex" gap={1.5} justifyContent="flex-end">
                                  <Button 
                                    size="xs" 
                                    variant="secondary"
                                    onClick={() => setSelectedVendor(v)}
                                  >
                                    Inspect
                                  </Button>
                                  <Button 
                                    size="xs" 
                                    variant="primary"
                                    onClick={() => setActionModal({ open: true, type: "APPROVE", vendor: v, reason: "" })}
                                  >
                                    Approve
                                  </Button>
                                  <Button 
                                    size="xs" 
                                    variant="outline"
                                    onClick={() => setActionModal({ open: true, type: "REJECT", vendor: v, reason: "" })}
                                  >
                                    Reject
                                  </Button>
                                </Box>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </TableWrapper>
                  )}
                </Card>
              </Box>
            )}

            {/* TAB 2: VENDOR DIRECTORY */}
            {activeTab === "all-vendors" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Card padding={6} radius="lg" elevation="subtle">
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={3}>
                    <div>
                      <h2 style={{ margin: "0 0 4px 0", fontSize: "20px", fontWeight: 800, color: theme.palette.text.primary }}>
                        Marketplace Vendor Directory ({allVendors.length})
                      </h2>
                      <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                        Manage vendor status, monitor store profiles, and apply governance actions.
                      </p>
                    </div>
                    {/* Status Filter Chips */}
                    <Box display="flex" gap={1} flexWrap="wrap">
                      {["ALL", "APPROVED", "PENDING_APPROVAL", "SUSPENDED", "REJECTED"].map(f => (
                        <button
                          key={f}
                          onClick={() => setVendorFilter(f)}
                          style={{
                            padding: "6px 12px",
                            borderRadius: "8px",
                            fontSize: "12px",
                            fontWeight: 700,
                            border: vendorFilter === f ? "1px solid #7C3AED" : "1px solid #CBD5E1",
                            backgroundColor: vendorFilter === f ? "#7C3AED" : "#FFFFFF",
                            color: vendorFilter === f ? "#FFFFFF" : "#475569",
                            cursor: "pointer"
                          }}
                        >
                          {f.replace("_", " ")}
                        </button>
                      ))}
                    </Box>
                  </Box>

                  {loadingVendors ? (
                    <Skeleton variant="rectangular" height={150} width="100%" />
                  ) : filteredAllVendors.length === 0 ? (
                    <Box textAlign="center" py={10} px={4} backgroundColor="#F8FAFC" borderRadius="12px" border="1px dashed #CBD5E1">
                      <StoreIcon style={{ fontSize: "40px", color: "#94A3B8", marginBottom: "8px" }} />
                      <p style={{ margin: 0, fontSize: "14px", color: theme.palette.text.secondary }}>
                        No vendors found matching filter: <strong>{vendorFilter}</strong>
                      </p>
                    </Box>
                  ) : (
                    <TableWrapper>
                      <table>
                        <thead>
                          <tr>
                            <th>Store</th>
                            <th>Contact Email</th>
                            <th>Status</th>
                            <th>Payout Info</th>
                            <th>Reviewed By</th>
                            <th>Last Update</th>
                            <th style={{ textAlign: "right" }}>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredAllVendors.map((v) => (
                            <tr key={v.id || v.userId}>
                              <td>
                                <strong style={{ color: theme.palette.text.primary }}>{v.storeName}</strong>
                                <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                                  slug: {v.storeSlug}
                                </div>
                              </td>
                              <td>{v.email || v.supportEmail || "—"}</td>
                              <td>{renderStatusBadge(v.status)}</td>
                              <td style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                                {v.payoutInfo ? v.payoutInfo.substring(0, 24) : "—"}
                              </td>
                              <td style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                                {v.reviewedBy ? `Admin ${v.reviewedBy.toString().substring(0, 8)}` : "—"}
                              </td>
                              <td style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                                {v.updatedAt || v.createdAt ? new Date(v.updatedAt || v.createdAt).toLocaleDateString() : "—"}
                              </td>
                              <td style={{ textAlign: "right" }}>
                                <Box display="flex" gap={1} justifyContent="flex-end">
                                  <Button size="xs" variant="secondary" onClick={() => setSelectedVendor(v)}>
                                    Details
                                  </Button>
                                  {v.status === "APPROVED" && (
                                    <Button 
                                      size="xs" 
                                      variant="outline" 
                                      style={{ color: "#EF4444", borderColor: "#EF4444" }}
                                      onClick={() => setActionModal({ open: true, type: "SUSPEND", vendor: v, reason: "" })}
                                    >
                                      Suspend
                                    </Button>
                                  )}
                                  {v.status === "SUSPENDED" && (
                                    <Button 
                                      size="xs" 
                                      variant="primary" 
                                      onClick={() => setActionModal({ open: true, type: "REACTIVATE", vendor: v, reason: "" })}
                                    >
                                      Reactivate
                                    </Button>
                                  )}
                                  {v.status === "PENDING_APPROVAL" && (
                                    <Button 
                                      size="xs" 
                                      variant="primary" 
                                      onClick={() => setActionModal({ open: true, type: "APPROVE", vendor: v, reason: "" })}
                                    >
                                      Approve
                                    </Button>
                                  )}
                                </Box>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </TableWrapper>
                  )}
                </Card>
              </Box>
            )}

            {/* TAB 3: PRODUCT MODERATION */}
            {activeTab === "moderation" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Card padding={6} radius="lg" elevation="subtle">
                  <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={3}>
                    <div>
                      <h2 style={{ margin: "0 0 4px 0", fontSize: "20px", fontWeight: 800, color: theme.palette.text.primary }}>
                        Marketplace Product Catalog ({products.length})
                      </h2>
                      <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                        Live marketplace catalog governance. Approved vendors publish directly to the storefront; administrators oversee listings with policy takedown and reactivation controls.
                      </p>
                    </div>

                    {/* Status Filter Chips */}
                    <Box display="flex" gap={1} flexWrap="wrap">
                      {["ALL", "PUBLISHED", "DRAFT", "DEACTIVATED"].map(f => (
                        <button
                          key={f}
                          onClick={() => setProductFilter(f)}
                          style={{
                            padding: "6px 12px",
                            borderRadius: "8px",
                            fontSize: "12px",
                            fontWeight: 700,
                            border: productFilter === f ? "1px solid #7C3AED" : "1px solid #CBD5E1",
                            backgroundColor: productFilter === f ? "#7C3AED" : "#FFFFFF",
                            color: productFilter === f ? "#FFFFFF" : "#475569",
                            cursor: "pointer"
                          }}
                        >
                          {f}
                        </button>
                      ))}
                    </Box>
                  </Box>

                  {loadingProducts ? (
                    <Skeleton variant="rectangular" height={150} width="100%" />
                  ) : filteredProducts.length === 0 ? (
                    <Box textAlign="center" py={10} px={4} backgroundColor="#F8FAFC" borderRadius="12px" border="1px dashed #CBD5E1">
                      <InventoryIcon style={{ fontSize: "40px", color: "#94A3B8", marginBottom: "8px" }} />
                      <p style={{ margin: 0, fontSize: "14px", color: theme.palette.text.secondary }}>
                        No products found matching filter: <strong>{productFilter}</strong>
                      </p>
                    </Box>
                  ) : (
                    <TableWrapper>
                      <table>
                        <thead>
                          <tr>
                            <th>Product Name</th>
                            <th>Type</th>
                            <th>Category</th>
                            <th>Price</th>
                            <th>Status</th>
                            <th>Moderation Notes</th>
                            <th style={{ textAlign: "right" }}>Moderation Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {filteredProducts.map((p) => (
                            <tr key={p.id}>
                              <td>
                                <strong style={{ color: theme.palette.text.primary }}>{p.title || p.name}</strong>
                                <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                                  ID: {p.id.substring(0, 8)}
                                </div>
                              </td>
                              <td>
                                <Chip label={p.type || p.productType} color={p.type === "DIGITAL" ? "primary" : "accent"} size="xs" uppercase />
                              </td>
                              <td>{p.categoryName || p.category || "—"}</td>
                              <td><strong>₹{p.price?.toFixed(2)}</strong></td>
                              <td>{renderStatusBadge(p.status || "PUBLISHED")}</td>
                              <td style={{ maxWidth: "200px", fontSize: "12px", color: theme.palette.text.secondary }}>
                                {p.moderationReason ? (
                                  <span style={{ color: "#EF4444" }}>{p.moderationReason}</span>
                                ) : "—"}
                              </td>
                              <td style={{ textAlign: "right" }}>
                                <Box display="flex" gap={1} justifyContent="flex-end">
                                  {p.status === "PUBLISHED" && (
                                    <Button 
                                      size="xs" 
                                      variant="outline"
                                      style={{ color: "#EF4444", borderColor: "#EF4444" }}
                                      onClick={() => setTakedownModal({ open: true, product: p, reason: "" })}
                                    >
                                      Take Down
                                    </Button>
                                  )}
                                  {p.status === "DEACTIVATED" && (
                                    <Button 
                                      size="xs" 
                                      variant="primary"
                                      onClick={() => handleReactivateProduct(p.id)}
                                    >
                                      Reactivate
                                    </Button>
                                  )}
                                </Box>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </TableWrapper>
                  )}
                </Card>
              </Box>
            )}

            {/* TAB 4: PLATFORM OVERVIEW */}
            {activeTab === "overview" && (
              <Box display="flex" flexDirection="column" gap={6}>
                <Grid container spacing={6}>
                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Pending Applications
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "32px", fontWeight: 800, color: "#F59E0B" }}>
                        {pendingVendors.length}
                      </h2>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Awaiting governance review
                      </span>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Active Approved Vendors
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "32px", fontWeight: 800, color: "#10B981" }}>
                        {allVendors.filter(v => v.status === "APPROVED").length}
                      </h2>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        Authorized to publish products
                      </span>
                    </Card>
                  </Grid>

                  <Grid item xs={12} sm={4}>
                    <Card padding={6} radius="lg" elevation="subtle">
                      <span style={{ fontSize: "12px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                        Total Marketplace Products
                      </span>
                      <h2 style={{ margin: "4px 0 0 0", fontSize: "32px", fontWeight: 800, color: theme.palette.primary.main }}>
                        {products.length}
                      </h2>
                      <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                        {products.filter(p => p.status === "PUBLISHED").length} published
                      </span>
                    </Card>
                  </Grid>
                </Grid>

                <Card padding={6} radius="lg" elevation="subtle">
                  <h3 style={{ margin: "0 0 16px 0", fontSize: "18px", fontWeight: 800 }}>
                    Direct Publishing Marketplace Architecture
                  </h3>
                  <p style={{ fontSize: "14px", lineHeight: 1.6, color: theme.palette.text.secondary, margin: 0 }}>
                    ByteVault Media empowers approved vendors to directly create, draft, and publish digital assets and physical merchandise without intermediate approval queues. Marketplace administrators maintain quality control via active catalog moderation and takedown controls.
                  </p>
                </Card>
              </Box>
            )}

            {/* TAB 5: USER ACCOUNTS */}
            {activeTab === "users" && (
              <Card padding={6} radius="lg" elevation="subtle">
                <h2 style={{ margin: "0 0 16px 0", fontSize: "20px", fontWeight: 800 }}>
                  User Management ({userList.length})
                </h2>
                <TableWrapper>
                  <table>
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Email</th>
                        <th>Role</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {userList.map((u) => (
                        <tr key={u.id}>
                          <td><strong>{u.name}</strong></td>
                          <td>{u.email}</td>
                          <td><Chip label={u.role} color="primary" size="xs" uppercase /></td>
                          <td><Chip label={u.status} color="success" size="xs" uppercase /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </TableWrapper>
              </Card>
            )}

            {/* TAB 6: SUPPORT TICKETS */}
            {activeTab === "support" && (
              <Card padding={6} radius="lg" elevation="subtle">
                <h2 style={{ margin: "0 0 16px 0", fontSize: "20px", fontWeight: 800 }}>
                  Support Tickets ({supportTickets.length})
                </h2>
                {supportTickets.length === 0 ? (
                  <Box textAlign="center" py={8} color={theme.palette.text.secondary}>
                    No open support tickets.
                  </Box>
                ) : (
                  <TableWrapper>
                    <table>
                      <thead>
                        <tr>
                          <th>Ticket ID</th>
                          <th>Subject</th>
                          <th>Customer</th>
                          <th>Category</th>
                          <th>Status</th>
                          <th>Date</th>
                        </tr>
                      </thead>
                      <tbody>
                        {supportTickets.map((t) => (
                          <tr key={t.id}>
                            <td><code>{t.id.substring(0, 8)}</code></td>
                            <td><strong>{t.subject}</strong></td>
                            <td>{t.userEmail}</td>
                            <td><Chip label={t.category || "GENERAL"} color="neutral" size="xs" /></td>
                            <td><Chip label={t.status} color={t.status === "RESOLVED" ? "success" : "warning"} size="xs" /></td>
                            <td>{t.createdAt ? new Date(t.createdAt).toLocaleDateString() : "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </TableWrapper>
                )}
              </Card>
            )}
          </div>
        </Layout>
      </Container>

      {/* VENDOR DETAILS INSPECTION MODAL */}
      {selectedVendor && (
        <ModalBackdrop onClick={() => setSelectedVendor(null)}>
          <ModalBox onClick={(e) => e.stopPropagation()}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} pb={2} borderBottom="1px solid rgba(255, 255, 255, 0.1)">
              <Box display="flex" alignItems="center" gap={1.5}>
                <StoreIcon style={{ fontSize: "24px", color: "#A78BFA" }} />
                <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 800 }}>Vendor Store Profile</h3>
              </Box>
              <button 
                onClick={() => setSelectedVendor(null)}
                style={{ background: "transparent", border: "none", color: "#FFFFFF", cursor: "pointer" }}
              >
                <CloseIcon />
              </button>
            </Box>

            <Box display="flex" flexDirection="column" gap={3} fontSize="14px">
              <div>
                <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "rgba(255, 255, 255, 0.5)", display: "block" }}>
                  Store Name & Slug
                </span>
                <strong style={{ fontSize: "16px", color: "#FFFFFF" }}>{selectedVendor.storeName}</strong>
                <div style={{ color: "#A78BFA", fontSize: "12px" }}>/store/{selectedVendor.storeSlug}</div>
              </div>

              <div>
                <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "rgba(255, 255, 255, 0.5)", display: "block" }}>
                  Approval Status
                </span>
                <Box mt={0.5}>{renderStatusBadge(selectedVendor.status)}</Box>
              </div>

              <div>
                <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "rgba(255, 255, 255, 0.5)", display: "block" }}>
                  Store Description
                </span>
                <p style={{ margin: "4px 0 0 0", color: "rgba(255, 255, 255, 0.8)", lineHeight: 1.5 }}>
                  {selectedVendor.storeDescription || "No description provided."}
                </p>
              </div>
            </Box>

            <Box mt={5} pt={3} borderTop="1px solid rgba(255, 255, 255, 0.1)" display="flex" justifyContent="flex-end" gap={2}>
              <Button variant="secondary" onClick={() => setSelectedVendor(null)}>
                Close
              </Button>
            </Box>
          </ModalBox>
        </ModalBackdrop>
      )}

      {/* VENDOR ACTION MODAL */}
      {actionModal.open && (
        <ModalBackdrop onClick={() => !actionLoading && setActionModal({ open: false, type: null, vendor: null, reason: "" })}>
          <ModalBox onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: "0 0 8px 0", fontSize: "18px", fontWeight: 800 }}>
              {actionModal.type === "APPROVE" && `Approve Vendor Application?`}
              {actionModal.type === "REJECT" && `Reject Vendor Application`}
              {actionModal.type === "SUSPEND" && `Suspend Vendor Store?`}
              {actionModal.type === "REACTIVATE" && `Reactivate Vendor Store?`}
            </h3>

            <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "rgba(255, 255, 255, 0.7)", lineHeight: 1.5 }}>
              {actionModal.type === "APPROVE" && `Are you sure you want to approve "${actionModal.vendor?.storeName}"? This will grant full seller access to create and publish products.`}
              {actionModal.type === "REJECT" && `Please provide a reason for rejecting "${actionModal.vendor?.storeName}".`}
              {actionModal.type === "SUSPEND" && `Are you sure you want to suspend "${actionModal.vendor?.storeName}"? The vendor will be immediately blocked from managing products.`}
              {actionModal.type === "REACTIVATE" && `Are you sure you want to restore seller access for "${actionModal.vendor?.storeName}"?`}
            </p>

            {(actionModal.type === "REJECT" || actionModal.type === "SUSPEND") && (
              <Box mb={4}>
                <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "rgba(255, 255, 255, 0.6)", display: "block", marginBottom: "6px" }}>
                  Reason / Notes *
                </label>
                <textarea
                  rows={3}
                  value={actionModal.reason}
                  onChange={(e) => setActionModal({ ...actionModal, reason: e.target.value })}
                  style={{
                    width: "100%", padding: "10px 12px", borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                    color: "#FFFFFF", fontSize: "13px"
                  }}
                />
              </Box>
            )}

            <Box display="flex" justifyContent="flex-end" gap={2}>
              <Button 
                variant="secondary" 
                disabled={actionLoading}
                onClick={() => setActionModal({ open: false, type: null, vendor: null, reason: "" })}
              >
                Cancel
              </Button>
              {actionModal.type === "APPROVE" && (
                <Button variant="primary" loading={actionLoading} onClick={() => handleApproveVendor(actionModal.vendor.id || actionModal.vendor.userId)}>
                  Confirm Approval
                </Button>
              )}
              {actionModal.type === "REJECT" && (
                <Button variant="outline" loading={actionLoading} onClick={() => handleRejectVendor(actionModal.vendor.id || actionModal.vendor.userId, actionModal.reason)}>
                  Confirm Rejection
                </Button>
              )}
              {actionModal.type === "SUSPEND" && (
                <Button variant="primary" style={{ backgroundColor: "#EF4444" }} loading={actionLoading} onClick={() => handleSuspendVendor(actionModal.vendor.id || actionModal.vendor.userId, actionModal.reason)}>
                  Confirm Suspension
                </Button>
              )}
              {actionModal.type === "REACTIVATE" && (
                <Button variant="primary" loading={actionLoading} onClick={() => handleReactivateVendor(actionModal.vendor.id || actionModal.vendor.userId)}>
                  Confirm Reactivation
                </Button>
              )}
            </Box>
          </ModalBox>
        </ModalBackdrop>
      )}

      {/* PRODUCT TAKEDOWN MODAL */}
      {takedownModal.open && (
        <ModalBackdrop onClick={() => !takedownLoading && setTakedownModal({ open: false, product: null, reason: "" })}>
          <ModalBox onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: "0 0 8px 0", fontSize: "18px", fontWeight: 800 }}>
              Take Down Product Listing
            </h3>
            <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "rgba(255, 255, 255, 0.7)", lineHeight: 1.5 }}>
              Are you sure you want to deactivate and take down <strong>"{takedownModal.product?.title || takedownModal.product?.name}"</strong> from the public catalog?
            </p>

            <Box mb={4}>
              <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "rgba(255, 255, 255, 0.6)", display: "block", marginBottom: "6px" }}>
                Moderation Reason *
              </label>
              <textarea
                rows={3}
                placeholder="e.g. Inappropriate metadata, intellectual property infringement, or policy violation"
                value={takedownModal.reason}
                onChange={(e) => setTakedownModal({ ...takedownModal, reason: e.target.value })}
                style={{
                  width: "100%", padding: "10px 12px", borderRadius: "8px",
                  border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                  color: "#FFFFFF", fontSize: "13px"
                }}
              />
            </Box>

            <Box display="flex" justifyContent="flex-end" gap={2}>
              <Button 
                variant="secondary" 
                disabled={takedownLoading}
                onClick={() => setTakedownModal({ open: false, product: null, reason: "" })}
              >
                Cancel
              </Button>
              <Button 
                variant="primary" 
                style={{ backgroundColor: "#EF4444" }}
                loading={takedownLoading}
                onClick={handleTakedownProduct}
              >
                Confirm Takedown
              </Button>
            </Box>
          </ModalBox>
        </ModalBackdrop>
      )}
    </Box>
  );
};

export default AdminDashboard;
