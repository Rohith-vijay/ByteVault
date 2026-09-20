import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const PriceContainer = styled("div")({
  display: "inline-flex",
  alignItems: "baseline",
  flexWrap: "wrap",
  gap: "6px",
});

const CurrentPrice = styled("span", {
  shouldForwardProp: (prop) => prop !== "priceSize",
})(({ theme, priceSize }) => {
  let fontStyles = theme.typography.price;

  if (priceSize === "sm") {
    fontStyles = {
      ...theme.typography.price,
      fontSize: "16px",
      [theme.breakpoints.up("sm")]: { fontSize: "18px" },
      [theme.breakpoints.up("md")]: { fontSize: "20px" },
    };
  } else if (priceSize === "lg") {
    fontStyles = {
      ...theme.typography.price,
      fontSize: "24px",
      [theme.breakpoints.up("sm")]: { fontSize: "28px" },
      [theme.breakpoints.up("md")]: { fontSize: "32px" },
    };
  }

  return {
    ...fontStyles,
    color: theme.palette.text.primary,
    fontWeight: theme.typography.weight.bold,
  };
});

const Decimal = styled("span")({
  fontSize: "0.75em",
  fontWeight: "inherit",
});

const OriginalPrice = styled("span", {
  shouldForwardProp: (prop) => prop !== "priceSize",
})(({ theme, priceSize }) => {
  let fontSize = "14px";
  if (priceSize === "sm") fontSize = "12px";
  if (priceSize === "lg") fontSize = "18px";

  return {
    fontSize: fontSize,
    color: theme.palette.text.muted,
    textDecoration: "line-through",
    fontFamily: theme.typography.fontFamily,
    fontWeight: theme.typography.weight.regular,
  };
});

const DiscountBadge = styled("span")(({ theme }) => ({
  fontSize: "11px",
  fontWeight: theme.typography.weight.bold,
  color: theme.palette.accent.main,
  backgroundColor: theme.palette.accent.soft,
  padding: "2px 6px",
  borderRadius: theme.radius.xs,
  textTransform: "uppercase",
  letterSpacing: "0.02em",
}));

export const Price = ({
  amount,
  originalAmount,
  currency = "₹",
  size = "md",
  showDiscountBadge = true,
  className,
  ...props
}) => {
  const parts = amount.toFixed(2).split(".");
  const integerPart = parts[0];
  const decimalPart = parts[1];

  const hasDiscount = originalAmount !== undefined && originalAmount > amount;
  const discountPercent = hasDiscount
    ? Math.round(((originalAmount - amount) / originalAmount) * 100)
    : 0;

  return (
    <PriceContainer className={className} aria-label={`Price: ${currency}${amount}`} {...props}>
      <CurrentPrice priceSize={size}>
        <span>{currency}</span>
        <span>{integerPart}</span>
        <Decimal>.{decimalPart}</Decimal>
      </CurrentPrice>

      {hasDiscount && (
        <>
          <OriginalPrice priceSize={size} aria-label={`Original price: ${currency}${originalAmount}`}>
            {currency}
            {originalAmount.toFixed(2)}
          </OriginalPrice>
          {showDiscountBadge && discountPercent > 0 && (
            <DiscountBadge aria-label={`${discountPercent} percent off`}>
              -{discountPercent}%
            </DiscountBadge>
          )}
        </>
      )}
    </PriceContainer>
  );
};

Price.propTypes = {
  amount: PropTypes.number.isRequired,
  originalAmount: PropTypes.number,
  currency: PropTypes.string,
  size: PropTypes.oneOf(["sm", "md", "lg"]),
  showDiscountBadge: PropTypes.bool,
  className: PropTypes.string,
};
