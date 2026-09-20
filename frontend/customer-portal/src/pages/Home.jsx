import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import { motion } from "framer-motion";
import {
  ArrowForward as ArrowForwardIcon,
  Code as CodeIcon,
  Storage as StorageIcon,
  Bolt as BoltIcon,
  ArchitectureOutlined as ArchitectureIcon,
  FolderSpecialOutlined as VaultIcon,
  SpeedOutlined as PerformanceIcon,
  LockOutlined as LockIcon,
  CheckCircle as CheckIcon,
  VerifiedUserOutlined as VerifiedIcon,
  LoopOutlined as SyncIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { SectionHeader } from "../components/primitives/SectionHeader";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { Card } from "../components/primitives/Card";
import { Skeleton } from "../components/primitives/Skeleton";
import { ProductCard } from "../features/products/components/ProductCard/ProductCard";
import { useCart } from "../store/CartContext";
import { useWishlist } from "../store/WishlistContext";
import { productService } from "../services/productService";

// --- HERO STYLING (Atmospheric Midnight) ---
const HeroSection = styled("section")(({ theme }) => ({
  backgroundColor: "#050811",
  color: "#FFFFFF",
  paddingTop: theme.spacing(12),
  paddingBottom: theme.spacing(14),
  position: "relative",
  overflow: "hidden",
  borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
  minHeight: "75vh",
  display: "flex",
  alignItems: "center",
}));

const HeroGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "1.15fr 0.95fr",
  gap: theme.spacing(10),
  alignItems: "center",
  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
    gap: theme.spacing(8),
  },
}));

const HeroTitle = styled("h1")(({ theme }) => ({
  fontSize: "48px",
  fontWeight: 800,
  lineHeight: 1.12,
  letterSpacing: "-0.025em",
  color: "#FFFFFF",
  margin: "0 0 18px 0",

  "& .gradient-text": {
    background: "linear-gradient(135deg, #A78BFA 0%, #60A5FA 100%)",
    WebkitBackgroundClip: "text",
    WebkitTextFillColor: "transparent",
  },

  [theme.breakpoints.down("md")]: {
    fontSize: "36px",
  },
  [theme.breakpoints.down("sm")]: {
    fontSize: "28px",
  },
}));

const HeroSubtitle = styled("p")({
  fontSize: "16px",
  lineHeight: 1.65,
  color: "rgba(255, 255, 255, 0.72)",
  margin: "0 0 28px 0",
  maxWidth: "520px",
  letterSpacing: "-0.01em",
});

const HeroVisualBox = styled("div")({
  position: "relative",
  display: "flex",
  justifyContent: "center",
  alignItems: "center",

  "& .main-image": {
    width: "100%",
    maxWidth: "500px",
    height: "360px",
    objectFit: "cover",
    borderRadius: "20px",
    border: "1px solid rgba(255, 255, 255, 0.15)",
    boxShadow: "0 20px 50px rgba(0, 0, 0, 0.7), 0 0 30px rgba(124, 58, 237, 0.18)",
    transition: "transform 0.4s ease",
  },

  "&:hover .main-image": {
    transform: "scale(1.02)",
  }
});

const FloatingBadge = styled(motion.div)(({ theme }) => ({
  position: "absolute",
  padding: `${theme.spacing(2)} ${theme.spacing(3.5)}`,
  backgroundColor: "rgba(11, 16, 32, 0.9)",
  backdropFilter: "blur(16px)",
  border: "1px solid rgba(255, 255, 255, 0.18)",
  borderRadius: "10px",
  display: "flex",
  alignItems: "center",
  gap: "8px",
  boxShadow: "0 8px 20px rgba(0, 0, 0, 0.45)",
  zIndex: 3,

  "& .badge-title": {
    fontSize: "10px",
    textTransform: "uppercase",
    letterSpacing: "0.06em",
    color: "rgba(255, 255, 255, 0.5)",
    fontWeight: 700,
  },
  "& .badge-val": {
    fontSize: "12px",
    fontWeight: 700,
    color: "#FFFFFF",
  }
}));

// --- TRUST STRIP ---
const TrustStrip = styled("section")(({ theme }) => ({
  backgroundColor: "#FFFFFF",
  borderBottom: `1px solid ${theme.palette.border.default}`,
  padding: `${theme.spacing(7)} 0`,
}));

const TrustGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(4, 1fr)",
  gap: theme.spacing(6),
  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "repeat(2, 1fr)",
    gap: theme.spacing(5),
  },
  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
    gap: theme.spacing(4),
  },
}));

