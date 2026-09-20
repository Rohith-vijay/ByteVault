import React, { useState } from "react";
import { styled, useTheme } from "@mui/material/styles";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import AddIcon from "@mui/icons-material/Add";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import InfoIcon from "@mui/icons-material/Info";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InboxIcon from "@mui/icons-material/Inbox";
import { AnimatePresence, motion } from "framer-motion";

// Design Token / Primitive Imports
import { tokens } from "../theme/tokens";
import { Container } from "../components/primitives/Container";
import { SectionHeader } from "../components/primitives/SectionHeader";
import { Button } from "../components/primitives/Button";
import { IconButton } from "../components/primitives/IconButton";
import { Input } from "../components/primitives/Input";
import { Card } from "../components/primitives/Card";
import { Chip } from "../components/primitives/Chip";
import { Badge } from "../components/primitives/Badge";
import { Price } from "../components/primitives/Price";
import { Rating } from "../components/primitives/Rating";
import { Tooltip } from "../components/primitives/Tooltip";
import { Skeleton } from "../components/primitives/Skeleton";
import { EmptyState } from "../components/primitives/EmptyState";
import { ErrorState } from "../components/primitives/ErrorState";

// Features / Mock Imports
import { ProductCard } from "../features/products/components/ProductCard/ProductCard";
import { mockProducts } from "../features/products/mockData";
import { slideUpVariants, checkReducedMotion } from "../animations/motion";

const PageWrapper = styled("div")(({ theme }) => ({
  backgroundColor: theme.palette.background.default,
  minHeight: "100vh",
  paddingTop: theme.spacing(12),
  paddingBottom: theme.spacing(16),
}));

const PlaygroundHeader = styled("header")(({ theme }) => ({
  marginBottom: theme.spacing(10),
  borderBottom: `1px solid ${theme.palette.border.default}`,
  paddingBottom: theme.spacing(6),
}));

const BrandTitle = styled("h1")(({ theme }) => ({
  ...theme.typography.display,
  color: theme.palette.text.primary,
  marginBottom: theme.spacing(2),
}));

const TabPanelContainer = styled("div")(({ theme }) => ({
  marginTop: theme.spacing(8),
}));

const ColorBlock = styled("div")(({ theme, blockColor }) => ({
  height: "72px",
  backgroundColor: blockColor,
  borderRadius: theme.radius.sm,
  border: `1px solid ${theme.palette.border.default}`,
  boxShadow: theme.elevation.subtle,
  marginBottom: theme.spacing(2),
}));

const SpacingRow = styled("div")(({ theme, rowHeight }) => ({
  height: rowHeight,
  backgroundColor: theme.palette.primary.soft,
  borderRadius: theme.radius.xs,
  border: `1px solid ${theme.palette.primary.main}33`,
  display: "flex",
  alignItems: "center",
  justifyContent: "flex-end",
  paddingRight: theme.spacing(3),
  fontSize: "11px",
  color: theme.palette.primary.main,
  fontWeight: "bold",
  fontFamily: "monospace",
}));

const TabPanel = ({ children, value, index }) => (
  <TabPanelContainer role="tabpanel" hidden={value !== index} id={`tabpanel-${index}`}>
    {value === index && children}
  </TabPanelContainer>
);

