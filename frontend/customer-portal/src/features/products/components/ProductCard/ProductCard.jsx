import React, { useState } from "react";
import PropTypes from "prop-types";
import { styled, useTheme } from "@mui/material/styles";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import FavoriteBorderIcon from "@mui/icons-material/FavoriteBorder";
import FavoriteIcon from "@mui/icons-material/Favorite";
import BoltIcon from "@mui/icons-material/Bolt";
import LocalShippingIcon from "@mui/icons-material/LocalShipping";
import ShoppingBagOutlinedIcon from "@mui/icons-material/ShoppingBagOutlined";
import CheckIcon from "@mui/icons-material/Check";

import { Card } from "../../../../components/primitives/Card";
import { Price } from "../../../../components/primitives/Price";
import { Rating } from "../../../../components/primitives/Rating";
import { Chip } from "../../../../components/primitives/Chip";
import { IconButton } from "../../../../components/primitives/IconButton";
import { Button } from "../../../../components/primitives/Button";

const CardWrapper = styled(Card)(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  height: "100%",
  backgroundColor: theme.palette.background.paper,
  border: `1px solid ${theme.palette.border.default}`,
  borderRadius: theme.radius.lg,
  overflow: "hidden",
  transition: "all 0.25s cubic-bezier(0.16, 1, 0.3, 1)",
  position: "relative",
  cursor: "pointer",

  "&:hover": {
    borderColor: theme.palette.primary.light || "#8B5CF6",
    boxShadow: "0 12px 30px -8px rgba(15, 23, 42, 0.12), 0 4px 12px -2px rgba(124, 58, 237, 0.08)",
    transform: "translateY(-4px)",

    "& .product-img": {
      transform: "scale(1.05)",
    },
  },
}));

const ImageContainer = styled("div")(({ theme }) => ({
  position: "relative",
  width: "100%",
  paddingTop: "65%", // Sleek 16:10 ratio for technical preview
  backgroundColor: theme.palette.background.elevated,
  overflow: "hidden",
}));

const ImageElement = styled("img")({
  position: "absolute",
  top: 0,
  left: 0,
  width: "100%",
  height: "100%",
  objectFit: "cover",
  transition: "transform 0.5s cubic-bezier(0.16, 1, 0.3, 1)",
});

const BadgesRow = styled("div")(({ theme }) => ({
  position: "absolute",
  top: theme.spacing(3),
  left: theme.spacing(3),
  display: "flex",
  gap: theme.spacing(2),
  zIndex: 2,
}));

const WishlistWrap = styled("div")(({ theme }) => ({
  position: "absolute",
  top: theme.spacing(3),
  right: theme.spacing(3),
  zIndex: 2,
}));

const ContentContainer = styled("div")(({ theme }) => ({
  padding: theme.spacing(5),
  display: "flex",
  flexDirection: "column",
  flexGrow: 1,
  justifyContent: "space-between",
  gap: theme.spacing(3),
}));

const TopInfo = styled("div")({
  display: "flex",
  flexDirection: "column",
  gap: "6px",
});

const CategoryLabel = styled("span")(({ theme }) => ({
  ...theme.typography.label,
  color: theme.palette.text.muted,
  fontSize: "11px",
}));

const ProductTitle = styled("h4")(({ theme }) => ({
  fontSize: "15px",
  fontWeight: theme.typography.weight.semibold,
  color: theme.palette.text.primary,
  margin: 0,
  display: "-webkit-box",
  WebkitLineClamp: 2,
  WebkitBoxOrient: "vertical",
  overflow: "hidden",
  lineHeight: 1.35,
  minHeight: "40px",
  letterSpacing: "-0.01em",
}));

const TechMetaPill = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  gap: "6px",
  fontSize: "11px",
  color: theme.palette.text.secondary,
  backgroundColor: theme.palette.background.elevated,
  padding: "3px 8px",
  borderRadius: theme.radius.xs,
  fontFamily: theme.typography.fontFamily,
  alignSelf: "flex-start",
}));

const BottomMeta = styled("div")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(3),
  marginTop: "auto",
  paddingTop: theme.spacing(2),
  borderTop: `1px solid ${theme.palette.border.default}`,
}));

const PriceRow = styled("div")({
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
});

const FulfillmentRow = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  gap: "6px",
  fontSize: "11px",
  color: theme.palette.text.secondary,
  "& svg": {
    fontSize: "14px",
  },
}));

