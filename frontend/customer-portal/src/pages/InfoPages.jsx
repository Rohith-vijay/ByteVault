import React, { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import Accordion from "@mui/material/Accordion";
import AccordionSummary from "@mui/material/AccordionSummary";
import AccordionDetails from "@mui/material/AccordionDetails";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import EmailIcon from "@mui/icons-material/Email";
import LocationOnIcon from "@mui/icons-material/LocationOn";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import SecurityIcon from "@mui/icons-material/SecurityOutlined";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Input } from "../components/primitives/Input";
import { Chip } from "../components/primitives/Chip";
import { SectionHeader } from "../components/primitives/SectionHeader";

const InfoLayout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "260px 1fr",
  gap: theme.spacing(8),
  paddingTop: theme.spacing(8),
  paddingBottom: theme.spacing(20),

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
  },
}));

const SideNav = styled("div")({
  display: "flex",
  flexDirection: "column",
  gap: "6px",
  height: "fit-content",
});

const NavItemBtn = styled("button", {
  shouldForwardProp: (prop) => prop !== "active",
})(({ theme, active }) => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  padding: "12px 16px",
  borderRadius: "10px",
  border: "none",
  cursor: "pointer",
  fontSize: "13px",
  fontWeight: 600,
  textAlign: "left",
  backgroundColor: active ? theme.palette.primary.soft : "transparent",
  color: active ? theme.palette.primary.main : theme.palette.text.secondary,
  borderLeft: active ? `3px solid ${theme.palette.primary.main}` : "3px solid transparent",
  transition: "all 0.15s ease",

  "&:hover": {
    backgroundColor: active ? theme.palette.primary.soft : theme.palette.background.elevated,
    color: theme.palette.text.primary,
  }
}));

const EditorialArticle = styled("article")(({ theme }) => ({
  backgroundColor: "#FFFFFF",
  borderRadius: "20px",
  padding: theme.spacing(8),
  border: `1px solid ${theme.palette.border.default}`,
  boxShadow: theme.elevation.subtle,

  "& h2": {
    fontSize: "24px",
    fontWeight: 800,
    margin: "0 0 16px 0",
    letterSpacing: "-0.015em",
  },
  "& h3": {
    fontSize: "18px",
    fontWeight: 700,
    margin: "24px 0 10px 0",
  },
  "& p": {
    fontSize: "15px",
    lineHeight: 1.7,
    color: theme.palette.text.secondary,
    marginBottom: "16px",
  },
  "& ul": {
    paddingLeft: "24px",
    marginBottom: "20px",
    "& li": {
      fontSize: "14px",
      lineHeight: 1.7,
      color: theme.palette.text.secondary,
      marginBottom: "8px",
    }
  }
}));

