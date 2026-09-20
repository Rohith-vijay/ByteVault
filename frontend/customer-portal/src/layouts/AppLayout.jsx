import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import {
  ShoppingBagOutlined as ShoppingBagOutlinedIcon,
  FavoriteBorderOutlined as FavoriteBorderOutlinedIcon,
  PersonOutlineOutlined as PersonOutlineOutlinedIcon,
  Menu as MenuIcon,
  Close as CloseIcon,
  Search as SearchIcon,
  ExitToApp as ExitToAppIcon,
  Dashboard as DashboardIcon,
  WifiOff as WifiOffIcon,
  CheckCircle as CheckCircleIcon,
  ChevronRight as ChevronRightIcon,
  LockOutlined as LockIcon,
  FolderSpecialOutlined as VaultIcon,
  ReceiptLongOutlined as OrdersIcon,
  CodeOutlined as CodeIcon,
  DevicesOutlined as HardwareIcon,
  StorefrontOutlined as StoreIcon,
  AdminPanelSettingsOutlined as AdminIcon
} from "@mui/icons-material";
import Drawer from "@mui/material/Drawer";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import Box from "@mui/material/Box";

import { Container } from "../components/primitives/Container";
import { Button } from "../components/primitives/Button";
import { IconButton } from "../components/primitives/IconButton";
import { Badge } from "../components/primitives/Badge";
import { useCart } from "../store/CartContext";
import { useWishlist } from "../store/WishlistContext";
import { useAuth } from "../store/AuthContext";
import { productService } from "../services/productService";

// --- HEADER STYLING ---
const Header = styled("header", {
  shouldForwardProp: (prop) => prop !== "scrolled",
})(({ scrolled }) => ({
  position: "fixed",
  top: 0,
  left: 0,
  right: 0,
  zIndex: 1100,
  height: "72px",
  backgroundColor: scrolled 
    ? "rgba(0, 0, 0, 0.96)" 
    : "#000000",
  backdropFilter: "blur(16px)",
  WebkitBackdropFilter: "blur(16px)",
  borderBottom: "1px solid rgba(255, 255, 255, 0.1)",
  transition: "all 0.25s ease",
  display: "flex",
  alignItems: "center",
}));

const NavContainer = styled(Container)({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: "24px",
  width: "100%",
});

const NavLeftGroup = styled("div")({
  display: "flex",
  alignItems: "center",
  gap: "32px",
  flexShrink: 0,
});

const BrandLogoLink = styled(Link)({
  display: "inline-flex",
  alignItems: "center",
  textDecoration: "none",
  transition: "transform 0.15s ease",
  cursor: "pointer",
  "&:hover": {
    transform: "scale(1.03)",
  },
  "&:active": {
    transform: "scale(0.97)",
  }
});

const DesktopNav = styled("nav")({
  display: "flex",
  alignItems: "center",
  gap: "24px",
  "@media (max-width: 992px)": {
    display: "none",
  },
});

const NavItemLink = styled(Link, {
  shouldForwardProp: (prop) => prop !== "active",
})(({ active }) => ({
  textDecoration: "none",
  fontSize: "14px",
  fontWeight: active ? 600 : 500,
  color: active ? "#FFFFFF" : "rgba(255, 255, 255, 0.72)",
  display: "flex",
  alignItems: "center",
  gap: "4px",
  padding: "6px 0",
  position: "relative",
  transition: "color 0.15s ease",

  "&:hover": {
    color: "#FFFFFF",
  },

  "&::after": {
    content: '""',
    position: "absolute",
    bottom: "-2px",
    left: 0,
    right: 0,
    height: "2px",
    backgroundColor: active ? "#7C3AED" : "transparent",
    borderRadius: "2px",
    transition: "background-color 0.15s ease",
  }
}));

const DiscoverDropdownWrapper = styled("div")({
  position: "relative",
  display: "inline-block",
});

const DiscoverMegaMenu = styled(motion.div)(({ theme }) => ({
  position: "absolute",
  top: "100%",
  left: "-20px",
  width: "480px",
  backgroundColor: "#0F172A",
  borderRadius: "14px",
  border: "1px solid rgba(255, 255, 255, 0.12)",
  boxShadow: "0 20px 40px -10px rgba(0, 0, 0, 0.7)",
  padding: theme.spacing(5),
  zIndex: 1200,
  display: "grid",
  gridTemplateColumns: "1fr 1fr",
  gap: theme.spacing(4),
  marginTop: "12px",
}));

