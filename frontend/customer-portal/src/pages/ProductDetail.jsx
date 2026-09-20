import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Accordion from "@mui/material/Accordion";
import AccordionSummary from "@mui/material/AccordionSummary";
import AccordionDetails from "@mui/material/AccordionDetails";
import {
  ShoppingBagOutlined as ShoppingBagIcon,
  FavoriteBorder as FavoriteBorderIcon,
  Favorite as FavoriteIcon,
  Bolt as BoltIcon,
  LocalShippingOutlined as LocalShippingIcon,
  ArrowBack as ArrowBackIcon,
  CheckCircle as CheckIcon,
  LockOutlined as LockIcon,
  ExpandMore as ExpandMoreIcon,
  VerifiedUserOutlined as VerifiedIcon,
  ShoppingCart as CartIcon,
  ArrowForward as ArrowForwardIcon,
  Close as CloseIcon
} from "@mui/icons-material";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";

import { Container } from "../components/primitives/Container";
import { Button } from "../components/primitives/Button";
import { IconButton } from "../components/primitives/IconButton";
import { SectionHeader } from "../components/primitives/SectionHeader";
import { Chip } from "../components/primitives/Chip";
import { Rating } from "../components/primitives/Rating";
import { Price } from "../components/primitives/Price";
import { Skeleton } from "../components/primitives/Skeleton";
import { Card } from "../components/primitives/Card";
import { ProductCard } from "../features/products/components/ProductCard/ProductCard";
import { useCart } from "../store/CartContext";
import { useWishlist } from "../store/WishlistContext";
import { productService } from "../services/productService";

const ShowcaseCanvas = styled("div", {
  shouldForwardProp: (prop) => prop !== "isDigital",
})(({ isDigital }) => ({
  backgroundColor: isDigital ? "#070B16" : "#FFFFFF",
  color: isDigital ? "#FFFFFF" : "#0F172A",
  borderRadius: "20px",
  border: isDigital ? "1px solid rgba(255, 255, 255, 0.12)" : "1px solid #E2E8F0",
  padding: "36px",
  position: "relative",
  overflow: "hidden",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  boxShadow: isDigital ? "0 20px 50px rgba(0, 0, 0, 0.5)" : "0 4px 20px rgba(15, 23, 42, 0.05)",
}));

const ProductImageWrap = styled("div")({
  width: "100%",
  maxHeight: "440px",
  display: "flex",
  justifyContent: "center",
  alignItems: "center",
  position: "relative",

  "& img": {
    maxWidth: "100%",
    maxHeight: "420px",
    objectFit: "contain",
    borderRadius: "12px",
    transition: "transform 0.3s ease",
    "&:hover": {
      transform: "scale(1.03)",
    }
  }
});

const StickySidebar = styled("div")(({ theme }) => ({
  position: "sticky",
  top: "96px",
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(6),
}));

const QuantitySelector = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  border: `1px solid ${theme.palette.border.default}`,
  borderRadius: theme.radius.md,
  overflow: "hidden",
  backgroundColor: theme.palette.background.paper,

  "& button": {
    border: "none",
    background: "transparent",
    padding: "8px 16px",
    fontSize: "16px",
    fontWeight: 600,
    cursor: "pointer",
    color: theme.palette.text.primary,
    "&:hover": {
      backgroundColor: theme.palette.background.elevated,
    }
  },

  "& span": {
    padding: "0 12px",
    fontSize: "14px",
    fontWeight: 600,
    minWidth: "32px",
    textAlign: "center",
  }
}));

const SpecGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
  gap: theme.spacing(4),
  marginTop: theme.spacing(4),
}));

const SpecItem = styled("div")(({ theme }) => ({
  padding: theme.spacing(4),
  backgroundColor: theme.palette.background.elevated,
  borderRadius: theme.radius.md,
  border: `1px solid ${theme.palette.border.default}`,
  display: "flex",
  flexDirection: "column",
  gap: "4px",

  "& .spec-key": {
    fontSize: "11px",
    fontWeight: 700,
    textTransform: "uppercase",
    letterSpacing: "0.06em",
    color: theme.palette.text.muted,
  },
  "& .spec-val": {
    fontSize: "14px",
    fontWeight: 600,
    color: theme.palette.text.primary,
    fontFamily: theme.typography.fontFamily,
  }
}));

const MobileDock = styled("div")(({ theme }) => ({
  position: "fixed",
  bottom: 0,
  left: 0,
  right: 0,
  backgroundColor: "#FFFFFF",
  borderTop: `1px solid ${theme.palette.border.default}`,
  padding: `${theme.spacing(3)} ${theme.spacing(6)}`,
  zIndex: 1000,
  display: "none",
  alignItems: "center",
  justifyContent: "space-between",
  boxShadow: "0 -8px 20px rgba(0, 0, 0, 0.08)",

  [theme.breakpoints.down("md")]: {
    display: "flex",
  },
}));

