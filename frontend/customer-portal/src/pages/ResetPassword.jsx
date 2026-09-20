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

export const ResetPassword = () => {
  const navigate = useNavigate();

  const [token, setToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [formError, setFormError] = useState(null);

  const handleResetSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setFormError(null);

    try {
      await authService.resetPassword(token, newPassword);
      setSuccess(true);
      setTimeout(() => navigate("/login"), 2500);
    } catch (err) {
      setFormError(err.message || "Failed to update password.");
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
          <Box textAlign="center" mb={4}>
            <Box mb={2} display="inline-flex">
              <Chip label="AUTHORIZE NEW KEY" color="primary" variant="filled" uppercase />
            </Box>
            <h2 style={{ fontSize: "24px", fontWeight: 800, margin: "0 0 8px 0", color: "#FFFFFF" }}>
              Set New Password
            </h2>
            <p style={{ margin: 0, fontSize: "14px", color: "rgba(255, 255, 255, 0.65)" }}>
              Enter your authorization token and new access key.
            </p>
          </Box>

          {success ? (
            <Box textAlign="center" p={4} backgroundColor="rgba(16, 185, 129, 0.15)" border="1px solid #10B981" borderRadius="12px">
              <CheckCircleIcon style={{ color: "#10B981", fontSize: "36px", marginBottom: "8px" }} />
              <h4 style={{ margin: "0 0 8px 0", color: "#FFFFFF" }}>Password Updated</h4>
              <p style={{ fontSize: "13px", color: "rgba(255, 255, 255, 0.8)" }}>
                Redirecting to secure login...
              </p>
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
                      Reset Token
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. 883921"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
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
                    <label style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "rgba(255,255,255,0.6)", marginBottom: "6px", display: "block" }}>
                      New Password
                    </label>
                    <input
                      type="password"
                      placeholder="At least 6 characters"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
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
                    disabled={loading || !token || !newPassword}
                    fullWidth
                    rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
                  >
                    Confirm New Password
                  </Button>
                </Box>
              </form>
            </>
          )}

          <Box mt={6} textAlign="center" fontSize="13px" color="rgba(255, 255, 255, 0.6)">
            Back to{" "}
            <Link to="/login" style={{ color: "#A78BFA", fontWeight: 700, textDecoration: "none" }}>
              Sign In
            </Link>
          </Box>
        </Card>
      </FormContainer>
    </AuthWrapper>
  );
};

export default ResetPassword;