const TrustItem = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "flex-start",
  gap: theme.spacing(3),
}));

const TrustIconBox = styled("div", {
  shouldForwardProp: (prop) => prop !== "colorScheme",
})(({ theme, colorScheme }) => ({
  width: "40px",
  height: "40px",
  borderRadius: "10px",
  backgroundColor: colorScheme === "purple" ? theme.palette.primary.soft : theme.palette.accent.soft,
  color: colorScheme === "purple" ? theme.palette.primary.main : theme.palette.accent.main,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  flexShrink: 0,
}));

// --- FEATURED 3-COLUMN PRODUCTS GRID ---
const ProductsGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(3, 1fr)",
  gap: theme.spacing(6),
  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "repeat(2, 1fr)",
  },
  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
  },
}));

// --- DUAL GATEWAYS ---
const GatewaysSection = styled("section")(({ theme }) => ({
  backgroundColor: "#070B16",
  color: "#FFFFFF",
  paddingTop: theme.spacing(14),
  paddingBottom: theme.spacing(14),
  borderTop: "1px solid rgba(255, 255, 255, 0.08)",
  borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
}));

const GatewaysGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(2, 1fr)",
  gap: theme.spacing(8),
  [theme.breakpoints.down("md")]: {
    gridTemplateColumns: "1fr",
  },
}));

const GatewayCard = styled(motion.div, {
  shouldForwardProp: (prop) => prop !== "themeMode",
})(({ theme, themeMode }) => ({
  borderRadius: "20px",
  padding: theme.spacing(9),
  border: themeMode === "digital" 
    ? "1px solid rgba(124, 58, 237, 0.35)" 
    : "1px solid rgba(59, 130, 246, 0.35)",
  background: themeMode === "digital"
    ? "linear-gradient(180deg, rgba(124, 58, 237, 0.12) 0%, rgba(7, 11, 22, 0.95) 100%)"
    : "linear-gradient(180deg, rgba(59, 130, 246, 0.12) 0%, rgba(7, 11, 22, 0.95) 100%)",
  position: "relative",
  overflow: "hidden",
  display: "flex",
  flexDirection: "column",
  justifyContent: "space-between",
  minHeight: "340px",
  cursor: "pointer",
  transition: "border-color 0.25s ease, transform 0.25s ease",

  "&:hover": {
    borderColor: themeMode === "digital" ? "#A78BFA" : "#60A5FA",
    transform: "translateY(-4px)",
    "& .cta-arrow": {
      transform: "translateX(4px)",
    }
  },

  "& .bg-graphic": {
    position: "absolute",
    right: "-20px",
    bottom: "-20px",
    width: "280px",
    height: "200px",
    objectFit: "cover",
    borderRadius: "14px",
    opacity: 0.28,
    filter: "grayscale(20%) contrast(120%)",
    pointerEvents: "none",
  }
}));

// --- ASYMMETRICAL DEVELOPER PRODUCTIVITY SECTION (40% / 60%) ---
const ProductivitySection = styled("section")(({ theme }) => ({
  backgroundColor: "#F8FAFC",
  paddingTop: theme.spacing(16),
  paddingBottom: theme.spacing(16),
  borderBottom: `1px solid ${theme.palette.border.default}`,
}));

const AsymmetricalGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "0.85fr 1.15fr",
  gap: theme.spacing(10),
  alignItems: "flex-start",

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
    gap: theme.spacing(8),
  },
}));

const LeftEditorialCol = styled("div")({
  display: "flex",
  flexDirection: "column",
  gap: "16px",
  position: "sticky",
  top: "100px",
});

const RightFeatureStack = styled("div")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(4),
}));

const TechnicalFeaturePanel = styled(Card)(({ theme }) => ({
  padding: theme.spacing(6),
  borderRadius: "16px",
  backgroundColor: "#FFFFFF",
  border: `1px solid ${theme.palette.border.default}`,
  display: "flex",
  gap: theme.spacing(5),
  alignItems: "flex-start",
  transition: "border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease",

  "&:hover": {
    borderColor: theme.palette.primary.main,
    transform: "translateX(4px)",
    boxShadow: theme.elevation.subtle,
  },

  [theme.breakpoints.down("sm")]: {
    flexDirection: "column",
    gap: theme.spacing(3),
  }
}));

