import React, { useState } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import { styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import {
  ErrorOutlineOutlined as ErrorOutlineIcon,
  ArrowForward as ArrowForwardIcon
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
  maxWidth: "440px",
  width: "100%",
  zIndex: 2,
});

export const Login = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const { login } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [formError, setFormError] = useState(null);

  const origin = location.state?.from?.pathname || "/";

  const handleAutofill = (roleType) => {
    if (roleType === "VENDOR") {
      setEmail("vendor@bytevault.com");
      setPassword("vendor123");
    } else if (roleType === "ADMIN") {
      setEmail("admin@bytevault.com");
      setPassword("admin123");
    } else {
      setEmail("customer@bytevault.com");
      setPassword("password123");
    }
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setFormError(null);

    try {
      const loggedUser = await login(email, password);
      let targetPath = origin;
      if (!location.state?.from?.pathname || location.state?.from?.pathname === "/") {
        if (loggedUser?.role === "VENDOR") {
          targetPath = "/vendor";
        } else if (loggedUser?.role === "ADMIN") {
          targetPath = "/admin";
        } else {
          targetPath = "/account";
        }
      }
      navigate(targetPath, { replace: true });
    } catch (err) {
      setFormError(err.message || "Authentication failed. Please verify credentials.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthWrapper className="bv-tech-grid bv-glow-navy">
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
          <Box textAlign="center" mb={4}>
            <Box mb={2} display="inline-flex">
              <Chip label="SECURE ACCESS" color="primary" variant="filled" uppercase />
            </Box>
            <h2 style={{ fontSize: "26px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
              Sign In to ByteVault
            </h2>
            <p style={{ margin: 0, fontSize: "14px", color: "rgba(255, 255, 255, 0.65)" }}>
              Access your digital vault licenses, vendor studio, or admin console.
            </p>
          </Box>

          {/* Test Credentials Quick Selectors */}
          <Box 
            p={4} 
            mb={6} 
            backgroundColor="rgba(124, 58, 237, 0.08)" 
            border="1px solid rgba(124, 58, 237, 0.25)" 
            borderRadius="12px"
          >
            <div style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "#DDD6FE", marginBottom: "8px" }}>
              1-Click Demo Accounts (Mock Credentials)
            </div>
            <Box display="grid" gridTemplateColumns="repeat(3, 1fr)" gap={2}>
              <button
                type="button"
                onClick={() => handleAutofill("CUSTOMER")}
                style={{
                  padding: "8px 4px",
                  borderRadius: "8px",
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  backgroundColor: "rgba(255, 255, 255, 0.06)",
                  color: "#FFFFFF",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                  textAlign: "center",
                  transition: "all 0.15s ease",
                }}
              >
                👤 Customer
              </button>

              <button
                type="button"
                onClick={() => handleAutofill("VENDOR")}
                style={{
                  padding: "8px 4px",
                  borderRadius: "8px",
                  border: "1px solid rgba(167, 139, 250, 0.3)",
                  backgroundColor: "rgba(124, 58, 237, 0.2)",
                  color: "#DDD6FE",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                  textAlign: "center",
                  transition: "all 0.15s ease",
                }}
              >
                🏪 Vendor
              </button>

              <button
                type="button"
                onClick={() => handleAutofill("ADMIN")}
                style={{
                  padding: "8px 4px",
                  borderRadius: "8px",
                  border: "1px solid rgba(59, 130, 246, 0.3)",
                  backgroundColor: "rgba(37, 99, 235, 0.2)",
                  color: "#93C5FD",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                  textAlign: "center",
                  transition: "all 0.15s ease",
                }}
              >
                ⚡ Admin
              </button>
            </Box>
          </Box>

          {formError && (
            <Box p={4} mb={4} backgroundColor="rgba(239, 68, 68, 0.15)" border="1px solid #EF4444" borderRadius="10px" color="#FCA5A5" fontSize="13px" display="flex" alignItems="center" gap={1.5}>
              <ErrorOutlineIcon style={{ fontSize: "18px" }} />
              <span>{formError}</span>
            </Box>
          )}

          <form onSubmit={handleLoginSubmit}>
            <Box display="flex" flexDirection="column" gap={4}>
              <div>
                <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "rgba(255,255,255,0.6)", marginBottom: "6px", display: "block" }}>
                  Email Address
                </label>
                <input
                  type="email"
                  placeholder="customer@bytevault.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  style={{
                    width: "100%",
                    padding: "12px 14px",
                    borderRadius: "10px",
                    border: "1px solid rgba(255, 255, 255, 0.15)",
                    backgroundColor: "rgba(255, 255, 255, 0.05)",
                    color: "#FFFFFF",
                    fontSize: "14px",
                    outline: "none",
                  }}
                />
              </div>

              <div>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.75}>
                  <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "rgba(255,255,255,0.6)", display: "block" }}>
                    Password
                  </label>
                  <Link to="/forgot-password" style={{ fontSize: "12px", color: "#A78BFA", textDecoration: "none" }}>
                    Forgot Password?
                  </Link>
                </Box>
                <input
                  type="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  style={{
                    width: "100%",
                    padding: "12px 14px",
                    borderRadius: "10px",
                    border: "1px solid rgba(255, 255, 255, 0.15)",
                    backgroundColor: "rgba(255, 255, 255, 0.05)",
                    color: "#FFFFFF",
                    fontSize: "14px",
                    outline: "none",
                  }}
                />
              </div>

              <Button
                variant="primary"
                size="lg"
                type="submit"
                state={loading ? "loading" : "default"}
                disabled={loading || !email || !password}
                fullWidth
                rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              >
                Sign In to Platform
              </Button>
            </Box>
          </form>

          <Box mt={6} textAlign="center" fontSize="13px" color="rgba(255, 255, 255, 0.6)">
            Don't have an account yet?{" "}
            <Link to="/register" style={{ color: "#A78BFA", fontWeight: 700, textDecoration: "none" }}>
              Create Account
            </Link>
          </Box>
        </Card>
      </FormContainer>
    </AuthWrapper>
  );
};

export default Login;
