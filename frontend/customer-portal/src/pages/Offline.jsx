import React from "react";
import { styled, useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import WifiOffIcon from "@mui/icons-material/WifiOff";

import { Container } from "../components/primitives/Container";
import { Button } from "../components/primitives/Button";
import { Card } from "../components/primitives/Card";
import { Chip } from "../components/primitives/Chip";

const PageContainer = styled(Container)(() => ({
  paddingTop: "80px",
  paddingBottom: "120px",
  maxWidth: "540px",
  textAlign: "center",
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  minHeight: "calc(100vh - 72px - 280px)",
}));

export const Offline = () => {
  const theme = useTheme();

  const handleRetry = () => {
    if (navigator.onLine) {
      window.location.reload();
    } else {
      window.dispatchEvent(
        new CustomEvent("bytevault_toast", {
          detail: { message: "Still offline. Verifying connection...", type: "error" }
        })
      );
    }
  };

  return (
    <PageContainer>
      <Card padding={8} elevation="card" radius="xl">
        <Box mb={2}>
          <WifiOffIcon style={{ fontSize: "56px", color: theme.palette.error.main }} />
        </Box>
        <Box mb={2} display="inline-flex">
          <Chip label="OFFLINE DETECTED" color="error" size="xs" uppercase />
        </Box>
        <h2 style={{ fontSize: "28px", fontWeight: 800, margin: "0 0 12px 0", color: theme.palette.text.primary }}>
          You Are Currently Offline
        </h2>
        <p style={{ fontSize: "14px", color: theme.palette.text.secondary, margin: "0 0 32px 0", lineHeight: 1.6 }}>
          We detected that your network connection has dropped. Cached assets remain available, but live vault releases require an active link.
        </p>
        <Button variant="primary" size="lg" onClick={handleRetry} fullWidth>
          Retry Connection
        </Button>
      </Card>
    </PageContainer>
  );
};

export default Offline;
