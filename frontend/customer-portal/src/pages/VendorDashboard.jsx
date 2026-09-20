import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import {
  StorefrontOutlined as StoreIcon,
  HourglassTopOutlined as PendingIcon,
  CheckCircle as ApprovedIcon,
  BlockOutlined as SuspendedIcon,
  HighlightOffOutlined as RejectedIcon,
  EditOutlined as EditIcon,
  HeadsetMicOutlined as SupportIcon,
  Inventory2Outlined as InventoryIcon,
  Add as AddIcon,
  PublishOutlined as PublishIcon,
  VisibilityOffOutlined as DeactivateIcon,
  Refresh as RefreshIcon,
  Close as CloseIcon,
  LayersOutlined as DigitalIcon,
  LocalShippingOutlined as PhysicalIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { Skeleton } from "../components/primitives/Skeleton";
import { useAuth } from "../store/AuthContext";
import { vendorService } from "../services/vendorService";
import { productService } from "../services/productService";

const Layout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "240px 1fr",
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
  gap: "12px",
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

  "&:hover": {
    backgroundColor: "rgba(255, 255, 255, 0.06)",
    color: "#FFFFFF",
  },
}));

const StatusBanner = styled("div", {
  shouldForwardProp: (prop) => prop !== "variant",
})(({ theme, variant }) => ({
  borderRadius: "16px",
  padding: theme.spacing(8),
  border: variant === "warning"
    ? "1px solid rgba(245, 158, 11, 0.3)"
    : variant === "error"
    ? "1px solid rgba(239, 68, 68, 0.3)"
    : "1px solid rgba(16, 185, 129, 0.3)",
  background: variant === "warning"
    ? "linear-gradient(180deg, rgba(245, 158, 11, 0.08) 0%, rgba(15, 23, 42, 0.95) 100%)"
    : variant === "error"
    ? "linear-gradient(180deg, rgba(239, 68, 68, 0.08) 0%, rgba(15, 23, 42, 0.95) 100%)"
    : "linear-gradient(180deg, rgba(16, 185, 129, 0.08) 0%, rgba(15, 23, 42, 0.95) 100%)",
  color: "#FFFFFF",
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(4),
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
  maxWidth: "640px",
  width: "100%",
  boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.75)",
  maxHeight: "90vh",
  overflowY: "auto",
}));

