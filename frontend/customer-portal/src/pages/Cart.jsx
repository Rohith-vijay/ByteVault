import React, { useState, useMemo } from "react";
import { useNavigate, Link } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Checkbox from "@mui/material/Checkbox";
import { AnimatePresence, motion } from "framer-motion";
import {
  DeleteOutlineOutlined as DeleteIcon,
  Bolt as BoltIcon,
  LocalShippingOutlined as LocalShippingIcon,
  ArrowForward as ArrowForwardIcon,
  LockOutlined as LockIcon,
  CheckCircle as CheckIcon,
  ShoppingCartCheckout as CheckoutIcon,
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { SectionHeader } from "../components/primitives/SectionHeader";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { IconButton } from "../components/primitives/IconButton";
import { Chip } from "../components/primitives/Chip";
import { Price } from "../components/primitives/Price";
import { EmptyState } from "../components/primitives/EmptyState";
import { Input } from "../components/primitives/Input";
import { useCart } from "../store/CartContext";
import { cartService } from "../services/cartService";

const CartLayout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "1fr 380px",
  gap: theme.spacing(8),
  paddingTop: theme.spacing(8),
  paddingBottom: theme.spacing(16),
  [theme.breakpoints.down("lg")]: { gridTemplateColumns: "1fr" },
}));

const CartItemRow = styled(motion.div)(({ theme, selected }) => ({
  display: "flex",
  gap: theme.spacing(4),
  padding: `${theme.spacing(5)} 0`,
  borderBottom: `1px solid ${theme.palette.border.default}`,
  alignItems: "center",
  transition: "background 0.15s",
  borderRadius: "8px",
  paddingLeft: "8px",
  paddingRight: "8px",
  background: selected ? "rgba(139,92,246,0.05)" : "transparent",
  "&:last-child": { borderBottom: "none" },
  [theme.breakpoints.down("sm")]: {
    flexDirection: "column",
    alignItems: "flex-start",
    gap: theme.spacing(3),
  },
}));

const ThumbnailWrap = styled("div")(({ theme }) => ({
  width: "90px",
  height: "70px",
  borderRadius: theme.radius.md,
  overflow: "hidden",
  backgroundColor: theme.palette.background.elevated,
  flexShrink: 0,
  "& img": { width: "100%", height: "100%", objectFit: "cover" },
}));

const ItemDetails = styled("div")({
  flexGrow: 1,
  display: "flex",
  flexDirection: "column",
  gap: "4px",
});

const QuantityBox = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  border: `1px solid ${theme.palette.border.default}`,
  borderRadius: theme.radius.sm,
  overflow: "hidden",
  "& button": {
    border: "none",
    background: "transparent",
    padding: "4px 10px",
    cursor: "pointer",
    fontSize: "14px",
    fontWeight: 600,
    color: theme.palette.text.primary,
    "&:hover": { backgroundColor: theme.palette.background.elevated },
  },
  "& span": { padding: "0 8px", fontSize: "13px", fontWeight: 600 },
}));

const SummaryRow = styled("div")({
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  fontSize: "14px",
  marginBottom: "12px",
});

const SelectionBar = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  padding: `${theme.spacing(3)} ${theme.spacing(4)}`,
  marginBottom: theme.spacing(4),
  borderRadius: theme.radius.md,
  background: "rgba(139,92,246,0.08)",
  border: "1px solid rgba(139,92,246,0.2)",
  fontSize: "13px",
  fontWeight: 600,
}));