export const ProductCard = ({
  product,
  onAddToCart,
  onWishlistToggle,
  isWishlisted = false,
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const [isFav, setIsFav] = useState(isWishlisted);
  const [cartState, setCartState] = useState("default");

  const isDigital = (product.type || "").toLowerCase() === "digital";

  const handleCardClick = () => {
    navigate(`/products/${product.id}`);
  };

  const handleWishlistClick = (e) => {
    e.stopPropagation();
    const next = !isFav;
    setIsFav(next);
    if (onWishlistToggle) {
      onWishlistToggle(product, next);
    }
  };

  const handleAddToCartClick = (e) => {
    e.stopPropagation();
    if (!onAddToCart || !product.inStock) return;

    setCartState("loading");
    setTimeout(() => {
      onAddToCart(product);
      setCartState("success");
      setTimeout(() => {
        setCartState("default");
      }, 1400);
    }, 450);
  };

  return (
    <CardWrapper onClick={handleCardClick} padding={0} border={false} elevation="none">
      <ImageContainer>
        <ImageElement
          className="product-img"
          src={product.image}
          alt={product.title}
          loading="lazy"
        />

        <BadgesRow>
          {isDigital ? (
            <Chip label="Digital" color="primary" variant="filled" uppercase />
          ) : (
            <Chip label="Gear" color="accent" variant="filled" uppercase />
          )}

          {!product.inStock && (
            <Chip label="Backorder" color="error" variant="filled" uppercase />
          )}
        </BadgesRow>

        <WishlistWrap>
          <IconButton
            aria-label={isFav ? "Remove from wishlist" : "Add to wishlist"}
            variant="filled"
            size="sm"
            onClick={handleWishlistClick}
            style={{
              backgroundColor: "rgba(255, 255, 255, 0.85)",
              backdropFilter: "blur(8px)",
              boxShadow: "0 2px 6px rgba(0,0,0,0.1)",
            }}
          >
            <motion.div
              animate={{ scale: isFav ? [1, 1.3, 1] : 1 }}
              transition={{ duration: 0.25 }}
              style={{ display: "flex" }}
            >
              {isFav ? (
                <FavoriteIcon style={{ color: "#EF4444", fontSize: "16px" }} />
              ) : (
                <FavoriteBorderIcon style={{ fontSize: "16px", color: theme.palette.text.secondary }} />
              )}
            </motion.div>
          </IconButton>
        </WishlistWrap>
      </ImageContainer>

      <ContentContainer>
        <TopInfo>
          <CategoryLabel>{product.category || (isDigital ? "Software Blueprint" : "Workspace Hardware")}</CategoryLabel>
          <ProductTitle title={product.title}>{product.title}</ProductTitle>

          {isDigital && product.specs && (
            <TechMetaPill>
              <span>{product.specs.format || "ZIP"}</span>
              <span>·</span>
              <span>{product.specs.version || "v1.0"}</span>
            </TechMetaPill>
          )}

          <div style={{ marginTop: "4px" }}>
            <Rating value={product.rating || 4.8} count={product.ratingCount || 36} size="xs" />
          </div>
        </TopInfo>

        <BottomMeta>
          <PriceRow>
            <Price amount={product.price} originalAmount={product.originalPrice} size="sm" />
            <FulfillmentRow>
              {isDigital ? (
                <>
                  <BoltIcon style={{ color: "#F59E0B" }} />
                  <span>Instant</span>
                </>
              ) : (
                <>
                  <LocalShippingIcon style={{ color: theme.palette.accent.main }} />
                  <span>1-2 Days</span>
                </>
              )}
            </FulfillmentRow>
          </PriceRow>

          <Button
            variant={cartState === "success" ? "primary" : "secondary"}
            size="sm"
            fullWidth
            state={cartState}
            disabled={!product.inStock}
            onClick={handleAddToCartClick}
            leftIcon={
              cartState === "success" ? (
                <CheckIcon style={{ fontSize: "15px" }} />
              ) : (
                <ShoppingBagOutlinedIcon style={{ fontSize: "15px" }} />
              )
            }
          >
            {cartState === "success" ? "Added to Cart" : !product.inStock ? "Out of Stock" : "Add to Cart"}
          </Button>
        </BottomMeta>
      </ContentContainer>
    </CardWrapper>
  );
};

ProductCard.propTypes = {
  product: PropTypes.shape({
    id: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    type: PropTypes.oneOf(["digital", "physical", "DIGITAL", "PHYSICAL"]).isRequired,
    image: PropTypes.string.isRequired,
    price: PropTypes.number.isRequired,
    originalPrice: PropTypes.number,
    rating: PropTypes.number,
    ratingCount: PropTypes.number,
    inStock: PropTypes.bool.isRequired,
    deliveryInfo: PropTypes.string,
    category: PropTypes.string,
    specs: PropTypes.object,
  }).isRequired,
  onAddToCart: PropTypes.func,
  onWishlistToggle: PropTypes.func,
  isWishlisted: PropTypes.bool,
};

export default ProductCard;