export const VendorDashboard = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState("products"); // "products" | "overview" | "profile"
  const [vendorProfile, setVendorProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  // Products State
  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productFilter, setProductFilter] = useState("ALL");
  const [productModal, setProductModal] = useState({ open: false, isEdit: false, id: null });
  const [productSubmitting, setProductSubmitting] = useState(false);
  const [availableCategories, setAvailableCategories] = useState([]);

  // Product Form
  const [productForm, setProductForm] = useState({
    name: "",
    description: "",
    price: "",
    currency: "INR",
    productType: "DIGITAL",
    categoryId: null,
    categoryName: "Developer Software",
    tags: "",
    // Digital fields
    fileName: "",
    fileType: "ZIP / Code",
    fileVersion: "1.0.0",
    // Physical fields
    sku: "",
    weight: "",
    length: "",
    width: "",
    height: "",
    shippingClass: "Standard Ground"
  });

  // Profile Edit State
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({
    storeName: "",
    storeDescription: "",
    businessTaxId: "",
    supportEmail: "",
    payoutInfo: ""
  });
  const [saveLoading, setSaveLoading] = useState(false);

  const fetchProfile = async () => {
    setLoading(true);
    try {
      const data = await vendorService.getMyVendorProfile();
      setVendorProfile(data);
      if (data) {
        setEditForm({
          storeName: data.storeName || "",
          storeDescription: data.storeDescription || "",
          businessTaxId: data.businessTaxId || "",
          supportEmail: data.supportEmail || "",
          payoutInfo: data.payoutInfo || ""
        });
      }
    } catch (err) {
      console.warn("Failed fetching vendor profile", err);
    } finally {
      setLoading(false);
    }
  };

  const fetchVendorProducts = async () => {
    setProductsLoading(true);
    try {
      const data = await productService.getMyProducts();
      setProducts(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn("Failed fetching vendor products", err);
    } finally {
      setProductsLoading(false);
    }
  };

  const fetchCategories = async () => {
    try {
      const cats = await productService.getCategoriesList();
      if (Array.isArray(cats) && cats.length > 0) {
        setAvailableCategories(cats);
      }
    } catch (err) {
      console.warn("Failed fetching categories", err);
    }
  };

  useEffect(() => {
    fetchProfile();
    fetchVendorProducts();
    fetchCategories();
  }, []);

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSaveLoading(true);
    try {
      const updated = await vendorService.updateMyVendorProfile(editForm);
      setVendorProfile(updated);
      setIsEditing(false);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Store profile updated successfully!", type: "success" }
        })
      );
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to update profile", type: "error" }
        })
      );
    } finally {
      setSaveLoading(false);
    }
  };

  const openCreateModal = () => {
    const defaultCat = availableCategories.length > 0 ? availableCategories[0] : null;
    setProductForm({
      name: "",
      description: "",
      price: "",
      currency: "INR",
      productType: "DIGITAL",
      categoryId: defaultCat ? defaultCat.id : null,
      categoryName: defaultCat ? defaultCat.name : "Developer Software",
      tags: "",
      fileName: "",
      fileType: "ZIP / Code",
      fileVersion: "1.0.0",
      sku: `BV-${Math.random().toString(36).substring(2, 7).toUpperCase()}`,
      weight: "0.5",
      length: "20",
      width: "15",
      height: "5",
      shippingClass: "Standard Ground"
    });
    setProductModal({ open: true, isEdit: false, id: null });
  };

  const handleCreateOrUpdateProduct = async (publishNow) => {
    if (!productForm.name || !productForm.price) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Please fill in all required product fields.", type: "error" }
        })
      );
      return;
    }

    setProductSubmitting(true);
    try {
      let resolvedCategoryId = productForm.categoryId;
      if (resolvedCategoryId == null && availableCategories.length > 0) {
        const found = availableCategories.find(c => c.name === productForm.categoryName);
        resolvedCategoryId = found ? found.id : availableCategories[0].id;
      }

      const payload = {
        name: productForm.name,
        description: productForm.description,
        price: parseFloat(productForm.price),
        currency: productForm.currency,
        productType: productForm.productType,
        categoryId: resolvedCategoryId != null ? Number(resolvedCategoryId) : null,
        tags: productForm.tags,
        ...(productForm.productType === "DIGITAL" ? {
          fileName: productForm.fileName || `${productForm.name.toLowerCase().replace(/\s+/g, "-")}.zip`,
          fileType: productForm.fileType,
          fileVersion: productForm.fileVersion
        } : {
          sku: productForm.sku,
          physicalSku: productForm.sku,
          weight: parseFloat(productForm.weight) || 0.1,
          length: parseFloat(productForm.length) || 10,
          width: parseFloat(productForm.width) || 10,
          height: parseFloat(productForm.height) || 5,
          shippingClass: productForm.shippingClass
        })
      };

      if (productModal.isEdit) {
        await productService.updateVendorProduct(productModal.id, payload);
        window.dispatchEvent(
          new CustomEvent("bytevault_toast", {
            detail: { message: "Product updated successfully!", type: "success" }
          })
        );
      } else {
        await productService.createVendorProduct(payload, publishNow);
        window.dispatchEvent(
          new CustomEvent("bytevault_toast", {
            detail: {
              message: publishNow
                ? "Product published directly to marketplace catalog!"
                : "Draft saved successfully.",
              type: "success"
            }
          })
        );
      }

      setProductModal({ open: false, isEdit: false, id: null });
      await fetchVendorProducts();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to save product", type: "error" }
        })
      );
    } finally {
      setProductSubmitting(false);
    }
  };

  const handlePublishProduct = async (id) => {
    try {
      await productService.publishVendorProduct(id);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Product published to marketplace catalog!", type: "success" }
        })
      );
      await fetchVendorProducts();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to publish product", type: "error" }
        })
      );
    }
  };

  const handleDeactivateProduct = async (id) => {
    try {
      await productService.deactivateVendorProduct(id);
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Product deactivated from live catalog.", type: "info" }
        })
      );
      await fetchVendorProducts();
    } catch (err) {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: err.message || "Failed to deactivate product", type: "error" }
        })
      );
    }
  };

  if (loading) {
    return (
      <Box py={10} backgroundColor="#F8FAFC" minHeight="80vh">
        <Container maxWidth="lg">
          <Skeleton variant="rectangular" height={80} width="60%" style={{ marginBottom: "24px" }} />
          <Skeleton variant="rectangular" height={300} width="100%" />
        </Container>
      </Box>
    );
  }

  const status = vendorProfile?.status || "PENDING_APPROVAL";

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
                Vendor Workspace
              </h1>
              {status === "APPROVED" && <Chip label="APPROVED SELLER" color="success" size="xs" uppercase />}
              {status === "PENDING_APPROVAL" && <Chip label="AWAITING APPROVAL" color="warning" size="xs" uppercase />}
              {status === "SUSPENDED" && <Chip label="SUSPENDED" color="error" size="xs" uppercase />}
              {status === "REJECTED" && <Chip label="NOT APPROVED" color="neutral" size="xs" uppercase />}
            </Box>
            <span style={{ fontSize: "14px", color: theme.palette.text.secondary }}>
              Store: <strong>{vendorProfile?.storeName || user?.name || "Seller"}</strong> ({user?.email})
            </span>
          </div>
          <Box display="flex" gap={2}>
            {status === "APPROVED" && (
              <Button variant="primary" size="sm" onClick={openCreateModal} leftIcon={<AddIcon style={{ fontSize: "16px" }} />}>
                Add New Product
              </Button>
            )}
            <Button variant="secondary" size="sm" onClick={() => { fetchProfile(); fetchVendorProducts(); }} leftIcon={<RefreshIcon style={{ fontSize: "16px" }} />}>
              Refresh Records
            </Button>
            <Button variant="secondary" size="sm" onClick={() => navigate("/catalog")}>
              Marketplace Storefront
            </Button>
          </Box>
        </Box>

        {/* 1. NON-APPROVED STATES: PENDING / SUSPENDED / REJECTED */}
        {status !== "APPROVED" ? (
          <Box maxWidth="850px" mx="auto">
            {/* PENDING APPROVAL STATE */}
            {status === "PENDING_APPROVAL" && (
              <StatusBanner variant="warning">
                <Box display="flex" alignItems="flex-start" gap={3}>
                  <Box p={2} borderRadius="12px" backgroundColor="rgba(245, 158, 11, 0.18)" color="#F59E0B">
                    <PendingIcon style={{ fontSize: "36px" }} />
                  </Box>
                  <div>
                    <h2 style={{ margin: "0 0 6px 0", fontSize: "22px", fontWeight: 800 }}>
                      Vendor Application Under Review
                    </h2>
                    <p style={{ margin: 0, fontSize: "14px", color: "rgba(255, 255, 255, 0.8)", lineHeight: 1.6 }}>
                      Your seller account has been registered and is currently in <strong>PENDING_APPROVAL</strong> status. A ByteVault platform administrator will review your store credentials. Once approved, you can directly create, edit, and publish products.
                    </p>
                  </div>
                </Box>
              </StatusBanner>
            )}

            {/* SUSPENDED STATE */}
            {status === "SUSPENDED" && (
              <StatusBanner variant="error">
                <Box display="flex" alignItems="flex-start" gap={3}>
                  <Box p={2} borderRadius="12px" backgroundColor="rgba(239, 68, 68, 0.2)" color="#EF4444">
                    <SuspendedIcon style={{ fontSize: "36px" }} />
                  </Box>
                  <div>
                    <h2 style={{ margin: "0 0 6px 0", fontSize: "22px", fontWeight: 800 }}>
                      Vendor Store Suspended
                    </h2>
                    <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "rgba(255, 255, 255, 0.8)", lineHeight: 1.6 }}>
                      Your seller workspace and product publishing privileges have been suspended by marketplace administration.
                    </p>
                    {vendorProfile?.suspensionReason && (
                      <Box p={3} borderRadius="8px" backgroundColor="rgba(239, 68, 68, 0.15)" border="1px solid #EF4444" mb={3}>
                        <strong style={{ fontSize: "12px", color: "#FCA5A5", display: "block", marginBottom: "4px" }}>
                          Reason for Suspension:
                        </strong>
                        <span style={{ fontSize: "14px" }}>{vendorProfile.suspensionReason}</span>
                      </Box>
                    )}
                    <Button variant="secondary" size="sm" onClick={() => navigate("/account?tab=support")} leftIcon={<SupportIcon style={{ fontSize: "16px" }} />}>
                      Contact Support
                    </Button>
                  </div>
                </Box>
              </StatusBanner>
            )}

            {/* REJECTED STATE */}
            {status === "REJECTED" && (
              <StatusBanner variant="error">
                <Box display="flex" alignItems="flex-start" gap={3}>
                  <Box p={2} borderRadius="12px" backgroundColor="rgba(239, 68, 68, 0.2)" color="#EF4444">
                    <RejectedIcon style={{ fontSize: "36px" }} />
                  </Box>
                  <div>
                    <h2 style={{ margin: "0 0 6px 0", fontSize: "22px", fontWeight: 800 }}>
                      Vendor Application Not Approved
                    </h2>
                    <p style={{ margin: "0 0 16px 0", fontSize: "14px", color: "rgba(255, 255, 255, 0.8)", lineHeight: 1.6 }}>
                      Your seller onboarding application could not be approved at this time.
                    </p>
                    {vendorProfile?.rejectionReason && (
                      <Box p={3} borderRadius="8px" backgroundColor="rgba(239, 68, 68, 0.15)" border="1px solid #EF4444" mb={3}>
                        <strong style={{ fontSize: "12px", color: "#FCA5A5", display: "block", marginBottom: "4px" }}>
                          Review Feedback:
                        </strong>
                        <span style={{ fontSize: "14px" }}>{vendorProfile.rejectionReason}</span>
                      </Box>
                    )}
                  </div>
                </Box>
              </StatusBanner>
            )}
          </Box>
        ) : (
          /* 2. APPROVED VENDOR SELLER WORKSPACE */
          <Layout>
            <Sidebar>
              <Box p={2} mb={2}>
                <div style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.4)", textTransform: "uppercase", letterSpacing: "0.08em" }}>
                  SELLER SECTIONS
                </div>
              </Box>
              <TabBtn active={activeTab === "products"} onClick={() => setActiveTab("products")}>
                <InventoryIcon style={{ fontSize: "18px", color: "#7C3AED" }} />
                <span>Products & Catalog</span>
              </TabBtn>
              <TabBtn active={activeTab === "overview"} onClick={() => setActiveTab("overview")}>
                <StoreIcon style={{ fontSize: "18px" }} />
                <span>Studio Overview</span>
              </TabBtn>
              <TabBtn active={activeTab === "profile"} onClick={() => setActiveTab("profile")}>
                <ApprovedIcon style={{ fontSize: "18px", color: "#10B981" }} />
                <span>Store Profile</span>
              </TabBtn>
            </Sidebar>

            <div>
              {/* TAB 1: PRODUCTS & CATALOG */}
              {activeTab === "products" && (
                <Box display="flex" flexDirection="column" gap={6}>
                  <Card padding={6} radius="lg" elevation="subtle">
                    <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} flexWrap="wrap" gap={3}>
                      <div>
                        <h2 style={{ margin: "0 0 4px 0", fontSize: "20px", fontWeight: 800, color: theme.palette.text.primary }}>
                          My Products & Listings ({products.length})
                        </h2>
                        <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                          Direct publishing active. Create digital assets or physical products with direct marketplace availability.
                        </p>
                      </div>

                      {/* Filter chips */}
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

                    {productsLoading ? (
                      <Skeleton variant="rectangular" height={150} width="100%" />
                    ) : filteredProducts.length === 0 ? (
                      <Box textAlign="center" py={10} px={4} backgroundColor="#F8FAFC" borderRadius="12px" border="1px dashed #CBD5E1">
                        <InventoryIcon style={{ fontSize: "40px", color: "#94A3B8", marginBottom: "8px" }} />
                        <h3 style={{ margin: "0 0 6px 0", fontSize: "16px", fontWeight: 700 }}>
                          No products found in this view
                        </h3>
                        <p style={{ margin: "0 0 16px 0", fontSize: "13px", color: theme.palette.text.secondary }}>
                          Create your first digital download or physical product to start selling.
                        </p>
                        <Button variant="primary" size="sm" onClick={openCreateModal} leftIcon={<AddIcon style={{ fontSize: "16px" }} />}>
                          Create Product
                        </Button>
                      </Box>
                    ) : (
                      <TableWrapper>
                        <table>
                          <thead>
                            <tr>
                              <th>Product</th>
                              <th>Type</th>
                              <th>Category</th>
                              <th>Price</th>
                              <th>Status</th>
                              <th style={{ textAlign: "right" }}>Actions</th>
                            </tr>
                          </thead>
                          <tbody>
                            {filteredProducts.map((p) => (
                              <tr key={p.id}>
                                <td>
                                  <strong style={{ color: theme.palette.text.primary }}>{p.title || p.name}</strong>
                                  <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>
                                    {p.type === "DIGITAL" ? `Format: ${p.specs?.format || "Digital Asset"}` : `SKU: ${p.specs?.sku || "Physical Item"}`}
                                  </div>
                                </td>
                                <td>
                                  <Chip label={p.type || p.productType} color={p.type === "DIGITAL" ? "primary" : "accent"} size="xs" uppercase />
                                </td>
                                <td>{p.categoryName || p.category || "—"}</td>
                                <td><strong>₹{p.price?.toFixed(2)}</strong></td>
                                <td>
                                  {p.status === "PUBLISHED" && <Chip label="PUBLISHED" color="success" size="xs" uppercase />}
                                  {p.status === "DRAFT" && <Chip label="DRAFT" color="warning" size="xs" uppercase />}
                                  {p.status === "DEACTIVATED" && <Chip label="DEACTIVATED" color="neutral" size="xs" uppercase />}
                                </td>
                                <td style={{ textAlign: "right" }}>
                                  <Box display="flex" gap={1} justifyContent="flex-end">
                                    {p.status === "DRAFT" && (
                                      <Button size="xs" variant="primary" onClick={() => handlePublishProduct(p.id)} leftIcon={<PublishIcon style={{ fontSize: "14px" }} />}>
                                        Publish
                                      </Button>
                                    )}
                                    {p.status === "PUBLISHED" && (
                                      <Button size="xs" variant="outline" onClick={() => handleDeactivateProduct(p.id)} leftIcon={<DeactivateIcon style={{ fontSize: "14px" }} />}>
                                        Deactivate
                                      </Button>
                                    )}
                                    {p.status === "DEACTIVATED" && (
                                      <Button size="xs" variant="primary" onClick={() => handlePublishProduct(p.id)}>
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

              {/* TAB 2: OVERVIEW */}
              {activeTab === "overview" && (
                <Box display="flex" flexDirection="column" gap={6}>
                  <Card padding={6} radius="lg" elevation="subtle">
                    <Box display="flex" alignItems="center" gap={3} mb={4}>
                      <Box p={2} borderRadius="12px" backgroundColor="rgba(16, 185, 129, 0.12)" color="#10B981">
                        <ApprovedIcon style={{ fontSize: "32px" }} />
                      </Box>
                      <div>
                        <h2 style={{ margin: "0 0 4px 0", fontSize: "20px", fontWeight: 800, color: theme.palette.text.primary }}>
                          Seller Privileges Active
                        </h2>
                        <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                          Your vendor account is authorized for direct product creation and publishing.
                        </p>
                      </div>
                    </Box>

                    <Grid container spacing={4}>
                      <Grid item xs={12} sm={4}>
                        <Box p={4} borderRadius="12px" backgroundColor="#F8FAFC" border="1px solid #E2E8F0">
                          <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                            Published Products
                          </span>
                          <div style={{ fontSize: "22px", fontWeight: 800, color: "#10B981", marginTop: "4px" }}>
                            {products.filter(p => p.status === "PUBLISHED").length}
                          </div>
                        </Box>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Box p={4} borderRadius="12px" backgroundColor="#F8FAFC" border="1px solid #E2E8F0">
                          <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                            Draft Products
                          </span>
                          <div style={{ fontSize: "22px", fontWeight: 800, color: "#F59E0B", marginTop: "4px" }}>
                            {products.filter(p => p.status === "DRAFT").length}
                          </div>
                        </Box>
                      </Grid>
                      <Grid item xs={12} sm={4}>
                        <Box p={4} borderRadius="12px" backgroundColor="#F8FAFC" border="1px solid #E2E8F0">
                          <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: theme.palette.text.muted }}>
                            Marketplace Split
                          </span>
                          <div style={{ fontSize: "22px", fontWeight: 800, color: theme.palette.text.primary, marginTop: "4px" }}>
                            90% Net
                          </div>
                        </Box>
                      </Grid>
                    </Grid>
                  </Card>
                </Box>
              )}

              {/* TAB 3: PROFILE */}
              {activeTab === "profile" && (
                <Card padding={6} radius="lg" elevation="subtle">
                  <h2 style={{ margin: "0 0 16px 0", fontSize: "20px", fontWeight: 800 }}>
                    Store Settings & Credentials
                  </h2>
                  <Grid container spacing={3} fontSize="14px">
                    <Grid item xs={12} sm={6}>
                      <span style={{ fontSize: "11px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase", display: "block" }}>
                        Store Name
                      </span>
                      <strong>{vendorProfile?.storeName}</strong>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <span style={{ fontSize: "11px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase", display: "block" }}>
                        Public Store Slug
                      </span>
                      <code>/store/{vendorProfile?.storeSlug}</code>
                    </Grid>
                    <Grid item xs={12}>
                      <span style={{ fontSize: "11px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase", display: "block" }}>
                        Description
                      </span>
                      <span>{vendorProfile?.storeDescription || "No description provided."}</span>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <span style={{ fontSize: "11px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase", display: "block" }}>
                        Business Tax ID
                      </span>
                      <span>{vendorProfile?.businessTaxId || "—"}</span>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <span style={{ fontSize: "11px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase", display: "block" }}>
                        Support Email
                      </span>
                      <span>{vendorProfile?.supportEmail || "—"}</span>
                    </Grid>
                  </Grid>
                </Card>
              )}
            </div>
          </Layout>
        )}
      </Container>

      {/* CREATE / EDIT PRODUCT MODAL */}
      {productModal.open && (
        <ModalBackdrop onClick={() => !productSubmitting && setProductModal({ open: false, isEdit: false, id: null })}>
          <ModalBox onClick={(e) => e.stopPropagation()}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4} pb={2} borderBottom="1px solid rgba(255, 255, 255, 0.1)">
              <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 800 }}>
                {productModal.isEdit ? "Edit Product" : "Add New Marketplace Product"}
              </h3>
              <button 
                onClick={() => setProductModal({ open: false, isEdit: false, id: null })}
                style={{ background: "transparent", border: "none", color: "#FFFFFF", cursor: "pointer" }}
              >
                <CloseIcon />
              </button>
            </Box>

            {/* Type Selector */}
            <Box display="flex" gap={2} mb={4}>
              <button
                type="button"
                onClick={() => setProductForm({ ...productForm, productType: "DIGITAL" })}
                style={{
                  flex: 1,
                  padding: "12px",
                  borderRadius: "10px",
                  border: productForm.productType === "DIGITAL" ? "2px solid #7C3AED" : "1px solid rgba(255, 255, 255, 0.2)",
                  backgroundColor: productForm.productType === "DIGITAL" ? "rgba(124, 58, 237, 0.2)" : "rgba(255, 255, 255, 0.05)",
                  color: "#FFFFFF",
                  fontWeight: 700,
                  fontSize: "13px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                  cursor: "pointer"
                }}
              >
                <DigitalIcon style={{ fontSize: "18px", color: "#A78BFA" }} />
                <span>DIGITAL ASSET</span>
              </button>

              <button
                type="button"
                onClick={() => setProductForm({ ...productForm, productType: "PHYSICAL" })}
                style={{
                  flex: 1,
                  padding: "12px",
                  borderRadius: "10px",
                  border: productForm.productType === "PHYSICAL" ? "2px solid #7C3AED" : "1px solid rgba(255, 255, 255, 0.2)",
                  backgroundColor: productForm.productType === "PHYSICAL" ? "rgba(124, 58, 237, 0.2)" : "rgba(255, 255, 255, 0.05)",
                  color: "#FFFFFF",
                  fontWeight: 700,
                  fontSize: "13px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "8px",
                  cursor: "pointer"
                }}
              >
                <PhysicalIcon style={{ fontSize: "18px", color: "#10B981" }} />
                <span>PHYSICAL GOOD</span>
              </button>
            </Box>

            {/* Form Fields */}
            <Box display="flex" flexDirection="column" gap={3}>
              <div>
                <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                  Product Name *
                </label>
                <input
                  type="text"
                  placeholder="e.g. Cloud Security Architecture Blueprint"
                  value={productForm.name}
                  onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                  style={{
                    width: "100%", padding: "10px 12px", borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                    color: "#FFFFFF", fontSize: "14px"
                  }}
                />
              </div>

              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                    Price (INR ₹) *
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    min="0.01"
                    placeholder="499.00"
                    value={productForm.price}
                    onChange={(e) => setProductForm({ ...productForm, price: e.target.value })}
                    style={{
                      width: "100%", padding: "10px 12px", borderRadius: "8px",
                      border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                      color: "#FFFFFF", fontSize: "14px"
                    }}
                  />
                </Grid>

                <Grid item xs={6}>
                  <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                    Category
                  </label>
                  <select
                    value={productForm.categoryId != null ? productForm.categoryId : ""}
                    onChange={(e) => {
                      const selectedVal = e.target.value;
                      const cat = availableCategories.find(c => String(c.id) === String(selectedVal));
                      setProductForm({
                        ...productForm,
                        categoryId: cat ? cat.id : (selectedVal ? Number(selectedVal) : null),
                        categoryName: cat ? cat.name : productForm.categoryName
                      });
                    }}
                    style={{
                      width: "100%", padding: "10px 12px", borderRadius: "8px",
                      border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "#0B1020",
                      color: "#FFFFFF", fontSize: "14px"
                    }}
                  >
                    {availableCategories.length > 0 ? (
                      availableCategories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))
                    ) : (
                      <>
                        <option value="1">E-Books</option>
                        <option value="2">Developer Software</option>
                        <option value="3">Hardware & Security</option>
                        <option value="4">Audio & Media</option>
                      </>
                    )}
                  </select>
                </Grid>
              </Grid>

              <div>
                <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                  Description
                </label>
                <textarea
                  rows={3}
                  placeholder="Detailed overview of product specifications, deliverables, and architecture..."
                  value={productForm.description}
                  onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                  style={{
                    width: "100%", padding: "10px 12px", borderRadius: "8px",
                    border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                    color: "#FFFFFF", fontSize: "14px"
                  }}
                />
              </div>

              {/* Dynamic type fields */}
              {productForm.productType === "DIGITAL" ? (
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                      File Format / Type
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. ZIP (React, TS, Docker)"
                      value={productForm.fileType}
                      onChange={(e) => setProductForm({ ...productForm, fileType: e.target.value })}
                      style={{
                        width: "100%", padding: "10px 12px", borderRadius: "8px",
                        border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                        color: "#FFFFFF", fontSize: "14px"
                      }}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                      Release Version
                    </label>
                    <input
                      type="text"
                      placeholder="1.0.0"
                      value={productForm.fileVersion}
                      onChange={(e) => setProductForm({ ...productForm, fileVersion: e.target.value })}
                      style={{
                        width: "100%", padding: "10px 12px", borderRadius: "8px",
                        border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                        color: "#FFFFFF", fontSize: "14px"
                      }}
                    />
                  </Grid>
                </Grid>
              ) : (
                <>
                  <Grid container spacing={2}>
                    <Grid item xs={6}>
                      <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                        Physical SKU *
                      </label>
                      <input
                        type="text"
                        placeholder="BV-KEY-001"
                        value={productForm.sku}
                        onChange={(e) => setProductForm({ ...productForm, sku: e.target.value })}
                        style={{
                          width: "100%", padding: "10px 12px", borderRadius: "8px",
                          border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                          color: "#FFFFFF", fontSize: "14px"
                        }}
                      />
                    </Grid>
                    <Grid item xs={6}>
                      <label style={{ fontSize: "11px", fontWeight: 700, color: "rgba(255, 255, 255, 0.6)", textTransform: "uppercase", display: "block", marginBottom: "4px" }}>
                        Weight (kg)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        placeholder="0.25"
                        value={productForm.weight}
                        onChange={(e) => setProductForm({ ...productForm, weight: e.target.value })}
                        style={{
                          width: "100%", padding: "10px 12px", borderRadius: "8px",
                          border: "1px solid rgba(255, 255, 255, 0.2)", backgroundColor: "rgba(255, 255, 255, 0.05)",
                          color: "#FFFFFF", fontSize: "14px"
                        }}
                      />
                    </Grid>
                  </Grid>
                </>
              )}

              <Box display="flex" justifyContent="flex-end" gap={2} mt={3} pt={3} borderTop="1px solid rgba(255, 255, 255, 0.1)">
                <Button 
                  variant="secondary" 
                  disabled={productSubmitting}
                  onClick={() => setProductModal({ open: false, isEdit: false, id: null })}
                >
                  Cancel
                </Button>
                <Button 
                  variant="outline" 
                  loading={productSubmitting}
                  onClick={() => handleCreateOrUpdateProduct(false)}
                >
                  Save as Draft
                </Button>
                <Button 
                  variant="primary" 
                  loading={productSubmitting}
                  onClick={() => handleCreateOrUpdateProduct(true)}
                  leftIcon={<PublishIcon style={{ fontSize: "16px" }} />}
                >
                  Publish Directly
                </Button>
              </Box>
            </Box>
          </ModalBox>
        </ModalBackdrop>
      )}
    </Box>
  );
};

export default VendorDashboard;