export const InfoPages = () => {
  const theme = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  
  const path = location.pathname.substring(1);
  const activeTab = path === "refund-policy" || path === "refund" ? "refund" : (path || "about");

  const handleTabChange = (tabName) => {
    navigate(`/${tabName}`);
  };

  const [contactName, setContactName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactMsg, setContactMsg] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleContactSubmit = (e) => {
    e.preventDefault();
    setSubmitting(true);
    setTimeout(() => {
      setSubmitting(false);
      setSubmitted(true);
      setContactName("");
      setContactEmail("");
      setContactMsg("");
    }, 600);
  };

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC" }}>
      <Container maxWidth="xxl">
        <SectionHeader
          label="DOCUMENTATION & LEGAL"
          title="ByteVault Knowledge Center"
          subtitle="Engineering documentation, licensing policies, and developer support contacts."
        />

        <InfoLayout>
          {/* Side Navigation */}
          <SideNav>
            <Card padding={4} radius="lg" elevation="subtle">
              <NavItemBtn active={activeTab === "about"} onClick={() => handleTabChange("about")}>
                <span>About ByteVault</span>
              </NavItemBtn>
              <NavItemBtn active={activeTab === "faq"} onClick={() => handleTabChange("faq")}>
                <span>Engineering FAQ</span>
              </NavItemBtn>
              <NavItemBtn active={activeTab === "contact"} onClick={() => handleTabChange("contact")}>
                <span>Developer Support</span>
              </NavItemBtn>
              <NavItemBtn active={activeTab === "privacy"} onClick={() => handleTabChange("privacy")}>
                <span>Privacy Policy</span>
              </NavItemBtn>
              <NavItemBtn active={activeTab === "terms"} onClick={() => handleTabChange("terms")}>
                <span>Terms of Service</span>
              </NavItemBtn>
              <NavItemBtn active={activeTab === "refund" || activeTab === "refund-policy"} onClick={() => handleTabChange("refund")}>
                <span>Refund Policy</span>
              </NavItemBtn>
            </Card>
          </SideNav>

          {/* Main Editorial Pane */}
          <div>
            {/* 1. ABOUT US */}
            {activeTab === "about" && (
              <EditorialArticle>
                <Box mb={2}>
                  <Chip label="FOUNDED 2026" color="primary" size="xs" uppercase />
                </Box>
                <h2>Bridging Digital Code & Tactile Workspaces</h2>
                <p>
                  ByteVault Media was founded with a unified mission: Eliminate the cognitive friction between architectural software discovery and physical workspace execution.
                </p>
                <p>
                  Modern software engineers spend thousands of hours designing architectures, configuring infrastructure, and building user interfaces. We believe that exceptional code deserves exceptional execution environments.
                </p>

                <h3>Our Quality Standards</h3>
                <ul>
                  <li><strong>Rigorous Double-Audits:</strong> Every software blueprint, UI kit, and Rust template is verified by senior systems architects before publication.</li>
                  <li><strong>Instant Cryptographic Entitlement:</strong> Digital assets are bound directly to your customer vault session, allowing instant multi-device signed downloads.</li>
                  <li><strong>Tactile Hardware Ergonomics:</strong> Our physical workspace gear features machined aluminum cases, hot-swap PCB sockets, and premium vegetable-tanned leathers.</li>
                </ul>
              </EditorialArticle>
            )}

            {/* 2. FAQ */}
            {activeTab === "faq" && (
              <EditorialArticle>
                <h2>Frequently Asked Questions</h2>
                <p>Find answers to common questions regarding our licensing, vault downloads, and shipping times.</p>

                <Box mt={6}>
                  {[
                    {
                      q: "Can I use digital assets in commercial client projects?",
                      a: "Yes. All digital blueprints, UI kits, and code scripts include a full commercial license. You are permitted to integrate them into client products and commercial SaaS applications without per-seat royalties."
                    },
                    {
                      q: "How do updates work for digital blueprints?",
                      a: "When a creator or our internal engineering team issues an update (e.g. React 19 compatibility or Rust 2024 edition migrations), you receive an alert inside your Digital Vault cabinet with a 1-click download button."
                    },
                    {
                      q: "What are the shipping estimates for workspace hardware?",
                      a: "Physical workspace hardware packages ship within 1 to 2 business days via express carrier routes. You will receive active tracking milestones inside your order tracking dashboard."
                    },
                    {
                      q: "How does the 14-day technical guarantee work?",
                      a: "If an asset does not meet your technical expectations or compatibility specifications, submit an automated refund ticket within 14 days of purchase for a complete refund."
                    }
                  ].map((faq, idx) => (
                    <Accordion key={idx} sx={{ mb: 2, borderRadius: "10px !important", border: `1px solid ${theme.palette.border.default}`, "&:before": { display: "none" } }}>
                      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                        <span style={{ fontWeight: 600, fontSize: "14px" }}>{faq.q}</span>
                      </AccordionSummary>
                      <AccordionDetails>
                        <p style={{ margin: 0, fontSize: "14px", lineHeight: 1.6, color: theme.palette.text.secondary }}>
                          {faq.a}
                        </p>
                      </AccordionDetails>
                    </Accordion>
                  ))}
                </Box>
              </EditorialArticle>
            )}

            {/* 3. CONTACT SUPPORT */}
            {activeTab === "contact" && (
              <EditorialArticle>
                <h2>Developer Support & Inquiries</h2>
                <p>Have an inquiry regarding a blueprint, custom bulk gear orders, or partner integrations? Our team responds within 24 hours.</p>

                <Grid container spacing={6} my={4}>
                  <Grid item xs={12} sm={4}>
                    <Card padding={4} backgroundColor={theme.palette.background.elevated} radius="md">
                      <EmailIcon style={{ color: theme.palette.primary.main, fontSize: "24px", marginBottom: "8px" }} />
                      <div style={{ fontSize: "12px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase" }}>EMAIL INQUIRIES</div>
                      <div style={{ fontSize: "13px", fontWeight: 600 }}>support@bytevault.com</div>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <Card padding={4} backgroundColor={theme.palette.background.elevated} radius="md">
                      <LocationOnIcon style={{ color: theme.palette.accent.main, fontSize: "24px", marginBottom: "8px" }} />
                      <div style={{ fontSize: "12px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase" }}>HEADQUARTERS</div>
                      <div style={{ fontSize: "13px", fontWeight: 600 }}>San Francisco, CA</div>
                    </Card>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <Card padding={4} backgroundColor={theme.palette.background.elevated} radius="md">
                      <SecurityIcon style={{ color: theme.palette.status.success, fontSize: "24px", marginBottom: "8px" }} />
                      <div style={{ fontSize: "12px", fontWeight: 700, color: theme.palette.text.muted, textTransform: "uppercase" }}>SUPPORT SLA</div>
                      <div style={{ fontSize: "13px", fontWeight: 600 }}>99.9% 24h Response</div>
                    </Card>
                  </Grid>
                </Grid>

                {submitted ? (
                  <Box p={6} backgroundColor="#ECFDF5" border="1px solid #10B981" borderRadius="12px" textAlign="center">
                    <CheckCircleIcon style={{ color: "#10B981", fontSize: "36px", marginBottom: "8px" }} />
                    <h4 style={{ margin: "0 0 8px 0" }}>Inquiry Dispatched Successfully</h4>
                    <p style={{ margin: 0, fontSize: "13px", color: theme.palette.text.secondary }}>
                      Our developer relations team will get back to you shortly.
                    </p>
                  </Box>
                ) : (
                  <form onSubmit={handleContactSubmit}>
                    <Box display="flex" flexDirection="column" gap={4} maxWidth="560px">
                      <Input
                        label="Your Name"
                        placeholder="Alex Rivera"
                        value={contactName}
                        onChange={(e) => setContactName(e.target.value)}
                        required
                        fullWidth
                      />
                      <Input
                        label="Email Address"
                        type="email"
                        placeholder="alex@enterprise.com"
                        value={contactEmail}
                        onChange={(e) => setContactEmail(e.target.value)}
                        required
                        fullWidth
                      />
                      <div>
                        <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.05em", color: theme.palette.text.secondary, display: "block", marginBottom: "6px" }}>
                          Inquiry Details
                        </label>
                        <textarea
                          rows={5}
                          placeholder="Describe your technical inquiry..."
                          value={contactMsg}
                          onChange={(e) => setContactMsg(e.target.value)}
                          required
                          style={{
                            width: "100%",
                            padding: "12px 14px",
                            borderRadius: "10px",
                            border: `1px solid ${theme.palette.border.default}`,
                            fontFamily: theme.typography.fontFamily,
                            fontSize: "14px",
                            outline: "none",
                            resize: "vertical",
                          }}
                        />
                      </div>
                      <Button variant="primary" size="lg" type="submit" state={submitting ? "loading" : "default"}>
                        Dispatch Inquiry
                      </Button>
                    </Box>
                  </form>
                )}
              </EditorialArticle>
            )}

            {/* 4. PRIVACY POLICY */}
            {activeTab === "privacy" && (
              <EditorialArticle>
                <h2>Privacy & Cryptographic Data Policy</h2>
                <p style={{ fontSize: "12px", color: theme.palette.text.muted }}>Last Updated: August 31, 2026</p>
                <p>
                  ByteVault Media values developer privacy and data protection. This policy outlines how information is gathered, encrypted, and utilized.
                </p>
                <h3>1. Information Collected</h3>
                <p>We only collect account identifiers necessary to issue signed digital entitlements and process physical shipping logistics.</p>
                <h3>2. Zero Telemetry in Blueprints</h3>
                <p>All downloadable source code templates and Figma assets are 100% telemetry-free and contain no tracking scripts or phone-home mechanisms.</p>
              </EditorialArticle>
            )}

            {/* 5. TERMS OF SERVICE */}
            {activeTab === "terms" && (
              <EditorialArticle>
                <h2>Terms of Service & Commercial License</h2>
                <p style={{ fontSize: "12px", color: theme.palette.text.muted }}>Last Updated: August 31, 2026</p>
                <p>By accessing ByteVault Media services and purchasing engineering blueprints, you agree to these binding terms.</p>
                <h3>1. Grant of License</h3>
                <p>Each digital asset purchase grants a perpetual, worldwide, non-exclusive commercial license to deploy the code in unlimited production projects.</p>
                <h3>2. Redistribution Restrictions</h3>
                <p>You may not redistribute or resell unmodified raw templates or blueprint source files on third-party marketplace platforms.</p>
              </EditorialArticle>
            )}

            {/* 6. REFUND POLICY */}
            {(activeTab === "refund" || activeTab === "refund-policy") && (
              <EditorialArticle>
                <h2>14-Day Technical Refund Guarantee</h2>
                <p style={{ fontSize: "12px", color: theme.palette.text.muted }}>Last Updated: August 31, 2026</p>
                <p>
                  We stand behind the engineering fidelity of our marketplace catalog. If an asset is defective or incompatible with your documented tech stack, you are protected by our 14-day technical guarantee.
                </p>
                <h3>Eligible Refund Conditions</h3>
                <ul>
                  <li>Software blueprint exhibits critical reproducible defects not resolved within 48 hours.</li>
                  <li>Workspace gear arrives damaged or defective in transit.</li>
                  <li>License activation or cryptographic signing fails to verify.</li>
                </ul>
              </EditorialArticle>
            )}
          </div>
        </InfoLayout>
      </Container>
    </Box>
  );
};

export default InfoPages;