const MegaMenuCol = styled("div")({
  display: "flex",
  flexDirection: "column",
  gap: "4px",
});

const MegaMenuHeading = styled("div")({
  fontSize: "11px",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  color: "#A78BFA",
  padding: "6px 8px",
  display: "flex",
  alignItems: "center",
  gap: "6px",
});

const MegaMenuLink = styled(Link)({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  textDecoration: "none",
  fontSize: "13px",
  fontWeight: 500,
  color: "rgba(255, 255, 255, 0.8)",
  padding: "8px 10px",
  borderRadius: "8px",
  transition: "all 0.15s ease",

  "&:hover": {
    backgroundColor: "rgba(124, 58, 237, 0.15)",
    color: "#FFFFFF",
    transform: "translateX(3px)",
  },
});

// --- SEARCH BAR STYLING ---
const SearchWrapper = styled("div")(({ theme }) => ({
  position: "relative",
  flex: 1,
  maxWidth: "420px",
  margin: "0 16px",
  "@media (max-width: 768px)": {
    display: "none",
  },
}));

const SearchBar = styled("form")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  backgroundColor: "rgba(255, 255, 255, 0.06)",
  border: "1px solid rgba(255, 255, 255, 0.12)",
  borderRadius: "10px",
  padding: "7px 12px",
  transition: "all 0.2s ease",

  "&:focus-within": {
    backgroundColor: "rgba(255, 255, 255, 0.09)",
    borderColor: "#7C3AED",
    boxShadow: "0 0 0 3px rgba(124, 58, 237, 0.25)",
  },
}));

const SearchInput = styled("input")({
  border: "none",
  background: "transparent",
  color: "#FFFFFF",
  fontSize: "13px",
  fontWeight: 400,
  width: "100%",
  padding: "0 8px",
  outline: "none",

  "&::placeholder": {
    color: "rgba(255, 255, 255, 0.45)",
  },
});

const SearchKbd = styled("kbd")({
  fontSize: "11px",
  fontWeight: 600,
  fontFamily: "var(--font-mono, monospace)",
  color: "rgba(255, 255, 255, 0.45)",
  backgroundColor: "rgba(255, 255, 255, 0.08)",
  border: "1px solid rgba(255, 255, 255, 0.12)",
  borderRadius: "4px",
  padding: "2px 5px",
  flexShrink: 0,
});

const SuggestionDropdown = styled(motion.div)(({ theme }) => ({
  position: "absolute",
  top: "calc(100% + 8px)",
  left: 0,
  right: 0,
  backgroundColor: "#0F172A",
  borderRadius: "12px",
  border: "1px solid rgba(255, 255, 255, 0.12)",
  boxShadow: "0 16px 36px rgba(0, 0, 0, 0.6)",
  padding: theme.spacing(2),
  zIndex: 1200,
  overflow: "hidden",
}));

const SuggestionItem = styled("div")({
  display: "flex",
  alignItems: "center",
  gap: "12px",
  padding: "8px 10px",
  borderRadius: "8px",
  cursor: "pointer",
  transition: "background-color 0.15s ease",

  "&:hover": {
    backgroundColor: "rgba(124, 58, 237, 0.15)",
  },
});

// --- RIGHT NAV ACTIONS ---
const NavActions = styled("div")({
  display: "flex",
  alignItems: "center",
  gap: "12px",
  flexShrink: 0,
});

const AuthGroup = styled("div")({
  display: "flex",
  alignItems: "center",
  gap: "24px", // Deliberate 24px physical gap
  marginLeft: "12px",
});

const SignInTextLink = styled(Link)({
  textDecoration: "none",
  color: "rgba(255, 255, 255, 0.8)",
  fontSize: "14px",
  fontWeight: 600,
  transition: "color 0.15s ease",

  "&:hover": {
    color: "#FFFFFF",
  },
});

