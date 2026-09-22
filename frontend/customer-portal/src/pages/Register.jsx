import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import {
  ErrorOutlineOutlined as ErrorOutlineIcon,
  ArrowForward as ArrowForwardIcon,
  ShoppingBagOutlined as CustomerIcon,
  Code as DeveloperIcon,
  CheckCircle as CheckIcon
} from "@mui/icons-material";

import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { useAuth } from "../store/AuthContext";

const AuthWrapper = styled("div")(({ theme }) => ({
  minHeight: "calc(100vh - 72px)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  padding: `${theme.spacing(12)} ${theme.spacing(4)}`,
  backgroundColor: "#070B16",
  color: "#FFFFFF",
  position: "relative",
  overflow: "hidden",
}));

const FormContainer = styled("div")({
  maxWidth: "520px",
  width: "100%",
  zIndex: 2,
});

const RoleGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "1fr 1fr",
  gap: theme.spacing(3),
  marginBottom: theme.spacing(6),

  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
  },
}));

const RoleCard = styled("div", {
  shouldForwardProp: (prop) => prop !== "selected",
})(({ theme, selected }) => ({
  backgroundColor: selected ? "rgba(124, 58, 237, 0.12)" : "rgba(255, 255, 255, 0.04)",
  border: selected 
    ? "2px solid #7C3AED" 
    : "1px solid rgba(255, 255, 255, 0.12)",
  borderRadius: "14px",
  padding: theme.spacing(4),
  cursor: "pointer",
  transition: "all 0.2s cubic-bezier(0.16, 1, 0.3, 1)",
  display: "flex",
  flexDirection: "column",
  gap: "8px",
  position: "relative",
  outline: "none",

  "&:hover": {
    borderColor: selected ? "#8B5CF6" : "rgba(255, 255, 255, 0.3)",
    backgroundColor: selected ? "rgba(124, 58, 237, 0.16)" : "rgba(255, 255, 255, 0.07)",
    transform: "translateY(-2px)",
  },

  "&:focus-visible": {
    boxShadow: "0 0 0 3px rgba(124, 58, 237, 0.4)",
  }
}));

const RoleIconContainer = styled("div", {
  shouldForwardProp: (prop) => prop !== "selected",
})(({ selected }) => ({
  width: "36px",
  height: "36px",
  borderRadius: "8px",
  backgroundColor: selected ? "#7C3AED" : "rgba(255, 255, 255, 0.1)",
  color: "#FFFFFF",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  transition: "background-color 0.2s ease",
}));

const CheckmarkIndicator = styled("div")({
  position: "absolute",
  top: "12px",
  right: "12px",
  color: "#7C3AED",
  display: "flex",
  alignItems: "center",
});

const InputField = styled("input")({
  width: "100%",
  padding: "12px 14px",
  borderRadius: "10px",
  border: "1px solid rgba(255, 255, 255, 0.15)",
  backgroundColor: "rgba(255, 255, 255, 0.05)",
  color: "#FFFFFF",
  fontSize: "14px",
  outline: "none",
  transition: "all 0.2s ease",

  "&:focus": {
    borderColor: "#7C3AED",
    backgroundColor: "rgba(255, 255, 255, 0.08)",
    boxShadow: "0 0 0 3px rgba(124, 58, 237, 0.25)",
  },

  "&::placeholder": {
    color: "rgba(255, 255, 255, 0.4)",
  }
});

const FieldLabel = styled("label")({
  fontSize: "11px",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.06em",
  color: "rgba(255, 255, 255, 0.65)",
  marginBottom: "6px",
  display: "block",
});

