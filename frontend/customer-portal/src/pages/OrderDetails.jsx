import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import FolderSpecialIcon from "@mui/icons-material/FolderSpecialOutlined";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Chip } from "../components/primitives/Chip";
import { Price } from "../components/primitives/Price";
import { Skeleton } from "../components/primitives/Skeleton";
import { orderService } from "../services/orderService";

export const OrderDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchOrder = async () => {
      try {
        const found = await orderService.getOrderById(id);
        setOrder(found);
      } catch (err) {
        console.error("Order details fetch failed", err);
      } finally {
        setLoading(false);
      }
    };
    fetchOrder();
  }, [id]);

  if (loading) {
    return (
      <Container maxWidth="lg" style={{ paddingTop: "48px", paddingBottom: "80px" }}>
        <Skeleton variant="rectangular" height={300} radius="xl" />
      </Container>
    );
  }

  if (!order) {
    return (
      <Container maxWidth="md" style={{ paddingTop: "80px", paddingBottom: "80px", textAlign: "center" }}>
        <h2>Order #{id} Not Located</h2>
        <p style={{ color: "#64748B", marginBottom: "20px" }}>
          We could not find matching records for this order reference.
        </p>
        <Button variant="primary" onClick={() => navigate("/account?tab=orders")}>
          Return to Orders
        </Button>
      </Container>
    );
  }

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC" }}>
      <Container maxWidth="lg">
        <Box mb={4}>
          <Button variant="ghost" size="sm" onClick={() => navigate("/account?tab=orders")} leftIcon={<ArrowBackIcon style={{ fontSize: "16px" }} />}>
            Back to Orders
          </Button>
        </Box>

        <Card padding={8} radius="xl" elevation="card">
          <Box display="flex" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={3} mb={4}>
            <div>
              <span style={{ fontSize: "12px", color: "#64748B", fontWeight: 700, textTransform: "uppercase" }}>
                ORDER REFERENCE
              </span>
              <h2 style={{ fontSize: "24px", fontWeight: 800, margin: "4px 0 0 0" }}>#{order.id}</h2>
            </div>

            <Chip label={order.status || "CONFIRMED"} color="primary" variant="filled" uppercase />
          </Box>

          <Divider style={{ margin: "20px 0" }} />

          <h3 style={{ fontSize: "16px", fontWeight: 700, margin: "0 0 16px 0" }}>Included Items</h3>
          <Box display="flex" flexDirection="column" gap={3} mb={6}>
            {order.items?.map((item, idx) => (
              <Box key={idx} display="flex" justifyContent="space-between" alignItems="center" p={4} backgroundColor="#F1F5F9" borderRadius="10px">
                <Box display="flex" alignItems="center" gap={3}>
                  <img src={item.image} alt={item.title} style={{ width: "48px", height: "48px", objectFit: "cover", borderRadius: "8px" }} />
                  <div>
                    <strong style={{ fontSize: "14px" }}>{item.title}</strong>
                    <div style={{ fontSize: "12px", color: "#64748B" }}>Qty: {item.quantity} · {item.type}</div>
                  </div>
                </Box>
                <Price amount={item.price * item.quantity} size="sm" />
              </Box>
            ))}
          </Box>

          <Box display="flex" gap={3} flexWrap="wrap">
            <Button variant="primary" onClick={() => navigate("/account?tab=downloads")} leftIcon={<FolderSpecialIcon />}>
              Open in Digital Vault
            </Button>
            <Button variant="secondary" onClick={() => navigate("/catalog")}>
              Continue Shopping
            </Button>
          </Box>
        </Card>
      </Container>
    </Box>
  );
};

export default OrderDetails;