// --- FOOTER STYLING (Structured 5-Column Grid) ---
const FooterSection = styled("footer")(({ theme }) => ({
  backgroundColor: "#080D1C",
  borderTop: "1px solid rgba(255, 255, 255, 0.08)",
  color: "rgba(255, 255, 255, 0.7)",
  fontSize: "14px",
  paddingTop: theme.spacing(16),
  paddingBottom: theme.spacing(12),
}));

const FooterTrustStrip = styled("div")(({ theme }) => ({
  borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
  paddingBottom: theme.spacing(8),
  marginBottom: theme.spacing(12),
}));

const FooterGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "1.4fr 1fr 1fr 1fr 1fr",
  gap: theme.spacing(8),
  marginBottom: theme.spacing(12),

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "repeat(3, 1fr)",
  },
  [theme.breakpoints.down("md")]: {
    gridTemplateColumns: "repeat(2, 1fr)",
  },
  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
    gap: theme.spacing(6),
  },
}));

const FooterCol = styled("div")({
  display: "flex",
  flexDirection: "column",
});

const FooterColTitle = styled("h4")({
  color: "#FFFFFF",
  margin: "0 0 16px 0",
  fontSize: "13px",
  fontWeight: 700,
  letterSpacing: "0.05em",
  textTransform: "uppercase",
});

const FooterLink = styled(Link)({
  display: "block",
  textDecoration: "none",
  color: "rgba(255, 255, 255, 0.65)",
  fontSize: "13px",
  lineHeight: "1.6",
  padding: "4px 0",
  transition: "color 0.15s ease, transform 0.15s ease",

  "&:hover": {
    color: "#FFFFFF",
    transform: "translateX(3px)",
  },
});

const FooterSub = styled("div")(({ theme }) => ({
  borderTop: "1px solid rgba(255, 255, 255, 0.08)",
  paddingTop: theme.spacing(8),
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  flexWrap: "wrap",
  gap: theme.spacing(4),
  fontSize: "12px",
  color: "rgba(255, 255, 255, 0.45)",
}));

const OfflineBanner = styled(Box)(({ theme }) => ({
  position: "fixed",
  top: 0,
  left: 0,
  right: 0,
  height: "36px",
  backgroundColor: theme.palette.status.error,
  color: "#FFFFFF",
  zIndex: 2000,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  gap: "8px",
  fontSize: "12px",
  fontWeight: "bold",
}));

const ToastContainer = styled(motion.div)(({ theme }) => ({
  position: "fixed",
  bottom: "24px",
  right: "24px",
  zIndex: 3000,
  backgroundColor: "#0F172A",
  border: "1px solid rgba(255, 255, 255, 0.12)",
  color: "#FFFFFF",
  padding: `${theme.spacing(3)} ${theme.spacing(5)}`,
  borderRadius: "12px",
  boxShadow: "0 20px 40px rgba(0, 0, 0, 0.6)",
  display: "flex",
  alignItems: "center",
  gap: "10px",
  fontSize: "13px",
  fontWeight: 600,
}));