export const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();

  const [role, setRole] = useState("CUSTOMER"); // "CUSTOMER" | "VENDOR"
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [storeName, setStoreName] = useState("");
  const [storeDescription, setStoreDescription] = useState("");
  const [businessTaxId, setBusinessTaxId] = useState("");
  const [supportEmail, setSupportEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [formError, setFormError] = useState(null);

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setFormError(null);

    if (password.length < 8) {
      setFormError("Password must be at least 8 characters long.");
      return;
    }

    if (password !== confirmPassword) {
      setFormError("Passwords do not match. Please verify your entries.");
      return;
    }

    if (role === "VENDOR" && (!storeName || !storeName.trim())) {
      setFormError("Store name is required for vendor registration.");
      return;
    }

    setLoading(true);
    try {
      const vendorData = role === "VENDOR" ? {
        storeName: storeName.trim(),
        storeDescription: storeDescription.trim(),
        businessTaxId: businessTaxId.trim(),
        supportEmail: supportEmail.trim() || email.trim()
      } : {};

      await register(name, email, password, role, vendorData);
      navigate("/", { replace: true });
    } catch (err) {
      setFormError(err.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };


  return (
    <AuthWrapper className="bv-tech-grid bv-glow-vault">
      <FormContainer>
        <Card 
          padding={8} 
          radius="xl" 
          elevation="darkCard"
          style={{ 
            backgroundColor: "#0B1020", 
            border: "1px solid rgba(255, 255, 255, 0.12)",
            color: "#FFFFFF" 
          }}
        >
          <Box textAlign="center" mb={5}>
            <Box mb={2} display="inline-flex">
              <Chip 
                label={role === "VENDOR" ? "VENDOR ACCOUNT" : "CUSTOMER ACCOUNT"} 
                color="primary" 
                variant="filled" 
                uppercase 
              />
            </Box>
            <h2 style={{ fontSize: "26px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
              Join ByteVault Media
            </h2>
            <p style={{ margin: 0, fontSize: "14px", color: "rgba(255, 255, 255, 0.65)" }}>
              Access production-grade blueprints, cryptographic downloads, and hardware gear.
            </p>
          </Box>

          {formError && (
            <Box 
              p={4} 
              mb={5} 
              backgroundColor="rgba(239, 68, 68, 0.15)" 
              border="1px solid #EF4444" 
              borderRadius="10px" 
              color="#FCA5A5" 
              fontSize="13px" 
              display="flex" 
              alignItems="center" 
              gap={1.5}
            >
              <ErrorOutlineIcon style={{ fontSize: "18px", flexShrink: 0 }} />
              <span>{formError}</span>
            </Box>
          )}

          <form onSubmit={handleRegisterSubmit}>
            {/* 1. Account Role Selector Cards */}
            <Box mb={2}>
              <FieldLabel>Choose Account Type</FieldLabel>
            </Box>
            <RoleGrid role="radiogroup" aria-label="Account Type">
              {/* Customer Role */}
              <RoleCard
                role="radio"
                aria-checked={role === "CUSTOMER"}
                tabIndex={0}
                selected={role === "CUSTOMER"}
                onClick={() => setRole("CUSTOMER")}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    setRole("CUSTOMER");
                  }
                }}
              >
                {role === "CUSTOMER" && (
                  <CheckmarkIndicator>
                    <CheckIcon style={{ fontSize: "18px" }} />
                  </CheckmarkIndicator>
                )}
                <RoleIconContainer selected={role === "CUSTOMER"}>
                  <CustomerIcon style={{ fontSize: "18px" }} />
                </RoleIconContainer>
                <div>
                  <strong style={{ fontSize: "14px", color: "#FFFFFF", display: "block" }}>
                    Customer
                  </strong>
                  <span style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.6)", lineHeight: 1.4, display: "block", marginTop: "2px" }}>
                    Buy blueprints, digital assets, and workspace gear.
                  </span>
                </div>
              </RoleCard>

              {/* Vendor Role */}
              <RoleCard
                role="radio"
                aria-checked={role === "VENDOR"}
                tabIndex={0}
                selected={role === "VENDOR"}
                onClick={() => setRole("VENDOR")}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    setRole("VENDOR");
                  }
                }}
              >
                {role === "VENDOR" && (
                  <CheckmarkIndicator>
                    <CheckIcon style={{ fontSize: "18px" }} />
                  </CheckmarkIndicator>
                )}
                <RoleIconContainer selected={role === "VENDOR"}>
                  <DeveloperIcon style={{ fontSize: "18px" }} />
                </RoleIconContainer>
                <div>
                  <strong style={{ fontSize: "14px", color: "#FFFFFF", display: "block" }}>
                    Vendor
                  </strong>
                  <span style={{ fontSize: "12px", color: "rgba(255, 255, 255, 0.6)", lineHeight: 1.4, display: "block", marginTop: "2px" }}>
                    List products, publish blueprints, and reach customers.
                  </span>
                </div>
              </RoleCard>
            </RoleGrid>

            {/* 2. Text Input Fields */}
            <Box display="flex" flexDirection="column" gap={4}>
              <div>
                <FieldLabel>Full Name / Organization</FieldLabel>
                <InputField
                  type="text"
                  placeholder="Alex Rivera"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>

              <div>
                <FieldLabel>Email Address</FieldLabel>
                <InputField
                  type="email"
                  placeholder="alex.rivera@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <div>
                <FieldLabel>Password</FieldLabel>
                <InputField
                  type="password"
                  placeholder="••••••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              <div>
                <FieldLabel>Confirm Password</FieldLabel>
                <InputField
                  type="password"
                  placeholder="••••••••••••"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
              </div>

              {/* 3. Vendor Store Onboarding Fields */}
              {role === "VENDOR" && (
                <Box 
                  p={4} 
                  borderRadius="12px" 
                  backgroundColor="rgba(124, 58, 237, 0.08)" 
                  border="1px solid rgba(124, 58, 237, 0.3)"
                  display="flex" 
                  flexDirection="column" 
                  gap={3.5}
                >
                  <Box display="flex" alignItems="center" gap={1.5} pb={1} borderBottom="1px solid rgba(124, 58, 237, 0.2)">
                    <DeveloperIcon style={{ fontSize: "18px", color: "#A78BFA" }} />
                    <strong style={{ fontSize: "13px", color: "#DDD6FE" }}>
                      Vendor Store Details (Onboarding Review)
                    </strong>
                  </Box>

                  <div>
                    <FieldLabel>Store / Brand Name *</FieldLabel>
                    <InputField
                      type="text"
                      placeholder="e.g. HyperScale Distributed Systems"
                      value={storeName}
                      onChange={(e) => setStoreName(e.target.value)}
                      required={role === "VENDOR"}
                    />
                  </div>

                  <div>
                    <FieldLabel>Store Description / Bio</FieldLabel>
                    <InputField
                      type="text"
                      placeholder="e.g. Enterprise microservice blueprints, UI components & hardware tools"
                      value={storeDescription}
                      onChange={(e) => setStoreDescription(e.target.value)}
                    />
                  </div>

                  <div>
                    <FieldLabel>Business / Tax ID (Optional)</FieldLabel>
                    <InputField
                      type="text"
                      placeholder="e.g. EU-VAT-992144 or US-EIN-123456"
                      value={businessTaxId}
                      onChange={(e) => setBusinessTaxId(e.target.value)}
                    />
                  </div>

                  <div>
                    <FieldLabel>Support Email (Optional)</FieldLabel>
                    <InputField
                      type="email"
                      placeholder="support@yourstore.io (defaults to registration email)"
                      value={supportEmail}
                      onChange={(e) => setSupportEmail(e.target.value)}
                    />
                  </div>

                  <Box 
                    p={3} 
                    borderRadius="8px" 
                    backgroundColor="rgba(245, 158, 11, 0.12)" 
                    border="1px solid rgba(245, 158, 11, 0.3)"
                    fontSize="12px"
                    color="#FDE68A"
                    lineHeight={1.5}
                  >
                    <strong>Approval Policy:</strong> All vendor registrations enter <strong>PENDING_APPROVAL</strong> status. A platform administrator reviews each vendor application before seller workspace access and product publishing are activated.
                  </Box>
                </Box>
              )}

              <Box mt={2}>
                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  fullWidth
                  loading={loading}
                  rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                >
                  {role === "VENDOR" ? "Submit Vendor Application" : "Create Customer Account"}
                </Button>
              </Box>

            </Box>
          </form>

          <Box mt={5} pt={4} borderTop="1px solid rgba(255, 255, 255, 0.1)" textAlign="center" fontSize="13px" color="rgba(255, 255, 255, 0.65)">
            Already have a ByteVault account?{" "}
            <Link to="/login" style={{ color: "#A78BFA", fontWeight: 600, textDecoration: "none" }}>
              Sign In
            </Link>
          </Box>
        </Card>
      </FormContainer>
    </AuthWrapper>
  );
};

export default Register;