export const DesignSystemPlayground = () => {
  const muiTheme = useTheme();
  const [activeTab, setActiveTab] = useState(0);

  const [btnLoading, setBtnLoading] = useState(false);
  const [btnState, setBtnState] = useState("default");
  const [inputText, setInputText] = useState("");
  const [inputErr, setInputErr] = useState(false);

  const [demoVisible, setDemoVisible] = useState(true);

  const handleTabChange = (_event, newValue) => {
    setActiveTab(newValue);
  };

  const triggerStateChange = (state) => {
    setBtnLoading(true);
    setTimeout(() => {
      setBtnLoading(false);
      setBtnState(state);
      setTimeout(() => {
        setBtnState("default");
      }, 2000);
    }, 1200);
  };

  return (
    <PageWrapper>
      <Container maxWidth="xl">
        <PlaygroundHeader>
          <BrandTitle>Design System Playground</BrandTitle>
          <p style={{ color: muiTheme.palette.text.secondary, marginTop: 0 }}>
            Antigravity Enterprise Marketplace UI Foundation — Phase F0 Sandbox (JavaScript Version)
          </p>
          <Tabs
            value={activeTab}
            onChange={handleTabChange}
            variant="scrollable"
            scrollButtons="auto"
            indicatorColor="primary"
            textColor="primary"
            style={{ marginTop: muiTheme.spacing(6) }}
          >
            <Tab label="1. Colors & Layout" />
            <Tab label="2. Typography & Spacing" />
            <Tab label="3. Primitive Controls" />
            <Tab label="4. Interactive States" />
            <Tab label="5. Motion & Physics" />
            <Tab label="6. Product Cards & Grid" />
          </Tabs>
        </PlaygroundHeader>

        {/* 1. COLORS & LAYOUT */}
        <TabPanel value={activeTab} index={0}>
          <SectionHeader
            title="Color Tokens & Layout Geometry"
            subtitle="Central semantic values for backgrounds, primary text, and accents."
            label="Visual Core"
          />

          <h3 style={{ ...muiTheme.typography.h3, marginBottom: muiTheme.spacing(4) }}>Semantic Color Palette</h3>
          <Grid container spacing={4}>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.background.default} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Background Default</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.background.default}</p>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.background.surface} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Background Surface</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.background.surface}</p>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.background.elevated} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Background Elevated</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.background.elevated}</p>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.primary.main} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Primary Main</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.primary.main}</p>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.accent.main} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Accent Main</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.accent.main}</p>
              </Card>
            </Grid>

            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.status.success} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Status Success</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.status.success}</p>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.status.warning} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Status Warning</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.status.warning}</p>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Card padding={4} elevation="subtle">
                <ColorBlock blockColor={tokens.color.status.error} />
                <span style={{ fontWeight: "bold", fontSize: "13px" }}>Status Error</span>
                <p style={{ margin: 0, fontSize: "11px", color: tokens.color.text.secondary }}>{tokens.color.status.error}</p>
              </Card>
            </Grid>
          </Grid>

          <h3 style={{ ...muiTheme.typography.h3, marginTop: muiTheme.spacing(8), marginBottom: muiTheme.spacing(4) }}>Border Radii & Shapes</h3>
          <Grid container spacing={3}>
            {Object.entries(tokens.radius).map(([key, val]) => (
              <Grid item xs={6} sm={4} md={2} key={key}>
                <Card
                  padding={4}
                  elevation="subtle"
                  style={{
                    borderRadius: val,
                    textAlign: "center",
                    border: `2px solid ${muiTheme.palette.primary.main}`,
                  }}
                >
                  <span style={{ fontFamily: "monospace", fontSize: "12px", fontWeight: "bold" }}>
                    radius.{key} ({val})
                  </span>
                </Card>
              </Grid>
            ))}
          </Grid>

          <h3 style={{ ...muiTheme.typography.h3, marginTop: muiTheme.spacing(8), marginBottom: muiTheme.spacing(4) }}>Elevation Levels</h3>
          <Grid container spacing={4}>
            {Object.entries(tokens.elevation).map(([key, val]) => (
              <Grid item xs={12} sm={6} md={2.4} key={key}>
                <div
                  style={{
                    padding: muiTheme.spacing(6),
                    backgroundColor: muiTheme.palette.background.paper,
                    borderRadius: muiTheme.radius.md,
                    boxShadow: val,
                    border: key === "none" ? `1px solid ${muiTheme.palette.border.default}` : "none",
                    textAlign: "center",
                  }}
                >
                  <span style={{ fontWeight: "bold", fontSize: "13px" }}>elevation.{key}</span>
                </div>
              </Grid>
            ))}
          </Grid>
        </TabPanel>

        {/* 2. TYPOGRAPHY & SPACING */}
        <TabPanel value={activeTab} index={1}>
          <SectionHeader
            title="Typography Hierarchy & Spacing scale"
            subtitle="Visual grid system demonstrating font scales and spacing units."
            label="Grid & Copy"
          />

          <Grid container spacing={6}>
            <Grid item xs={12} md={7}>
              <Card padding={6}>
                <SectionHeader title="Responsive Typographies" subtitle="Adaptive layout text blocks" />
                <Box display="flex" flexDirection="column" gap={4}>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>display</span>
                    <h1 style={{ ...muiTheme.typography.display, margin: 0 }}>Premium Technology</h1>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>h1</span>
                    <h1 style={{ ...muiTheme.typography.h1, margin: 0 }}>Marketplace Core</h1>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>h2</span>
                    <h2 style={{ ...muiTheme.typography.h2, margin: 0 }}>Trending Collections</h2>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>h3</span>
                    <h3 style={{ ...muiTheme.typography.h3, margin: 0 }}>Responsive Sections</h3>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>productTitle & price</span>
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <span style={{ ...muiTheme.typography.productTitle }}>Minimalist Leather Backpack</span>
                      <Price amount={135.00} originalAmount={160.00} size="md" />
                    </Box>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>bodyLarge</span>
                    <p style={{ ...muiTheme.typography.bodyLarge, margin: 0 }}>
                      This is a body large copy used for hero sections or intro statements. Extremely readable and spacious.
                    </p>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>body (body1)</span>
                    <p style={{ ...muiTheme.typography.body1, margin: 0 }}>
                      This is the default body copy used throughout the platform. Comfortable line-height ensures easy scanning for product listings and descriptions.
                    </p>
                  </div>
                  <div>
                    <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>label (Uppercase metadata headers)</span>
                    <div>
                      <span style={{ ...muiTheme.typography.label }}>digital product</span>
                    </div>
                  </div>
                </Box>
              </Card>
            </Grid>

            <Grid item xs={12} md={5}>
              <Card padding={6}>
                <SectionHeader title="Spacing Tokens" subtitle="Rigid 4px grid blocks" />
                <Box display="flex" flexDirection="column" gap={2}>
                  {Object.entries(tokens.spacing).map(([key, val]) => (
                    <Box key={key}>
                      <Box display="flex" justifyContent="space-between" mb={0.5}>
                        <span style={{ fontSize: "11px", fontWeight: "bold" }}>spacing.{key}</span>
                        <span style={{ fontSize: "11px", color: muiTheme.palette.text.secondary }}>{val}</span>
                      </Box>
                      <SpacingRow rowHeight={val}>{val}</SpacingRow>
                    </Box>
                  ))}
                </Box>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* 3. PRIMITIVE CONTROLS */}
        <TabPanel value={activeTab} index={2}>
          <SectionHeader
            title="Design System Primitives"
            subtitle="Core domain-independent presentation components."
            label="Elements"
          />

          <Grid container spacing={6}>
            <Grid item xs={12} md={6}>
              <Card padding={6}>
                <h3>Buttons & Chips</h3>
                <Box display="flex" flexWrap="wrap" gap={3} mb={6}>
                  <Button variant="primary">Primary Button</Button>
                  <Button variant="accent">Accent Alert</Button>
                  <Button variant="secondary">Secondary Outline</Button>
                  <Button variant="primary" disabled>Disabled Action</Button>
                </Box>

                <Box display="flex" flexWrap="wrap" gap={2} mb={6}>
                  <IconButton aria-label="Add to cart" tooltipText="Add to Shopping Cart">
                    <ShoppingCartIcon />
                  </IconButton>
                  <IconButton aria-label="Details" variant="outlined" tooltipText="Read Specifications">
                    <InfoIcon />
                  </IconButton>
                  <IconButton aria-label="Config" variant="filled" tooltipText="Settings panel">
                    <AddIcon />
                  </IconButton>
                </Box>

                <Box display="flex" flexWrap="wrap" gap={2}>
                  <Chip label="Success soft" color="success" />
                  <Chip label="Error outline" color="error" variant="outlined" />
                  <Chip label="Warning filled" color="warning" variant="filled" />
                  <Chip label="Accent uppercase" color="accent" uppercase />
                </Box>
              </Card>
            </Grid>

            <Grid item xs={12} md={6}>
              <Card padding={6}>
                <h3>Input Controls</h3>
                <Box display="flex" flexDirection="column" gap={4}>
                  <Input label="Email Address" placeholder="alex@enterprise.com" />
                  <Input
                    label="Username"
                    value="alex_dev"
                    inputState="success"
                    helperText="Username is available"
                    readOnly
                  />
                  <Input
                    label="Password"
                    type="password"
                    placeholder="••••••••"
                    inputState="error"
                    helperText="Password must be at least 8 characters"
                  />
                </Box>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card padding={6}>
                <h3>Skeleton Loaders</h3>
                <Box display="flex" flexDirection="column" gap={3}>
                  <Skeleton variant="circular" width={60} height={60} />
                  <Skeleton variant="text" width="60%" />
                  <Skeleton variant="rectangular" height={120} radius="lg" />
                </Box>
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card padding={6}>
                <h3>Empty State Display</h3>
                <EmptyState
                  icon={<InboxIcon />}
                  title="No downloads found"
                  description="Your digital cabinet is currently empty. Shop digital products to add files."
                  actionText="Explore Digital"
                  onActionClick={() => alert("Navigate to Digital Products")}
                />
              </Card>
            </Grid>

            <Grid item xs={12} md={4}>
              <Card padding={6}>
                <h3>Error State Display</h3>
                <ErrorState
                  title="Connection Timeout"
                  message="Failed to synchronize with inventory service. Please check your connection."
                  onRetry={() => alert("Retrying connection...")}
                />
              </Card>
            </Grid>

            {/* Badges, Ratings, & Tooltips Demo Card */}
            <Grid item xs={12} md={12}>
              <Card padding={6}>
                <h3>Badges, Ratings, & Tooltips</h3>
                <Box display="flex" flexWrap="wrap" gap={6} alignItems="center">
                  <Box display="flex" gap={4} alignItems="center">
                    <span style={{ fontSize: "12px", color: muiTheme.palette.text.secondary }}>Badges:</span>
                    <Badge content={4} color="primary">
                      <ShoppingCartIcon />
                    </Badge>
                    <Badge content="NEW" color="accent">
                      <Chip label="Products" />
                    </Badge>
                    <Badge content="!" color="error">
                      <IconButton aria-label="Notifications"><InboxIcon /></IconButton>
                    </Badge>
                  </Box>

                  <Box display="flex" gap={2} alignItems="center" style={{ borderLeft: `1px solid ${muiTheme.palette.border.default}`, paddingLeft: muiTheme.spacing(6) }}>
                    <span style={{ fontSize: "12px", color: muiTheme.palette.text.secondary }}>Interactive Rating:</span>
                    <Rating value={4.5} count={12} readOnly={false} size="sm" onChange={(v) => alert(`You rated this ${v} stars!`)} />
                  </Box>

                  <Box display="flex" gap={2} alignItems="center" style={{ borderLeft: `1px solid ${muiTheme.palette.border.default}`, paddingLeft: muiTheme.spacing(6) }}>
                    <span style={{ fontSize: "12px", color: muiTheme.palette.text.secondary }}>Tooltips:</span>
                    <Tooltip title="This is a semantic tooltip indicator" arrow>
                      <span>
                        <Button variant="secondary">Hover me</Button>
                      </span>
                    </Tooltip>
                  </Box>
                </Box>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* 4. INTERACTIVE STATES */}
        <TabPanel value={activeTab} index={3}>
          <SectionHeader
            title="Interaction States"
            subtitle="Verify buttons, input validation transitions, and states."
            label="Feedbacks"
          />

          <Grid container spacing={6}>
            <Grid item xs={12} md={6}>
              <Card padding={6}>
                <h3>Interactive Button State Sandbox</h3>
                <p style={{ ...muiTheme.typography.body2, color: muiTheme.palette.text.secondary }}>
                  Click below to simulate API dispatch calls that trigger load sequences and feedback overlays on buttons.
                </p>
                <Box display="flex" flexWrap="wrap" gap={3} mt={4}>
                  <Button
                    variant="primary"
                    state={btnState === "default" && btnLoading ? "loading" : btnState}
                    onClick={() => triggerStateChange("success")}
                  >
                    Simulate Success Action
                  </Button>
                  <Button
                    variant="accent"
                    state={btnState === "default" && btnLoading ? "loading" : btnState}
                    onClick={() => triggerStateChange("error")}
                  >
                    Simulate Error Action
                  </Button>
                </Box>
                <Box mt={4} display="flex" alignItems="center" gap={2}>
                  {btnLoading && <Chip label="Processing..." color="info" />}
                  {btnState === "success" && <Chip label="Action Succeeded" color="success" leftIcon={<CheckCircleIcon />} />}
                  {btnState === "error" && <Chip label="Action Failed" color="error" leftIcon={<ErrorIcon />} />}
                </Box>
              </Card>
            </Grid>

            <Grid item xs={12} md={6}>
              <Card padding={6}>
                <h3>Live Input Validation</h3>
                <Input
                  label="Interactive Validation (Type 'error' to check state)"
                  value={inputText}
                  onChange={(e) => {
                    const text = e.target.value;
                    setInputText(text);
                    setInputErr(text.toLowerCase() === "error");
                  }}
                  inputState={inputErr ? "error" : inputText ? "success" : "default"}
                  helperText={
                    inputErr
                      ? "Validation failed: 'error' string detected."
                      : inputText
                      ? "Validation passed"
                      : "Type in field to test transitions."
                  }
                  fullWidth
                />
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* 5. MOTION & PHYSICS */}
        <TabPanel value={activeTab} index={4}>
          <SectionHeader
            title="Motion, Animation & Transitions"
            subtitle="Demos showcasing spring physics, micro interactions, and entering card fades."
            label="Interaction Quality"
          />

          <Box mb={6}>
            <Button onClick={() => setDemoVisible(!demoVisible)} variant="secondary">
              Toggle Animation Entrance ({demoVisible ? "Hide" : "Show"})
            </Button>
            {checkReducedMotion() && (
              <Box mt={2}>
                <Chip label="OS Reduced Motion Detected — Fades active, shifts disabled" color="warning" />
              </Box>
            )}
          </Box>

          <Grid container spacing={4}>
            <AnimatePresence>
              {demoVisible && (
                <>
                  <Grid item xs={12} sm={6} md={3} component={motion.div} variants={slideUpVariants} initial="initial" animate="animate" exit="exit">
                    <Card padding={6}>
                      <h4>1. Component Slide Up</h4>
                      <p style={{ ...muiTheme.typography.body2, color: muiTheme.palette.text.secondary }}>
                        Uses component standard duration (280ms) and ease-out deceleration curve for fluid entry.
                      </p>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={6} md={3} component={motion.div} variants={slideUpVariants} initial="initial" animate="animate" exit="exit" transition={{ delay: 0.1 }}>
                    <Card padding={6}>
                      <h4>2. Delayed Slide</h4>
                      <p style={{ ...muiTheme.typography.body2, color: muiTheme.palette.text.secondary }}>
                        Staggered entry showing layout continuity.
                      </p>
                    </Card>
                  </Grid>
                </>
              )}
            </AnimatePresence>

            <Grid item xs={12} sm={6} md={6}>
              <Card padding={6}>
                <h4>Spring Micro Interactive Hover</h4>
                <p style={{ ...muiTheme.typography.body2, color: muiTheme.palette.text.secondary, marginBottom: muiTheme.spacing(4) }}>
                  Hover/click cards below to test physical spring properties (stiffness: 300, damping: 26).
                </p>
                <Grid container spacing={3}>
                  <Grid item xs={6}>
                    <Card interactive elevation="subtle" padding={4} onClick={() => {}}>
                      <Box display="flex" alignItems="center" gap={2}>
                        <ShoppingCartIcon style={{ color: muiTheme.palette.primary.main }} />
                        <span>Tactile Card</span>
                      </Box>
                    </Card>
                  </Grid>
                  <Grid item xs={6}>
                    <Card interactive elevation="subtle" padding={4} onClick={() => {}}>
                      <Box display="flex" alignItems="center" gap={2}>
                        <CheckCircleIcon style={{ color: muiTheme.palette.success.main }} />
                        <span>Framer Motion</span>
                      </Box>
                    </Card>
                  </Grid>
                </Grid>
              </Card>
            </Grid>
          </Grid>
        </TabPanel>

        {/* 6. PRODUCT CARDS & GRID */}
        <TabPanel value={activeTab} index={5}>
          <SectionHeader
            title="Product Card Components"
            subtitle="Examines digital and physical variations including image zooms, wishlist toggles, and layout stability."
            label="Product Foundation"
          />

          <Grid container spacing={4}>
            {mockProducts.map((prod) => (
              <Grid item xs={12} sm={6} md={4} lg={2.4} key={prod.id}>
                <ProductCard
                  product={prod}
                  onAddToCart={(p) => alert(`Mock Add To Cart: ${p.title}`)}
                  onWishlistToggle={(p, f) => console.log(`Wishlist toggled for ${p.title}: ${f}`)}
                />
              </Grid>
            ))}
          </Grid>
        </TabPanel>
      </Container>
    </PageWrapper>
  );
};
