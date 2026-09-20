import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { motion } from "framer-motion";
import { hoverScale, activePress } from "../../animations/motion";
import { tokens } from "../../theme/tokens";

const StyledCard = styled("div", {
  shouldForwardProp: (prop) =>
    prop !== "cardElevation" &&
    prop !== "hasBorder" &&
    prop !== "cardPadding" &&
    prop !== "cardRadius" &&
    prop !== "cardVariant",
})(({ theme, cardElevation, hasBorder, cardPadding, cardRadius, cardVariant }) => {
  const paddingValue =
    typeof cardPadding === "number"
      ? (theme.spacing ? theme.spacing(cardPadding) : `${cardPadding * 4}px`)
      : cardPadding;

  const radiusValue = theme.radius?.[cardRadius] || tokens.radius[cardRadius] || tokens.radius.lg;
  const shadow = theme.elevation?.[cardElevation] || tokens.elevation[cardElevation] || tokens.elevation.none;

  let bg = theme.palette?.background?.paper || tokens.color.background.surface;
  let borderColor = hasBorder ? (theme.palette?.border?.default || tokens.color.border.default) : "transparent";
  let textColor = theme.palette?.text?.primary || tokens.color.text.primary;

  if (cardVariant === "dark") {
    bg = theme.palette?.background?.darkNavySurface || "#0F172A";
    borderColor = hasBorder ? "rgba(255, 255, 255, 0.08)" : "transparent";
    textColor = "#FFFFFF";
  } else if (cardVariant === "elevated") {
    bg = theme.palette?.background?.elevated || tokens.color.background.elevated;
  } else if (cardVariant === "transparent") {
    bg = "transparent";
  }

  return {
    backgroundColor: bg,
    color: textColor,
    borderRadius: radiusValue,
    padding: paddingValue,
    boxShadow: shadow,
    border: `1px solid ${borderColor}`,
    position: "relative",
    overflow: "hidden",
    transition: `box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease`,

    "&:focus-visible": {
      outline: `2px solid ${theme.palette?.primary?.main || tokens.color.primary.main}`,
      outlineOffset: "2px",
    },
  };
});

const MotionCardContainer = motion.create ? motion.create(StyledCard) : motion(StyledCard);

export const Card = React.forwardRef(
  (
    {
      children,
      variant = "default",
      elevation = "subtle",
      interactive = false,
      border = true,
      padding = 4,
      radius = "lg",
      className,
      onClick,
      ...props
    },
    ref
  ) => {
    const isInteractive = interactive && onClick !== undefined;

    return (
      <MotionCardContainer
        ref={ref}
        cardVariant={variant}
        cardElevation={elevation}
        hasBorder={border}
        cardPadding={padding}
        cardRadius={radius}
        onClick={onClick}
        className={className}
        style={{ cursor: isInteractive ? "pointer" : "default" }}
        whileHover={isInteractive ? hoverScale() : undefined}
        whileTap={isInteractive ? activePress() : undefined}
        tabIndex={isInteractive ? 0 : undefined}
        role={isInteractive ? "button" : undefined}
        {...props}
      >
        {children}
      </MotionCardContainer>
    );
  }
);

Card.displayName = "Card";

Card.propTypes = {
  children: PropTypes.node.isRequired,
  variant: PropTypes.oneOf(["default", "dark", "elevated", "transparent"]),
  elevation: PropTypes.oneOf(["none", "subtle", "card", "hover", "popover", "modal", "darkCard", "darkHover"]),
  interactive: PropTypes.bool,
  border: PropTypes.bool,
  padding: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  radius: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl", "xxl", "full"]),
  className: PropTypes.string,
  onClick: PropTypes.func,
};

export default Card;
