import React, { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import {
  CheckCircle as CheckCircleIcon,
  ErrorOutlineOutlined as ErrorOutlineIcon,
  ArrowForward as ArrowForwardIcon
} from "@mui/icons-material";

import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { authService } from "../services/authService";

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

export const ForgotPassword = () => {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [formError, setFormError] = useState(null);

  const handleResetSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setFormError(null);

    try {
      await authService.forgotPassword(email);
      setSuccess(true);
    } catch (err) {
      setFormError(err.message || "Failed to dispatch reset instructions.");
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
              <Chip label="SECURITY RECOVERY" color="primary" variant="filled" uppercase />
            </Box>
            <h2 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
              Reset Credentials
            </h2>
            <p style={{ margin: 0, fontSize: "14px", color: "rgba(255, 255, 255, 0.65)" }}>
              Enter your email to receive recovery instructions.
            </p>
          </Box>

          {success ? (
            <Box textAlign="center" p={4} backgroundColor="rgba(16, 185, 129, 0.15)" border="1px solid #10B981" borderRadius="12px">
              <CheckCircleIcon style={{ color: "#10B981", fontSize: "36px", marginBottom: "8px" }} />
              <h4 style={{ margin: "0 0 8px 0", color: "#FFFFFF" }}>Instructions Dispatched</h4>
              <p style={{ fontSize: "13px", color: "rgba(255, 255, 255, 0.8)", marginBottom: "20px" }}>
                If an account exists for <strong>{email}</strong>, a recovery link has been generated.
              </p>
              <Button variant="primary" fullWidth onClick={() => navigate("/reset-password")}>
                Enter Reset Token
              </Button>
            </Box>
          ) : (
            <>
              {formError && (
                <Box p={4} mb={4} backgroundColor="rgba(239, 68, 68, 0.15)" border="1px solid #EF4444" borderRadius="10px" color="#FCA5A5" fontSize="13px" display="flex" alignItems="center" gap={1.5}>
                  <ErrorOutlineIcon style={{ fontSize: "18px" }} />
                  <span>{formError}</span>
                </Box>
              )}

              <form onSubmit={handleResetSubmit}>
                <Box display="flex" flexDirection="column" gap={4}>
                  <div>
                    <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "rgba(255,255,255,0.6)", marginBottom: "6px", display: "block" }}>
                      Registered Email
                    </label>
                    <input
                      type="email"
                      placeholder="alex@enterprise.com"
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

                  <Button
                    variant="primary"
                    size="lg"
                    type="submit"
                    state={loading ? "loading" : "default"}
                    disabled={loading || !email}
                    fullWidth
                    rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                  >
                    Send Recovery Token
                  </Button>
                </Box>
              </form>
            </>
          )}

          <Box mt={6} textAlign="center" fontSize="13px" color="rgba(255, 255, 255, 0.6)">
            Remembered your credentials?{" "}
            <Link to="/login" style={{ color: "#A78BFA", fontWeight: 700, textDecoration: "none" }}>
              Sign In
            </Link>
          </Box>
        </Card>
      </FormContainer>
    </AuthWrapper>
  );
};

export default ForgotPassword;