export const AppLayout = ({ children }) => {
  const [scrolled, setScrolled] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  const [allProducts, setAllProducts] = useState([]);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const [discoverOpen, setDiscoverOpen] = useState(false);
  
  const [isOffline, setIsOffline] = useState(!navigator.onLine);
  const [toast, setToast] = useState(null);

  const { totals } = useCart();
  const { wishlistItems } = useWishlist();
  const { user, logout, isAuthenticated } = useAuth();

  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  useEffect(() => {
    const handleToastEvent = (e) => {
      setToast({
        message: e.detail.message,
        type: e.detail.type || "success",
        actionLabel: e.detail.actionLabel || null,
        actionHref: e.detail.actionHref || null,
      });
      setTimeout(() => {
        setToast(null);
      }, 4500);
    };
    window.addEventListener("bytevault_toast", handleToastEvent);
    return () => window.removeEventListener("bytevault_toast", handleToastEvent);
  }, []);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        setDiscoverOpen(false);
        setSuggestions([]);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  useEffect(() => {
    const loadProducts = async () => {
      try {
        const list = await productService.getProducts();
        setAllProducts(list);
      } catch (err) {
        console.warn("Suggestions fetch failed", err);
      }
    };
    loadProducts();
  }, []);

  const handleSearchChange = (e) => {
    const val = e.target.value;
    setSearchQuery(val);
    if (val.trim()) {
      const filtered = allProducts
        .filter(p => p.title.toLowerCase().includes(val.toLowerCase()) || (p.category && p.category.toLowerCase().includes(val.toLowerCase())))
        .slice(0, 4);
      setSuggestions(filtered);
    } else {
      setSuggestions([]);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/catalog?search=${encodeURIComponent(searchQuery)}`);
      setSuggestions([]);
      setSearchQuery("");
    }
  };

  const handleMenuOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = () => {
    logout();
    handleMenuClose();
    navigate("/login");
  };

  return (
    <>
      {isOffline && (
        <OfflineBanner>
          <WifiOffIcon style={{ fontSize: "16px" }} />
          <span>You are currently browsing offline. Verifying connection...</span>
        </OfflineBanner>
      )}

      <Header scrolled={scrolled} style={{ top: isOffline ? "36px" : 0 }}>
        <NavContainer maxWidth="xxl">
          <NavLeftGroup>
            <BrandLogoLink 
              to="/"
              aria-label="ByteVault Home"
            >
              <img 
                src="/brand/bytevault-rrc-logo.png" 
                alt="ByteVault Logo" 
                style={{ 
                  height: "44px", 
                  width: "auto",
                  maxWidth: "68px",
                  objectFit: "contain",
                  display: "block"
                }} 
              />
            </BrandLogoLink>

            <DesktopNav>
              <NavItemLink to="/" active={location.pathname === "/" ? 1 : 0}>
                Home
              </NavItemLink>
              
              <DiscoverDropdownWrapper
                onMouseEnter={() => setDiscoverOpen(true)}
                onMouseLeave={() => setDiscoverOpen(false)}
              >
                <NavItemLink 
                  to="/catalog" 
                  active={location.pathname.startsWith("/catalog") ? 1 : 0}
                >
                  Discover
                  <ChevronRightIcon style={{ fontSize: "14px", transform: discoverOpen ? "rotate(90deg)" : "none", transition: "transform 0.15s ease" }} />
                </NavItemLink>

                <AnimatePresence>
                  {discoverOpen && (
                    <DiscoverMegaMenu
                      initial={{ opacity: 0, y: 12, scale: 0.98 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 10, scale: 0.98 }}
                      transition={{ duration: 0.18, ease: "easeOut" }}
                    >
                      <MegaMenuCol>
                        <MegaMenuHeading>
                          <CodeIcon style={{ fontSize: "14px" }} />
                          Digital Assets & Code
                        </MegaMenuHeading>
                        <MegaMenuLink to="/catalog?type=digital" onClick={() => setDiscoverOpen(false)}>
                          <span>All Digital Blueprints</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?category=Software%20%26%20Coding" onClick={() => setDiscoverOpen(false)}>
                          <span>Software & Architecture</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?category=Design%20Resources" onClick={() => setDiscoverOpen(false)}>
                          <span>UI Kits & Icon Sets</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?sortBy=newest" onClick={() => setDiscoverOpen(false)}>
                          <span>New Code Releases</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                      </MegaMenuCol>

                      <MegaMenuCol>
                        <MegaMenuHeading>
                          <HardwareIcon style={{ fontSize: "14px" }} />
                          Workspace Gear
                        </MegaMenuHeading>
                        <MegaMenuLink to="/catalog?type=physical" onClick={() => setDiscoverOpen(false)}>
                          <span>All Workspace Gear</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?category=Computer%20Peripherals" onClick={() => setDiscoverOpen(false)}>
                          <span>Keyboards & Switches</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?category=Audio%20Equipment" onClick={() => setDiscoverOpen(false)}>
                          <span>Acoustic Audio</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                        <MegaMenuLink to="/catalog?category=Travel%20Gear" onClick={() => setDiscoverOpen(false)}>
                          <span>Desk Organizers & Bags</span>
                          <ChevronRightIcon style={{ fontSize: "14px", opacity: 0.5 }} />
                        </MegaMenuLink>
                      </MegaMenuCol>
                    </DiscoverMegaMenu>
                  )}
                </AnimatePresence>
              </DiscoverDropdownWrapper>
            </DesktopNav>
          </NavLeftGroup>

          <SearchWrapper>
            <SearchBar onSubmit={handleSearchSubmit}>
              <SearchIcon style={{ fontSize: "18px", color: "rgba(255, 255, 255, 0.5)" }} />
              <SearchInput
                type="text"
                placeholder="Search blueprints, gear, tags..."
                value={searchQuery}
                onChange={handleSearchChange}
              />
              <SearchKbd>⌘K</SearchKbd>
            </SearchBar>

            <AnimatePresence>
              {suggestions.length > 0 && (
                <SuggestionDropdown
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 8 }}
                  transition={{ duration: 0.15 }}
                >
                  {suggestions.map(p => (
                    <SuggestionItem
                      key={p.id}
                      onClick={() => {
                        setSearchQuery("");
                        setSuggestions([]);
                        navigate(`/products/${p.id}`);
                      }}
                    >
                      <img 
                        src={p.image} 
                        alt={p.title} 
                        style={{ width: "36px", height: "36px", objectFit: "cover", borderRadius: "6px" }} 
                      />
                      <Box flexGrow={1} overflow="hidden">
                        <strong style={{ fontSize: "13px", color: "#FFFFFF", display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {p.title}
                        </strong>
                        <span style={{ fontSize: "11px", color: "rgba(255, 255, 255, 0.5)" }}>
                          {p.category} · ₹{p.price.toFixed(2)}
                        </span>
                      </Box>
                    </SuggestionItem>
                  ))}
                </SuggestionDropdown>
              )}
            </AnimatePresence>
          </SearchWrapper>

          <NavActions>
            <IconButton
              aria-label="Wishlist"
              onClick={() => navigate(isAuthenticated ? "/wishlist" : "/login")}
              style={{ color: "rgba(255, 255, 255, 0.85)" }}
            >
              <Badge content={wishlistItems.length} color="primary">
                <FavoriteBorderOutlinedIcon style={{ fontSize: "20px" }} />
              </Badge>
            </IconButton>

            <IconButton
              aria-label="Shopping Cart"
              onClick={() => navigate("/cart")}
              style={{ color: "rgba(255, 255, 255, 0.85)" }}
            >
              <Badge content={totals.itemCount} color="primary">
                <ShoppingBagOutlinedIcon style={{ fontSize: "20px" }} />
              </Badge>
            </IconButton>

            {isAuthenticated ? (
              <>
                <IconButton
                  aria-label="Account Settings"
                  aria-controls="account-menu"
                  aria-haspopup="true"
                  onClick={handleMenuOpen}
                  style={{ 
                    color: "#FFFFFF",
                    backgroundColor: "rgba(255, 255, 255, 0.1)",
                    border: "1px solid rgba(255, 255, 255, 0.15)",
                  }}
                >
                  <PersonOutlineOutlinedIcon style={{ fontSize: "20px" }} />
                </IconButton>
                <Menu
                  id="account-menu"
                  anchorEl={anchorEl}
                  keepMounted
                  open={Boolean(anchorEl)}
                  onClose={handleMenuClose}
                  PaperProps={{
                    style: {
                      borderRadius: "14px",
                      marginTop: "10px",
                      minWidth: "200px",
                      backgroundColor: "#0B1020",
                      color: "#FFFFFF",
                      border: "1px solid rgba(255, 255, 255, 0.12)",
                      boxShadow: "0 20px 40px rgba(0, 0, 0, 0.6)"
                    }
                  }}
                >
                  <MenuItem onClick={() => { handleMenuClose(); navigate("/account?tab=overview"); }}>
                    <ListItemIcon style={{ color: "#7C3AED" }}><DashboardIcon fontSize="small" /></ListItemIcon>
                    Account Dashboard
                  </MenuItem>

                  {(user?.role === "VENDOR" || user?.role === "ADMIN") && (
                    <MenuItem onClick={() => { handleMenuClose(); navigate("/vendor"); }}>
                      <ListItemIcon style={{ color: "#A78BFA" }}><StoreIcon fontSize="small" /></ListItemIcon>
                      Vendor Studio
                    </MenuItem>
                  )}

                  {user?.role === "ADMIN" && (
                    <MenuItem onClick={() => { handleMenuClose(); navigate("/admin"); }}>
                      <ListItemIcon style={{ color: "#3B82F6" }}><AdminIcon fontSize="small" /></ListItemIcon>
                      Admin Console
                    </MenuItem>
                  )}

                  <MenuItem onClick={() => { handleMenuClose(); navigate("/account?tab=downloads"); }}>
                    <ListItemIcon style={{ color: "#60A5FA" }}><VaultIcon fontSize="small" /></ListItemIcon>
                    My Digital Vault
                  </MenuItem>
                  <MenuItem onClick={() => { handleMenuClose(); navigate("/account?tab=orders"); }}>
                    <ListItemIcon style={{ color: "#94A3B8" }}><OrdersIcon fontSize="small" /></ListItemIcon>
                    Orders & Tracking
                  </MenuItem>
                  <MenuItem onClick={() => { handleMenuClose(); navigate("/wishlist"); }}>
                    <ListItemIcon style={{ color: "#EF4444" }}><FavoriteBorderOutlinedIcon fontSize="small" /></ListItemIcon>
                    Wishlist
                  </MenuItem>
                  <MenuItem onClick={handleLogout} style={{ color: "#EF4444" }}>
                    <ListItemIcon style={{ color: "#EF4444" }}><ExitToAppIcon fontSize="small" /></ListItemIcon>
                    Sign Out
                  </MenuItem>
                </Menu>
              </>
            ) : (
              <AuthGroup>
                <SignInTextLink to="/login">
                  Sign In
                </SignInTextLink>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => navigate("/register")}
                >
                  Create Account
                </Button>
              </AuthGroup>
            )}

            {/* Mobile Menu Trigger */}
            <Box display={{ xs: "block", md: "none" }}>
              <IconButton
                aria-label="Open Navigation Menu"
                onClick={() => setMobileOpen(true)}
                style={{ color: "#FFFFFF" }}
              >
                <MenuIcon />
              </IconButton>
            </Box>
          </NavActions>
        </NavContainer>
      </Header>

      {/* Mobile Drawer */}
      <Drawer
        anchor="right"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        PaperProps={{
          style: {
            backgroundColor: "#070B16",
            color: "#FFFFFF",
            width: "300px",
            padding: "24px",
          }
        }}
      >
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
          <img
            src="/brand/bytevault-rrc-logo.png"
            alt="ByteVault"
            style={{ height: "36px", objectFit: "contain" }}
          />
          <IconButton aria-label="Close menu" onClick={() => setMobileOpen(false)} style={{ color: "#FFFFFF" }}>
            <CloseIcon />
          </IconButton>
        </Box>

        <Box display="flex" flexDirection="column" gap={2} mb={4}>
          <Link to="/" onClick={() => setMobileOpen(false)} style={{ color: "#FFFFFF", textDecoration: "none", fontSize: "16px", fontWeight: 600, padding: "8px 0" }}>
            Home
          </Link>
          <Link to="/catalog" onClick={() => setMobileOpen(false)} style={{ color: "#FFFFFF", textDecoration: "none", fontSize: "16px", fontWeight: 600, padding: "8px 0" }}>
            Discover Marketplace
          </Link>
          <Link to="/catalog?type=digital" onClick={() => setMobileOpen(false)} style={{ color: "#A78BFA", textDecoration: "none", fontSize: "14px", padding: "4px 0 4px 12px" }}>
            ↳ Digital Blueprints
          </Link>
          <Link to="/catalog?type=physical" onClick={() => setMobileOpen(false)} style={{ color: "#60A5FA", textDecoration: "none", fontSize: "14px", padding: "4px 0 4px 12px" }}>
            ↳ Workspace Gear
          </Link>
          <Link to="/wishlist" onClick={() => setMobileOpen(false)} style={{ color: "#FFFFFF", textDecoration: "none", fontSize: "16px", fontWeight: 600, padding: "8px 0" }}>
            Saved Wishlist ({wishlistItems.length})
          </Link>
          <Link to="/cart" onClick={() => setMobileOpen(false)} style={{ color: "#FFFFFF", textDecoration: "none", fontSize: "16px", fontWeight: 600, padding: "8px 0" }}>
            Shopping Cart ({totals.itemCount})
          </Link>
        </Box>

        <Box mt="auto" display="flex" flexDirection="column" gap={2}>
          {isAuthenticated ? (
            <>
              <Button variant="outline" size="sm" onClick={() => { setMobileOpen(false); navigate("/account"); }}>
                Developer Dashboard
              </Button>
              <Button variant="danger" size="sm" onClick={() => { setMobileOpen(false); handleLogout(); }}>
                Sign Out
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" size="sm" onClick={() => { setMobileOpen(false); navigate("/login"); }}>
                Sign In
              </Button>
              <Button variant="primary" size="sm" onClick={() => { setMobileOpen(false); navigate("/register"); }}>
                Create Account
              </Button>
            </>
          )}
        </Box>
      </Drawer>

      {/* Main Content Pane */}
      <main style={{ minHeight: "calc(100vh - 72px - 340px)", paddingTop: "72px" }}>
        {children}
      </main>

      {/* Toast Notification */}
      <AnimatePresence>
        {toast && (
          <ToastContainer
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 15, scale: 0.95 }}
            transition={{ duration: 0.2 }}
          >
            {toast.type === "error" ? (
              <span style={{ fontSize: "18px" }}>⚠️</span>
            ) : toast.type === "info" ? (
              <span style={{ fontSize: "18px" }}>ℹ️</span>
            ) : (
              <CheckCircleIcon style={{ color: "#10B981", fontSize: "18px" }} />
            )}
            <span style={{ flex: 1 }}>{toast.message}</span>
            {toast.actionLabel && toast.actionHref && (
              <span
                onClick={() => { setToast(null); navigate(toast.actionHref); }}
                style={{
                  cursor: "pointer",
                  background: "rgba(139,92,246,0.25)",
                  border: "1px solid rgba(139,92,246,0.5)",
                  color: "#A78BFA",
                  borderRadius: "6px",
                  padding: "3px 10px",
                  fontSize: "12px",
                  fontWeight: 600,
                  whiteSpace: "nowrap",
                  transition: "background 0.2s"
                }}
              >
                {toast.actionLabel}
              </span>
            )}
            <span
              onClick={() => setToast(null)}
              style={{ cursor: "pointer", opacity: 0.5, fontSize: "14px", marginLeft: "4px" }}
            >✕</span>
          </ToastContainer>
        )}
      </AnimatePresence>

      {/* Footer */}
      <FooterSection>
        <Container maxWidth="xxl">
          <FooterTrustStrip>
            <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3}>
              <Box display="flex" alignItems="center" gap={2}>
                <span className="bv-live-indicator" />
                <span style={{ fontSize: "13px", fontWeight: 600, color: "#FFFFFF" }}>
                  All Systems Operational
                </span>
                <span style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.4)" }}>·</span>
                <span style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.55)" }}>
                  API Gateways 99.98% uptime
                </span>
              </Box>
              <Box display="flex" alignItems="center" gap={1} fontSize="12px" color="rgba(255, 255, 255, 0.6)">
                <LockIcon style={{ fontSize: "14px", color: "#10B981" }} />
                <span>256-Bit Encrypted Vault Deliveries</span>
              </Box>
            </Box>
          </FooterTrustStrip>

          <FooterGrid>
            {/* Column 1: Brand */}
            <FooterCol>
              <BrandLogoLink
                to="/"
                style={{ marginBottom: "16px" }}
                aria-label="ByteVault Home"
              >
                <img
                  src="/brand/bytevault-rrc-logo.png"
                  alt="ByteVault Logo"
                  style={{
                    height: "44px",
                    width: "auto",
                    maxWidth: "70px",
                    objectFit: "contain",
                  }}
                />
              </BrandLogoLink>
              <p style={{ maxWidth: "300px", lineHeight: 1.6, color: "rgba(255, 255, 255, 0.65)", margin: "0 0 16px 0", fontSize: "13px" }}>
                Curated developer marketplace bridging production-grade software blueprints with tactile workspace hardware.
              </p>
              <Box fontSize="12px" color="rgba(255, 255, 255, 0.45)">
                Vijayawada, India
              </Box>
            </FooterCol>

            {/* Column 2: Explore */}
            <FooterCol>
              <FooterColTitle>Explore</FooterColTitle>
              <FooterLink to="/catalog">All Marketplace</FooterLink>
              <FooterLink to="/catalog?type=digital">Digital Blueprints</FooterLink>
              <FooterLink to="/catalog?category=Software%20%26%20Coding">Developer Scripts</FooterLink>
              <FooterLink to="/catalog?type=physical">Workspace Gear</FooterLink>
              <FooterLink to="/catalog?sortBy=rating">Top Rated Assets</FooterLink>
            </FooterCol>

            {/* Column 3: Customer */}
            <FooterCol>
              <FooterColTitle>Customer</FooterColTitle>
              <FooterLink to="/account?tab=overview">Dashboard</FooterLink>
              <FooterLink to="/account?tab=downloads">Digital Vault</FooterLink>
              <FooterLink to="/account?tab=orders">Order History</FooterLink>
              <FooterLink to="/wishlist">Saved Wishlist</FooterLink>
              <FooterLink to="/account?tab=profile">Account Settings</FooterLink>
            </FooterCol>

            {/* Column 4: Company */}
            <FooterCol>
              <FooterColTitle>Company</FooterColTitle>
              <FooterLink to="/about">About Us</FooterLink>
              <FooterLink to="/faq">Engineering FAQ</FooterLink>
              <FooterLink to="/contact">Developer Support</FooterLink>
              <FooterLink to="/offline">Offline Mode</FooterLink>
            </FooterCol>

            {/* Column 5: Legal */}
            <FooterCol>
              <FooterColTitle>Legal</FooterColTitle>
              <FooterLink to="/privacy">Privacy Policy</FooterLink>
              <FooterLink to="/terms">Terms of Service</FooterLink>
              <FooterLink to="/refund">Refund Policy</FooterLink>
              <FooterLink to="/terms">License Terms</FooterLink>
            </FooterCol>
          </FooterGrid>

          <FooterSub>
            <span>© 2026 ByteVault Media Inc. All engineering assets verified.</span>
            <Box display="flex" gap={3} alignItems="center">
              <span>Encrypted Stripe Checkout</span>
              <span>·</span>
              <span>Instant Signature Entitlements</span>
            </Box>
          </FooterSub>
        </Container>
      </FooterSection>

      {/* Dynamic Interactive Toast Notification with Action Buttons */}
      <AnimatePresence>
        {toast && (
          <ToastContainer
            initial={{ opacity: 0, y: 30, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20, scale: 0.95 }}
            transition={{ duration: 0.25 }}
          >
            {toast.type === "success" && <CheckCircleIcon style={{ color: "#10B981", fontSize: "20px", flexShrink: 0 }} />}
            {toast.type === "error" && <CloseIcon style={{ color: "#EF4444", fontSize: "20px", flexShrink: 0 }} />}
            {toast.type === "info" && <VaultIcon style={{ color: "#38BDF8", fontSize: "20px", flexShrink: 0 }} />}
            <span style={{ flexGrow: 1, marginRight: toast.actionLabel ? "8px" : "0" }}>{toast.message}</span>
            {toast.actionLabel && toast.actionHref && (
              <Button
                variant="primary"
                size="xs"
                onClick={() => {
                  navigate(toast.actionHref);
                  setToast(null);
                }}
                style={{ backgroundColor: "#8B5CF6", borderColor: "#8B5CF6", color: "#FFFFFF", fontWeight: 700, padding: "4px 12px", whiteSpace: "nowrap" }}
              >
                {toast.actionLabel}
              </Button>
            )}
            <IconButton
              size="xs"
              variant="ghost"
              onClick={() => setToast(null)}
              style={{ color: "rgba(255, 255, 255, 0.6)", padding: "2px" }}
            >
              <CloseIcon style={{ fontSize: "16px" }} />
            </IconButton>
          </ToastContainer>
        )}
      </AnimatePresence>
    </>
  );
};

AppLayout.propTypes = {
  children: PropTypes.node.isRequired,
};

export default AppLayout;