const PanelIconBox = styled("div", {
  shouldForwardProp: (prop) => prop !== "variant",
})(({ theme, variant }) => ({
  width: "48px",
  height: "48px",
  borderRadius: "12px",
  backgroundColor: variant === "purple" 
    ? theme.palette.primary.soft 
    : variant === "blue" 
    ? theme.palette.accent.soft 
    : "#ECFDF5",
  color: variant === "purple" 
    ? theme.palette.primary.main 
    : variant === "blue" 
    ? theme.palette.accent.main 
    : "#10B981",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  flexShrink: 0,
}));

// --- FINAL CTA ---
const FinalCTA = styled("section")(({ theme }) => ({
  backgroundColor: "#050811",
  color: "#FFFFFF",
  padding: `${theme.spacing(16)} 0`,
  textAlign: "center",
  position: "relative",
  overflow: "hidden",
}));

export const Home = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const { toggleWishlist, isWishlisted } = useWishlist();

  const [featuredProducts, setFeaturedProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchFeatured = async () => {
      try {
        setLoading(true);
        const list = await productService.getProducts({ sortBy: "trending" });
        setFeaturedProducts(list.slice(0, 3)); // Clean 3-column featured set
      } catch (err) {
        console.error("Featured fetch failed", err);
      } finally {
        setLoading(false);
      }
    };
    fetchFeatured();
  }, []);

  const revealAnim = {
    initial: { opacity: 0, y: 16 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true },
    transition: { duration: 0.4, ease: "easeOut" }
  };

  return (
    <>
      {/* 1. HERO SECTION */}
      <HeroSection className="bv-tech-grid bv-glow-navy">
        <Container maxWidth="xxl">
          <HeroGrid>
            {/* Left Column */}
            <div>
              <Box mb={2.5} display="inline-flex">
                <Chip
                  label="CURATED DEVELOPER MARKETPLACE · V2.4"
                  color="primary"
                  variant="filled"
                  uppercase
                />
              </Box>

              <HeroTitle>
                Engineering Assets. <br />
                Workspace Gear. <br />
                <span className="gradient-text">Built for Builders.</span>
              </HeroTitle>

              <HeroSubtitle>
                ByteVault Media bridges production-grade software blueprints, developer kits, and architecture templates with tactile mechanical workspace hardware.
              </HeroSubtitle>

              <Box display="flex" gap={3} flexWrap="wrap" mb={4}>
                <Button
                  variant="primary"
                  size="lg"
                  onClick={() => navigate("/catalog")}
                  rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                >
                  Explore Marketplace
                </Button>
                <Button
                  variant="dark"
                  size="lg"
                  onClick={() => navigate("/account?tab=downloads")}
                  leftIcon={<VaultIcon style={{ fontSize: "18px" }} />}
                >
                  Browse Digital Vault
                </Button>
              </Box>

              {/* Compact Trust Cluster */}
              <Box display="flex" alignItems="center" gap={3} flexWrap="wrap" fontSize="13px" color="rgba(255, 255, 255, 0.7)">
                <Box display="flex" alignItems="center" gap={1}>
                  <CheckIcon style={{ fontSize: "15px", color: "#10B981" }} />
                  <span>Engineer-vetted assets</span>
                </Box>
                <span style={{ opacity: 0.3 }}>·</span>
                <Box display="flex" alignItems="center" gap={1}>
                  <BoltIcon style={{ fontSize: "15px", color: "#F59E0B" }} />
                  <span>Instant digital delivery</span>
                </Box>
                <span style={{ opacity: 0.3 }}>·</span>
                <Box display="flex" alignItems="center" gap={1}>
                  <SyncIcon style={{ fontSize: "15px", color: "#60A5FA" }} />
                  <span>Lifetime releases</span>
                </Box>
              </Box>
            </div>

            {/* Right Column Visual */}
            <HeroVisualBox>
              <img
                src="https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=1000&q=80"
                alt="Developer Engineering Workspace"
                className="main-image"
              />

              {/* Attached Floating Metadata */}
              <FloatingBadge
                style={{ top: "16px", left: "-12px" }}
                animate={{ y: [0, -4, 0] }}
                transition={{ duration: 4, repeat: Infinity, ease: "easeInOut" }}
              >
                <CodeIcon style={{ color: "#A78BFA", fontSize: "16px" }} />
                <div>
                  <div className="badge-title">Architecture</div>
                  <div className="badge-val">Rust Microservices v2.1</div>
                </div>
              </FloatingBadge>

              <FloatingBadge
                style={{ bottom: "20px", right: "-12px" }}
                animate={{ y: [0, 4, 0] }}
                transition={{ duration: 4.5, repeat: Infinity, ease: "easeInOut", delay: 1 }}
              >
                <StorageIcon style={{ color: "#60A5FA", fontSize: "16px" }} />
                <div>
                  <div className="badge-title">Workspace Gear</div>
                  <div className="badge-val">KeyPro Linear Switch</div>
                </div>
              </FloatingBadge>
            </HeroVisualBox>
          </HeroGrid>
        </Container>
      </HeroSection>

      {/* 2. TRUST / VALUE METRICS STRIP */}
      <TrustStrip>
        <Container maxWidth="xxl">
          <TrustGrid>
            <TrustItem>
              <TrustIconBox colorScheme="purple">
                <ArchitectureIcon style={{ fontSize: "20px" }} />
              </TrustIconBox>
              <div>
                <strong style={{ fontSize: "14px", display: "block", color: theme.palette.text.primary, marginBottom: "2px" }}>
                  Engineer-Vetted Quality
                </strong>
                <span style={{ fontSize: "12px", color: theme.palette.text.secondary, lineHeight: 1.5, display: "block" }}>
                  Every software template and hardware peripheral is audited for production readiness.
                </span>
              </div>
            </TrustItem>

            <TrustItem>
              <TrustIconBox colorScheme="blue">
                <BoltIcon style={{ fontSize: "20px" }} />
              </TrustIconBox>
              <div>
                <strong style={{ fontSize: "14px", display: "block", color: theme.palette.text.primary, marginBottom: "2px" }}>
                  Instant Vault Delivery
                </strong>
                <span style={{ fontSize: "12px", color: theme.palette.text.secondary, lineHeight: 1.5, display: "block" }}>
                  Digital assets unlock cryptographically inside your customer vault cabinet instantly.
                </span>
              </div>
            </TrustItem>

            <TrustItem>
              <TrustIconBox colorScheme="purple">
                <LockIcon style={{ fontSize: "20px" }} />
              </TrustIconBox>
              <div>
                <strong style={{ fontSize: "14px", display: "block", color: theme.palette.text.primary, marginBottom: "2px" }}>
                  Secure Financial Checkout
                </strong>
                <span style={{ fontSize: "12px", color: theme.palette.text.secondary, lineHeight: 1.5, display: "block" }}>
                  Encrypted transactions backed by zero-risk 14-day technical return warranties.
                </span>
              </div>
            </TrustItem>

            <TrustItem>
              <TrustIconBox colorScheme="blue">
                <PerformanceIcon style={{ fontSize: "20px" }} />
              </TrustIconBox>
              <div>
                <strong style={{ fontSize: "14px", display: "block", color: theme.palette.text.primary, marginBottom: "2px" }}>
                  Lifetime Code Releases
                </strong>
                <span style={{ fontSize: "12px", color: theme.palette.text.secondary, lineHeight: 1.5, display: "block" }}>
                  Receive automated notifications on software upgrades and re-download anytime.
                </span>
              </div>
            </TrustItem>
          </TrustGrid>
        </Container>
      </TrustStrip>

      {/* 3. FEATURED PRODUCTS (Engineered 3-Column Grid) */}
      <motion.div {...revealAnim}>
        <Box style={{ paddingTop: "64px", paddingBottom: "72px", backgroundColor: "#FFFFFF" }}>
          <Container maxWidth="xxl">
            <SectionHeader
              label="CURATED SELECTIONS"
              title="Featured Engineering Blueprints & Gear"
              subtitle="Handpicked by our team for architecture stability, performance, and craftsmanship."
              action={
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => navigate("/catalog")}
                  rightIcon={<ArrowForwardIcon style={{ fontSize: "14px" }} />}
                >
                  View All Marketplace
                </Button>
              }
            />

            {loading ? (
              <ProductsGrid>
                {[1, 2, 3].map(i => (
                  <div key={i}>
                    <Skeleton variant="rectangular" height={240} radius="lg" style={{ marginBottom: "12px" }} />
                    <Skeleton variant="text" width="70%" style={{ marginBottom: "6px" }} />
                    <Skeleton variant="text" width="40%" />
                  </div>
                ))}
              </ProductsGrid>
            ) : (
              <ProductsGrid>
                {featuredProducts.map((prod) => (
                  <ProductCard
                    key={prod.id}
                    product={prod}
                    onAddToCart={addItem}
                    onWishlistToggle={toggleWishlist}
                    isWishlisted={isWishlisted(prod.id)}
                  />
                ))}
              </ProductsGrid>
            )}
          </Container>
        </Box>
      </motion.div>

      {/* 4. DUAL MARKETPLACE GATEWAYS (Digital Assets vs Workspace Gear) */}
      <motion.div {...revealAnim}>
        <GatewaysSection className="bv-tech-grid">
          <Container maxWidth="xxl">
            <SectionHeader
              light={true}
              label="EXPLORE BY DISCIPLINE"
              title="Two Gateways. One Cohesive Ecosystem."
              subtitle="Choose your focus: High-velocity digital architecture or precision workspace hardware."
            />

            <GatewaysGrid>
              <GatewayCard 
                themeMode="digital"
                whileHover={{ y: -4 }}
                onClick={() => navigate("/catalog?type=digital")}
              >
                <div>
                  <Box mb={2}>
                    <Chip label="CODE & ARCHITECTURE" color="primary" variant="filled" uppercase />
                  </Box>
                  <h3 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
                    Digital Assets & Blueprints
                  </h3>
                  <p style={{ fontSize: "14px", color: "rgba(255, 255, 255, 0.7)", margin: "0 0 24px 0", lineHeight: 1.6 }}>
                    Production-grade React UI kits, Rust backend microservice templates, and Figma enterprise design systems.
                  </p>
                </div>
                <img 
                  src="https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=600&q=80" 
                  alt="Code Blueprints" 
                  className="bg-graphic" 
                />
                <Button 
                  variant="primary" 
                  size="sm"
                  style={{ alignSelf: "flex-start" }}
                  rightIcon={<ArrowForwardIcon className="cta-arrow" style={{ fontSize: "14px", transition: "transform 0.2s ease" }} />}
                >
                  Browse Digital Vault
                </Button>
              </GatewayCard>

              <GatewayCard 
                themeMode="gear"
                whileHover={{ y: -4 }}
                onClick={() => navigate("/catalog?type=physical")}
              >
                <div>
                  <Box mb={2}>
                    <Chip label="HARDWARE & ERGONOMICS" color="accent" variant="filled" uppercase />
                  </Box>
                  <h3 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
                    Tactile Workspace Gear
                  </h3>
                  <p style={{ fontSize: "14px", color: "rgba(255, 255, 255, 0.7)", margin: "0 0 24px 0", lineHeight: 1.6 }}>
                    Custom hot-swap mechanical keyboards, acoustic isolation headphones, and vegetable-tanned desk organizers.
                  </p>
                </div>
                <img 
                  src="https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=600&q=80" 
                  alt="Workspace Gear" 
                  className="bg-graphic" 
                />
                <Button 
                  variant="accent" 
                  size="sm"
                  style={{ alignSelf: "flex-start" }}
                  rightIcon={<ArrowForwardIcon className="cta-arrow" style={{ fontSize: "14px", transition: "transform 0.2s ease" }} />}
                >
                  Browse Workspace Gear
                </Button>
              </GatewayCard>
            </GatewaysGrid>
          </Container>
        </GatewaysSection>
      </motion.div>

      {/* 5. ASYMMETRICAL DEVELOPER PRODUCTIVITY (40% / 60% Composition) */}
      <motion.div {...revealAnim}>
        <ProductivitySection>
          <Container maxWidth="xxl">
            <AsymmetricalGrid>
              {/* Left 40% Editorial */}
              <LeftEditorialCol>
                <Box mb={1} display="inline-flex">
                  <Chip label="THE BYTEVAULT STANDARD" color="primary" size="xs" uppercase />
                </Box>
                <h2 style={{ fontSize: "36px", fontWeight: 800, margin: "0 0 12px 0", color: theme.palette.text.primary, letterSpacing: "-0.02em", lineHeight: 1.2 }}>
                  Engineered for Developer Productivity
                </h2>
                <p style={{ fontSize: "15px", color: theme.palette.text.secondary, lineHeight: 1.7, margin: "0 0 20px 0" }}>
                  Every detail of our platform is constructed to minimize cognitive friction between architectural code discovery and physical workspace execution.
                </p>

                <Box p={4} backgroundColor="#FFFFFF" border={`1px solid ${theme.palette.border.default}`} borderRadius="14px" display="flex" flexDirection="column" gap={1.5}>
                  <Box display="flex" alignItems="center" gap={1.5} color={theme.palette.primary.main} fontWeight={700} fontSize="13px">
                    <VerifiedIcon style={{ fontSize: "18px" }} />
                    <span>Commercial & Telemetry-Free Guarantee</span>
                  </Box>
                  <span style={{ fontSize: "12px", color: theme.palette.text.muted, lineHeight: 1.5 }}>
                    Zero runtime tracking or telemetry scripts embedded into downloadable source blueprints.
                  </span>
                </Box>
              </LeftEditorialCol>

              {/* Right 60% Stacked Feature Panels */}
              <RightFeatureStack>
                <TechnicalFeaturePanel elevation="subtle">
                  <PanelIconBox variant="purple">
                    <ArchitectureIcon style={{ fontSize: "24px" }} />
                  </PanelIconBox>
                  <div>
                    <h4 style={{ margin: "0 0 4px 0", fontWeight: 700, fontSize: "16px", color: theme.palette.text.primary }}>
                      Architecture Audited
                    </h4>
                    <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary, lineHeight: 1.6 }}>
                      Every software bundle includes detailed migration scripts, environment variables templates, and verified test suites before catalog publication.
                    </p>
                  </div>
                </TechnicalFeaturePanel>

                <TechnicalFeaturePanel elevation="subtle">
                  <PanelIconBox variant="blue">
                    <VaultIcon style={{ fontSize: "24px" }} />
                  </PanelIconBox>
                  <div>
                    <h4 style={{ margin: "0 0 4px 0", fontWeight: 700, fontSize: "16px", color: theme.palette.text.primary }}>
                      Personal Vault Cabinet
                    </h4>
                    <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary, lineHeight: 1.6 }}>
                      Instant cryptographic signature authorization. Download ZIP archives, access Figma design tokens, and inspect release changelogs anytime.
                    </p>
                  </div>
                </TechnicalFeaturePanel>

                <TechnicalFeaturePanel elevation="subtle">
                  <PanelIconBox variant="green">
                    <PerformanceIcon style={{ fontSize: "24px" }} />
                  </PanelIconBox>
                  <div>
                    <h4 style={{ margin: "0 0 4px 0", fontWeight: 700, fontSize: "16px", color: theme.palette.text.primary }}>
                      Zero Bloat Guarantee
                    </h4>
                    <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary, lineHeight: 1.6 }}>
                      Optimized asset payload sizes, tree-shakeable packages, and minimal external runtime dependencies out-of-the-box.
                    </p>
                  </div>
                </TechnicalFeaturePanel>

                <TechnicalFeaturePanel elevation="subtle">
                  <PanelIconBox variant="purple">
                    <SyncIcon style={{ fontSize: "24px" }} />
                  </PanelIconBox>
                  <div>
                    <h4 style={{ margin: "0 0 4px 0", fontWeight: 700, fontSize: "16px", color: theme.palette.text.primary }}>
                      Continuous Version Streams
                    </h4>
                    <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary, lineHeight: 1.6 }}>
                      Receive automated alerts when frameworks issue major upstream updates (e.g. React 19 or Rust 2024 editions) with 1-click re-downloads.
                    </p>
                  </div>
                </TechnicalFeaturePanel>
              </RightFeatureStack>
            </AsymmetricalGrid>
          </Container>
        </ProductivitySection>
      </motion.div>

      {/* 6. FINAL CALL TO ACTION */}
      <motion.div {...revealAnim}>
        <FinalCTA className="bv-glow-vault">
          <Container maxWidth="md">
            <Box mb={3}>
              <Chip label="START BUILDING TODAY" color="primary" variant="filled" uppercase />
            </Box>
            <h2 style={{ fontSize: "36px", fontWeight: 800, margin: "0 0 16px 0", letterSpacing: "-0.02em" }}>
              Upgrade Your Engineering Workspace
            </h2>
            <p style={{ fontSize: "16px", margin: "0 0 32px 0", color: "rgba(255, 255, 255, 0.75)", lineHeight: 1.6 }}>
              Join technical founders, lead architects, and engineering teams shipping products faster with ByteVault Media.
            </p>
            <Box display="flex" justifyContent="center" gap={4} flexWrap="wrap">
              <Button 
                variant="primary" 
                size="lg" 
                onClick={() => navigate("/catalog")}
                rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              >
                Explore Full Catalog
              </Button>
              <Button 
                variant="dark" 
                size="lg" 
                onClick={() => navigate("/register")}
              >
                Create Developer Account
              </Button>
            </Box>
          </Container>
        </FinalCTA>
      </motion.div>
    </>
  );
};

export default Home;
