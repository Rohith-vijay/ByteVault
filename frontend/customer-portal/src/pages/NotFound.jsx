import React from "react";
import { useNavigate } from "react-router-dom";
import { styled, useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import SearchOffIcon from "@mui/icons-material/SearchOff";

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

export const NotFound = () => {
  const theme = useTheme();
  const navigate = useNavigate();

  return (
    <PageContainer>
      <Card padding={8} elevation="card" radius="xl">
        <Box mb={2}>
          <SearchOffIcon style={{ fontSize: "56px", color: theme.palette.primary.main }} />
        </Box>
        <Box mb={2} display="inline-flex">
          <Chip label="ERROR 404" color="primary" size="xs" uppercase />
        </Box>
        <h2 style={{ fontSize: "28px", fontWeight: 800, margin: "0 0 12px 0", color: theme.palette.text.primary }}>
          Page Coordinates Not Found
        </h2>
        <p style={{ fontSize: "14px", color: theme.palette.text.secondary, margin: "0 0 32px 0", lineHeight: 1.6 }}>
          The path you attempted to access does not exist or has been relocated within the ByteVault network.
        </p>
        <Box display="flex" flexDirection="column" gap={3}>
          <Button variant="primary" size="lg" onClick={() => navigate("/")} fullWidth>
            Return to Homepage
          </Button>
          <Button variant="secondary" size="md" onClick={() => navigate("/catalog")} fullWidth>
            Browse Marketplace
          </Button>
        </Box>
      </Card>
    </PageContainer>
  );
};

export default NotFound;
