import React from "react";
import { useNavigate } from "react-router-dom";
import { styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import { ArrowForward as ArrowForwardIcon } from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { SectionHeader } from "../components/primitives/SectionHeader";
import { Button } from "../components/primitives/Button";
import { EmptyState } from "../components/primitives/EmptyState";
import { ProductCard } from "../features/products/components/ProductCard/ProductCard";
import { useWishlist } from "../store/WishlistContext";
import { useCart } from "../store/CartContext";

const WishlistGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(4, 1fr)",
  gap: theme.spacing(6),
  [theme.breakpoints.down("xl")]: {
    gridTemplateColumns: "repeat(3, 1fr)",
  },
  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "repeat(2, 1fr)",
  },
  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
  },
}));

export const WishlistPage = () => {
  const navigate = useNavigate();
  const { wishlistItems, toggleWishlist, isWishlisted } = useWishlist();
  const { addItem } = useCart();

  return (
    <Box style={{ paddingTop: "32px", paddingBottom: "96px", backgroundColor: "#F8FAFC" }}>
      <Container maxWidth="xxl">
        <SectionHeader
          label="SAVED BLUEPRINTS & GEAR"
          title="Saved Wishlist Items"
          subtitle={`You have ${wishlistItems.length} engineering assets pinned for future deployment.`}
          action={
            wishlistItems.length > 0 ? (
              <Button variant="secondary" onClick={() => navigate("/catalog")}>
                Explore More Assets
              </Button>
            ) : null
          }
        />

        {wishlistItems.length === 0 ? (
          <EmptyState
            title="Your Wishlist is Empty"
            description="Bookmark software architectures and desk peripherals as you explore the catalog."
            action={
              <Button 
                variant="primary" 
                onClick={() => navigate("/catalog")}
                rightIcon={<ArrowForwardIcon style={{ fontSize: "16px" }} />}
              >
                Browse Marketplace
              </Button>
            }
          />
        ) : (
          <WishlistGrid>
            {wishlistItems.map((prod) => (
              <ProductCard
                key={prod.id}
                product={prod}
                onAddToCart={addItem}
                onWishlistToggle={toggleWishlist}
                isWishlisted={isWishlisted(prod.id)}
              />
            ))}
          </WishlistGrid>
        )}
      </Container>
    </Box>
  );
};

export default WishlistPage;