export const ProductDetail = () => {
  const { id } = useParams();
  const theme = useTheme();
  const navigate = useNavigate();

  const { addItem } = useCart();
  const { toggleWishlist, isWishlisted } = useWishlist();

  const [product, setProduct] = useState(null);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [cartBtnState, setCartBtnState] = useState("default");
  const [activeTab, setActiveTab] = useState(0);
  const [addedModalOpen, setAddedModalOpen] = useState(false);

  useEffect(() => {
    const fetchDetails = async () => {
      setLoading(true);
      setError(null);
      try {
        const details = await productService.getProductById(id);
        setProduct(details);
        setQuantity(1);

        try {
          const viewed = JSON.parse(localStorage.getItem("bytevault_recently_viewed") || "[]");
          const nextViewed = [details.id, ...viewed.filter(vid => vid !== details.id)].slice(0, 4);
          localStorage.setItem("bytevault_recently_viewed", JSON.stringify(nextViewed));
        } catch (e) {
          console.warn("Could not save recently viewed", e);
        }

        const recommendations = await productService.getRelatedProducts(details.id, 4);
        setRelated(recommendations);
      } catch (err) {
        setError(err.message || "Failed to load product.");
      } finally {
        setLoading(false);
      }
    };
    fetchDetails();
  }, [id]);

  if (loading) {
    return (
      <Container maxWidth="xxl" style={{ paddingTop: "40px", paddingBottom: "80px" }}>
        <Grid container spacing={8}>
          <Grid item xs={12} lg={7}>
            <Skeleton variant="rectangular" height={420} radius="xl" />
          </Grid>
          <Grid item xs={12} lg={5}>
            <Skeleton variant="text" width="40%" height={32} style={{ marginBottom: "12px" }} />
            <Skeleton variant="text" width="90%" height={48} style={{ marginBottom: "16px" }} />
            <Skeleton variant="text" width="60%" height={24} style={{ marginBottom: "24px" }} />
            <Skeleton variant="rectangular" height={160} radius="lg" />
          </Grid>
        </Grid>
      </Container>
    );
  }

  if (error || !product) {
    return (
      <Container maxWidth="md" style={{ paddingTop: "80px", paddingBottom: "80px", textAlign: "center" }}>
        <h2>Product Not Found</h2>
        <p style={{ color: theme.palette.text.secondary, marginBottom: "24px" }}>
          The requested engineering asset could not be located in our active database.
        </p>
        <Button variant="primary" onClick={() => navigate("/catalog")}>
          Back to Catalog
        </Button>
      </Container>
    );
  }

  const isDigital = (product.type || "").toLowerCase() === "digital";
  const isFav = isWishlisted(product.id);

  const handleAddToCart = () => {
    setCartBtnState("loading");
    setTimeout(() => {
      for (let i = 0; i < quantity; i++) {
        addItem(product);
      }
      setCartBtnState("success");
      setAddedModalOpen(true);
      setTimeout(() => setCartBtnState("default"), 1600);
    }, 300);
  };

  return (
    <Box style={{ paddingTop: "24px", paddingBottom: "96px" }}>
      <Container maxWidth="xxl">
        {/* Top Back Link */}
        <Box mb={4}>
          <Button 
            variant="ghost" 
            size="sm" 
            onClick={() => navigate("/catalog")}
            leftIcon={<ArrowBackIcon style={{ fontSize: "16px" }} />}
          >
            Back to Marketplace
          </Button>
        </Box>

        <Grid container spacing={8}>
          {/* Left Column: Product Showcase Canvas */}
          <Grid item xs={12} lg={7}>
            <ShowcaseCanvas isDigital={isDigital} className={isDigital ? "bv-tech-grid bv-glow-navy" : ""}>
              <Box 
                position="absolute" 
                top="20px" 
                left="20px" 
                display="flex" 
                gap={2} 
                zIndex={2}
              >
                {isDigital ? (
                  <Chip label="Digital Asset" color="primary" variant="filled" uppercase />
                ) : (
                  <Chip label="Workspace Gear" color="accent" variant="filled" uppercase />
                )}
                {!product.inStock && (
                  <Chip label="Backorder" color="error" variant="filled" uppercase />
                )}
              </Box>

              <ProductImageWrap>
                <img src={product.image} alt={product.title} />
              </ProductImageWrap>
            </ShowcaseCanvas>

            {/* Feature Highlights Strip */}
            <Box mt={6} display="flex" gap={4} flexWrap="wrap">
              <Box display="flex" alignItems="center" gap={1.5} fontSize="13px" color={theme.palette.text.secondary}>
                <VerifiedIcon style={{ color: theme.palette.primary.main, fontSize: "18px" }} />
                <span>Production Audited</span>
              </Box>
              <Box display="flex" alignItems="center" gap={1.5} fontSize="13px" color={theme.palette.text.secondary}>
                <BoltIcon style={{ color: "#F59E0B", fontSize: "18px" }} />
                <span>Instant Vault Entitlement</span>
              </Box>
              <Box display="flex" alignItems="center" gap={1.5} fontSize="13px" color={theme.palette.text.secondary}>
                <LockIcon style={{ color: "#10B981", fontSize: "18px" }} />
                <span>256-Bit Encrypted Transfer</span>
              </Box>
            </Box>

            {/* Tabbed Specs & Details */}
            <Box mt={8}>
              <Tabs
                value={activeTab}
                onChange={(e, val) => setActiveTab(val)}
                sx={{
                  borderBottom: `1px solid ${theme.palette.border.default}`,
                  "& .MuiTab-root": {
                    fontWeight: 600,
                    fontSize: "14px",
                    textTransform: "none",
                    minWidth: "120px",
                  }
                }}
              >
                <Tab label="Technical Specifications" />
                <Tab label="Verified Reviews" />
                <Tab label="Engineering FAQ" />
              </Tabs>

              {/* Tab 0: Specs */}
              {activeTab === 0 && (
                <Box pt={6}>
                  <h4 style={{ fontSize: "16px", fontWeight: 700, margin: "0 0 12px 0" }}>Technical Details</h4>
                  <p style={{ fontSize: "14px", lineHeight: 1.6, color: theme.palette.text.secondary, margin: "0 0 20px 0" }}>
                    {product.description || "Designed for maximum engineering reliability and tactile desk ergonomics."}
                  </p>

                  <SpecGrid>
                    {isDigital ? (
                      <>
                        <SpecItem>
                          <span className="spec-key">File Format</span>
                          <span className="spec-val">{product.specs?.format || "ZIP, Source Code, Figma"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Bundle Size</span>
                          <span className="spec-val">{product.specs?.fileSize || "128 MB"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Compatibility</span>
                          <span className="spec-val">{product.specs?.compatibility || "React 19+, Node 20+"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">License</span>
                          <span className="spec-val">{product.specs?.license || "Commercial Developer License"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Version</span>
                          <span className="spec-val">{product.specs?.version || "v2.1.0"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Release Updates</span>
                          <span className="spec-val">{product.specs?.updates || "Lifetime Free Updates"}</span>
                        </SpecItem>
                      </>
                    ) : (
                      <>
                        <SpecItem>
                          <span className="spec-key">Dimensions</span>
                          <span className="spec-val">{product.specs?.dimensions || "320 x 140 x 38 mm"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Weight</span>
                          <span className="spec-val">{product.specs?.weight || "850g"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Materials</span>
                          <span className="spec-val">{product.specs?.material || "Anodized Aluminum & PBT"}</span>
                        </SpecItem>
                        <SpecItem>
                          <span className="spec-key">Warranty</span>
                          <span className="spec-val">{product.specs?.warranty || "2-Year Manufacturer Warranty"}</span>
                        </SpecItem>
                      </>
                    )}
                  </SpecGrid>
                </Box>
              )}

              {/* Tab 1: Reviews */}
              {activeTab === 1 && (
                <Box pt={6}>
                  <Box display="flex" alignItems="center" gap={4} mb={6}>
                    <h3 style={{ fontSize: "36px", fontWeight: 800, margin: 0 }}>{product.rating || 4.8}</h3>
                    <div>
                      <Rating value={product.rating || 4.8} size="sm" />
                      <div style={{ fontSize: "12px", color: theme.palette.text.secondary, marginTop: "2px" }}>
                        Based on {product.ratingCount || 42} verified customer purchases
                      </div>
                    </div>
                  </Box>

                  <Box display="flex" flexDirection="column" gap={4}>
                    {(product.reviews || [
                      { id: "r1", author: "Alex Chen (Senior Architect)", rating: 5, text: "Outstanding quality. Integration took less than 15 minutes in our stack.", date: "2026-08-18" },
                      { id: "r2", author: "Elena Rostova (Lead Designer)", rating: 5, text: "Cleanest design hierarchy I've experienced in a commercial asset.", date: "2026-08-22" }
                    ]).map(rev => (
                      <Card key={rev.id} padding={5} elevation="subtle" radius="md">
                        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                          <span style={{ fontWeight: 600, fontSize: "14px" }}>{rev.author}</span>
                          <span style={{ fontSize: "12px", color: theme.palette.text.muted }}>{rev.date}</span>
                        </Box>
                        <Rating value={rev.rating} size="xs" />
                        <p style={{ margin: "12px 0 0 0", fontSize: "13px", lineHeight: 1.5, color: theme.palette.text.secondary }}>
                          {rev.text}
                        </p>
                      </Card>
                    ))}
                  </Box>
                </Box>
              )}

              {/* Tab 2: FAQ */}
              {activeTab === 2 && (
                <Box pt={6}>
                  {(product.faq || [
                    { q: "What license is included with this purchase?", a: "All digital purchases grant a commercial license allowing deployment in both personal projects and enterprise client products." },
                    { q: "How do I download future updates?", a: "Whenever an update is released, your customer cabinet 'Digital Vault' will show an update badge with a 1-click download button." },
                    { q: "What is your refund policy?", a: "We offer a 14-day refund window on all software blueprints and physical hardware products." }
                  ]).map((faqItem, idx) => (
                    <Accordion key={idx} sx={{ mb: 2, borderRadius: "8px !important", border: `1px solid ${theme.palette.border.default}`, "&:before": { display: "none" } }}>
                      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <span style={{ fontWeight: 600, fontSize: "14px" }}>{faqItem.q}</span>
                      </AccordionSummary>
                      <AccordionDetails>
                        <p style={{ margin: 0, fontSize: "13px", lineHeight: 1.6, color: theme.palette.text.secondary }}>
                          {faqItem.a}
                        </p>
                      </AccordionDetails>
                    </Accordion>
                  ))}
                </Box>
              )}
            </Box>
          </Grid>

          {/* Right Column: Sticky Sidebar Info & Checkout Action */}
          <Grid item xs={12} lg={5}>
            <StickySidebar>
              <Card padding={8} radius="xl" elevation="card">
                <Box mb={2}>
                  <Chip label={product.category || "Marketplace Product"} color="neutral" />
                </Box>

                <h1 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 12px 0", letterSpacing: "-0.015em" }}>
                  {product.title}
                </h1>

                <Box display="flex" alignItems="center" gap={3} mb={6}>
                  <Rating value={product.rating || 4.8} size="xs" />
                  <span style={{ fontSize: "13px", color: theme.palette.text.secondary }}>
                    ({product.ratingCount || 36} reviews)
                  </span>
                  <span style={{ fontSize: "12px", color: theme.palette.text.muted }}>·</span>
                  <span style={{ fontSize: "12px", color: product.inStock ? theme.palette.status.success : theme.palette.status.error, fontWeight: 600 }}>
                    {product.inStock ? "In Stock & Verified" : "Backorder Available"}
                  </span>
                </Box>

                <Box mb={6} p={4} backgroundColor={theme.palette.background.elevated} borderRadius="12px">
                  <Box display="flex" justifyContent="space-between" alignItems="baseline">
                    <Price amount={product.price} originalAmount={product.originalPrice} size="lg" />
                    <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>
                      Taxes calculated at checkout
                    </span>
                  </Box>
                </Box>

                {/* Delivery Info */}
                <Box display="flex" alignItems="center" gap={2} mb={6} p={3} border={`1px solid ${theme.palette.border.default}`} borderRadius="8px">
                  {isDigital ? (
                    <>
                      <BoltIcon style={{ color: "#F59E0B" }} />
                      <div>
                        <div style={{ fontSize: "13px", fontWeight: 600 }}>Instant Cryptographic Vault Release</div>
                        <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>Direct download signed in your customer cabinet</div>
                      </div>
                    </>
                  ) : (
                    <>
                      <LocalShippingIcon style={{ color: theme.palette.accent.main }} />
                      <div>
                        <div style={{ fontSize: "13px", fontWeight: 600 }}>Express Worldwide Delivery</div>
                        <div style={{ fontSize: "11px", color: theme.palette.text.muted }}>{product.deliveryInfo || "Ships within 1-2 business days with tracking"}</div>
                      </div>
                    </>
                  )}
                </Box>

                {/* Quantity & Actions */}
                <Box display="flex" gap={3} mb={4}>
                  {!isDigital && (
                    <QuantitySelector>
                      <button onClick={() => setQuantity(Math.max(1, quantity - 1))}>-</button>
                      <span>{quantity}</span>
                      <button onClick={() => setQuantity(quantity + 1)}>+</button>
                    </QuantitySelector>
                  )}

                  <Button
                    variant="primary"
                    size="lg"
                    fullWidth
                    state={cartBtnState}
                    disabled={!product.inStock}
                    onClick={handleAddToCart}
                    leftIcon={cartBtnState === "success" ? <CheckIcon /> : <ShoppingBagIcon />}
                  >
                    {cartBtnState === "success" ? "Added to Cart" : isDigital ? "Unlock in Digital Vault" : "Add to Cart"}
                  </Button>

                  <IconButton
                    aria-label={isFav ? "Remove from wishlist" : "Add to wishlist"}
                    variant="outline"
                    onClick={() => toggleWishlist(product)}
                    style={{ minWidth: "48px", height: "48px" }}
                  >
                    {isFav ? <FavoriteIcon style={{ color: "#EF4444" }} /> : <FavoriteBorderIcon />}
                  </IconButton>
                </Box>

                <Button
                  variant="secondary"
                  size="md"
                  fullWidth
                  onClick={() => {
                    addItem(product);
                    navigate("/checkout");
                  }}
                >
                  Instant Buy Now
                </Button>
              </Card>
            </StickySidebar>
          </Grid>
        </Grid>

        {/* Related Products Showcase */}
        {related.length > 0 && (
          <Box mt={16}>
            <SectionHeader
              label="SIMILAR ARCHITECTURES & GEAR"
              title="Related Engineering Products"
              subtitle="Developers who evaluated this asset also deployed these complementary tools."
            />
            <Grid container spacing={6}>
              {related.map(rel => (
                <Grid item xs={12} sm={6} lg={3} key={rel.id}>
                  <ProductCard
                    product={rel}
                    onAddToCart={addItem}
                    onWishlistToggle={toggleWishlist}
                    isWishlisted={isWishlisted(rel.id)}
                  />
                </Grid>
              ))}
            </Grid>
          </Box>
        )}
      </Container>

      {/* Mobile Bottom Dock */}
      <MobileDock>
        <Price amount={product.price} size="sm" />
        <Button
          variant="primary"
          size="sm"
          onClick={handleAddToCart}
          disabled={!product.inStock}
        >
          {isDigital ? "Unlock Vault" : "Add to Cart"}
        </Button>
      </MobileDock>

      {/* Added to Cart Interactive Modal */}
      <Dialog
        open={addedModalOpen}
        onClose={() => setAddedModalOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{
          style: {
            borderRadius: "16px",
            padding: "24px",
            backgroundColor: "#FFFFFF",
            boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
          },
        }}
      >
        <DialogContent style={{ padding: 0 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
            <Box display="flex" alignItems="center" gap={1.5}>
              <CheckIcon style={{ color: "#10B981", fontSize: "24px" }} />
              <h3 style={{ margin: 0, fontSize: "17px", fontWeight: 800, color: "#0F172A" }}>
                Added to Your Cart!
              </h3>
            </Box>
            <IconButton size="xs" variant="ghost" onClick={() => setAddedModalOpen(false)}>
              <CloseIcon style={{ fontSize: "18px" }} />
            </IconButton>
          </Box>

          <Box
            display="flex"
            gap={3}
            alignItems="center"
            p={3}
            mb={4}
            backgroundColor={theme.palette.background.elevated}
            borderRadius="12px"
            border={`1px solid ${theme.palette.border.default}`}
          >
            <img
              src={product.image}
              alt={product.title}
              style={{ width: "64px", height: "64px", objectFit: "cover", borderRadius: "8px", flexShrink: 0 }}
            />
            <div style={{ flexGrow: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "14px", display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {product.title}
              </strong>
              <div style={{ fontSize: "12px", color: theme.palette.text.secondary, marginTop: "2px" }}>
                {isDigital ? "Digital Asset (Instant Vault)" : `Qty: ${quantity} · Workspace Gear`}
              </div>
              <div style={{ fontSize: "14px", fontWeight: 700, color: theme.palette.primary.main, marginTop: "4px" }}>
                ₹{(product.price * quantity).toFixed(2)}
              </div>
            </div>
          </Box>

          <Box display="flex" flexDirection="column" gap={2}>
            <Button
              variant="primary"
              size="lg"
              fullWidth
              onClick={() => {
                setAddedModalOpen(false);
                navigate("/cart");
              }}
              leftIcon={<CartIcon style={{ fontSize: "18px" }} />}
              rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              style={{ fontWeight: 700 }}
            >
              Go to Cart & Select Items
            </Button>
            <Button
              variant="secondary"
              size="md"
              fullWidth
              onClick={() => setAddedModalOpen(false)}
            >
              Continue Shopping
            </Button>
          </Box>
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default ProductDetail;