export const Cart = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { cartItems, totals: allTotals, updateQuantity, removeItem, applyPromoCode } = useCart();

  // Track which item IDs are selected for checkout
  const [selectedIds, setSelectedIds] = useState(() => new Set(cartItems.map((i) => i.id)));

  const [promoInput, setPromoInput] = useState("");
  const [promoMessage, setPromoMessage] = useState(null);

  // Keep selection in sync when cart changes
  React.useEffect(() => {
    setSelectedIds((prev) => {
      const existingIds = new Set(cartItems.map((i) => i.id));
      // Keep only IDs that still exist in cart
      const updated = new Set([...prev].filter((id) => existingIds.has(id)));
      // Auto-add any new items (e.g., just added)
      cartItems.forEach((item) => {
        if (!prev.has(item.id)) updated.add(item.id);
      });
      return updated;
    });
  }, [cartItems.length]); // eslint-disable-line

  const selectedItems = useMemo(
    () => cartItems.filter((item) => selectedIds.has(item.id)),
    [cartItems, selectedIds]
  );

  const selectedTotals = useMemo(
    () => cartService.calculateTotals(selectedItems),
    [selectedItems]
  );

  const allSelected = cartItems.length > 0 && selectedIds.size === cartItems.length;
  const noneSelected = selectedIds.size === 0;

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(cartItems.map((i) => i.id)));
    }
  };

  const toggleItem = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleCheckout = () => {
    if (selectedItems.length === 0) return;
    // Store selected items in session so Checkout page can retrieve them
    sessionStorage.setItem("bytevault_checkout_items", JSON.stringify(selectedItems));
    navigate("/checkout");
  };

  const handleApplyPromo = (e) => {
    e.preventDefault();
    if (!promoInput.trim()) return;
    if (applyPromoCode) {
      const res = applyPromoCode(promoInput);
      setPromoMessage(res?.success
        ? { type: "success", text: res.message || "Promo applied!" }
        : { type: "error", text: "Invalid promo code (Try 'BUILDER10')" });
    } else {
      setPromoMessage({ type: "success", text: "10% Builder Discount Applied" });
    }
  };

  if (cartItems.length === 0) {
    return (
      <Container maxWidth="md" style={{ paddingTop: "80px", paddingBottom: "80px" }}>
        <EmptyState
          title="Your Shopping Cart is Empty"
          description="You haven't added any digital blueprints or workspace hardware products yet."
          action={
            <Button variant="primary" onClick={() => navigate("/catalog")} rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}>
              Explore Marketplace
            </Button>
          }
        />
      </Container>
    );
  }

  return (
    <Box style={{ paddingTop: "24px", paddingBottom: "96px" }}>
      <Container maxWidth="xxl">
        <SectionHeader
          label="SHOPPING CART"
          title={`Review Your Cart (${cartItems.length} Item${cartItems.length !== 1 ? "s" : ""})`}
          subtitle="Select the items you'd like to purchase, then proceed to checkout."
        />

        <CartLayout>
          {/* Cart Items List */}
          <div>
            {/* Select All Bar */}
            <SelectionBar>
              <Box display="flex" alignItems="center" gap={1}>
                <Checkbox
                  checked={allSelected}
                  indeterminate={!allSelected && selectedIds.size > 0}
                  onChange={toggleSelectAll}
                  size="small"
                  sx={{
                    color: "rgba(139,92,246,0.5)",
                    "&.Mui-checked": { color: "#8B5CF6" },
                    "&.MuiCheckbox-indeterminate": { color: "#8B5CF6" },
                    padding: 0,
                    marginRight: "4px",
                  }}
                />
                <span style={{ color: theme.palette.text.primary }}>
                  {allSelected ? "Deselect All" : "Select All"}
                </span>
              </Box>
              <span style={{ color: theme.palette.text.secondary }}>
                {selectedIds.size} of {cartItems.length} selected
              </span>
            </SelectionBar>

            <Card padding={6} radius="xl" elevation="subtle">
              <AnimatePresence>
                {cartItems.map((item) => {
                  const isDigital = (item.type || "").toLowerCase() === "digital";
                  const isSelected = selectedIds.has(item.id);

                  return (
                    <CartItemRow
                      key={item.id}
                      selected={isSelected ? 1 : 0}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, height: 0, padding: 0 }}
                      transition={{ duration: 0.2 }}
                    >
                      {/* Item Selection Checkbox */}
                      <Checkbox
                        checked={isSelected}
                        onChange={() => toggleItem(item.id)}
                        size="small"
                        sx={{
                          color: "rgba(139,92,246,0.4)",
                          "&.Mui-checked": { color: "#8B5CF6" },
                          padding: "2px",
                          flexShrink: 0,
                        }}
                      />

                      <ThumbnailWrap>
                        <img src={item.image} alt={item.title} />
                      </ThumbnailWrap>

                      <ItemDetails>
                        <Box display="flex" gap={1.5} alignItems="center" mb={0.5}>
                          <Chip
                            label={isDigital ? "Digital Asset" : "Workspace Gear"}
                            color={isDigital ? "primary" : "accent"}
                            size="xs"
                            uppercase
                          />
                        </Box>

                        <Link
                          to={`/products/${item.id}`}
                          style={{
                            textDecoration: "none",
                            fontSize: "15px",
                            fontWeight: 600,
                            color: theme.palette.text.primary,
                            lineHeight: 1.35,
                          }}
                        >
                          {item.title}
                        </Link>

                        <Box display="flex" alignItems="center" gap={1} fontSize="11px" color={theme.palette.text.secondary} mt={0.5}>
                          {isDigital ? (
                            <>
                              <BoltIcon style={{ fontSize: "13px", color: "#F59E0B" }} />
                              <span>Instant Vault Release</span>
                            </>
                          ) : (
                            <>
                              <LocalShippingIcon style={{ fontSize: "13px", color: theme.palette.accent?.main || "#8B5CF6" }} />
                              <span>Ships in 1-2 business days</span>
                            </>
                          )}
                        </Box>
                      </ItemDetails>

                      <Box display="flex" alignItems="center" gap={4} ml={{ sm: "auto" }}>
                        {!isDigital ? (
                          <QuantityBox>
                            <button onClick={() => updateQuantity(item.id, item.quantity - 1)}>-</button>
                            <span>{item.quantity}</span>
                            <button onClick={() => updateQuantity(item.id, item.quantity + 1)}>+</button>
                          </QuantityBox>
                        ) : (
                          <span style={{ fontSize: "12px", color: theme.palette.text.muted, fontWeight: 500 }}>
                            1 License
                          </span>
                        )}

                        <Price amount={item.price * item.quantity} size="sm" />

                        <IconButton
                          aria-label="Remove item"
                          variant="ghost"
                          size="sm"
                          onClick={() => removeItem(item.id)}
                          style={{ color: theme.palette.text.muted }}
                        >
                          <DeleteIcon style={{ fontSize: "18px" }} />
                        </IconButton>
                      </Box>
                    </CartItemRow>
                  );
                })}
              </AnimatePresence>
            </Card>
          </div>

          {/* Order Summary Column */}
          <div>
            <Card padding={8} radius="xl" elevation="card">
              <h3 style={{ fontSize: "18px", fontWeight: 700, margin: "0 0 4px 0" }}>Order Summary</h3>
              <p style={{ fontSize: "12px", color: theme.palette.text.secondary, margin: "0 0 20px 0" }}>
                {noneSelected ? "Select items above to see pricing" : `${selectedIds.size} item${selectedIds.size !== 1 ? "s" : ""} selected`}
              </p>

              <SummaryRow>
                <span style={{ color: theme.palette.text.secondary }}>Subtotal</span>
                <span style={{ fontWeight: 600 }}>₹{selectedTotals.subtotal.toFixed(2)}</span>
              </SummaryRow>

              <SummaryRow>
                <span style={{ color: theme.palette.text.secondary }}>Estimated Shipping</span>
                <span style={{ fontWeight: 600, color: selectedTotals.shipping === 0 ? theme.palette.status?.success || "#10B981" : theme.palette.text.primary }}>
                  {selectedTotals.shipping === 0 ? "FREE" : `₹${selectedTotals.shipping.toFixed(2)}`}
                </span>
              </SummaryRow>

              <SummaryRow>
                <span style={{ color: theme.palette.text.secondary }}>Tax Estimate (8%)</span>
                <span style={{ fontWeight: 600 }}>₹{selectedTotals.tax.toFixed(2)}</span>
              </SummaryRow>

              <Divider style={{ margin: "16px 0" }} />

              <SummaryRow style={{ marginBottom: "24px" }}>
                <span style={{ fontSize: "16px", fontWeight: 700 }}>Total</span>
                <Price amount={selectedTotals.total} size="md" />
              </SummaryRow>

              {/* Promo Code Box */}
              <form onSubmit={handleApplyPromo} style={{ marginBottom: "20px" }}>
                <Box display="flex" gap={2}>
                  <Input
                    placeholder="Promo (e.g. BUILDER10)"
                    value={promoInput}
                    onChange={(e) => setPromoInput(e.target.value)}
                    fullWidth
                  />
                  <Button variant="secondary" size="sm" type="submit">
                    Apply
                  </Button>
                </Box>
                {promoMessage && (
                  <Box mt={1} fontSize="12px" color={promoMessage.type === "success" ? (theme.palette.status?.success || "#10B981") : theme.palette.error?.main || "#EF4444"}>
                    {promoMessage.text}
                  </Box>
                )}
              </form>

              <Button
                variant="primary"
                size="lg"
                fullWidth
                disabled={noneSelected}
                onClick={handleCheckout}
                rightIcon={<CheckoutIcon style={{ fontSize: "18px" }} />}
                style={{ opacity: noneSelected ? 0.5 : 1, cursor: noneSelected ? "not-allowed" : "pointer" }}
              >
                {noneSelected
                  ? "Select Items to Checkout"
                  : `Checkout (${selectedIds.size} Item${selectedIds.size !== 1 ? "s" : ""})`}
              </Button>

              <Box mt={4} pt={4} borderTop={`1px solid ${theme.palette.border.default}`} display="flex" flexDirection="column" gap={1.5} fontSize="12px" color={theme.palette.text.secondary}>
                <Box display="flex" alignItems="center" gap={1}>
                  <CheckIcon style={{ fontSize: "15px", color: theme.palette.status?.success || "#10B981" }} />
                  <span>14-Day Money Back Technical Guarantee</span>
                </Box>
                <Box display="flex" alignItems="center" gap={1}>
                  <LockIcon style={{ fontSize: "15px", color: theme.palette.accent?.main || "#8B5CF6" }} />
                  <span>256-Bit SSL Encrypted Payment</span>
                </Box>
              </Box>
            </Card>
          </div>
        </CartLayout>
      </Container>
    </Box>
  );
};

export default Cart;
