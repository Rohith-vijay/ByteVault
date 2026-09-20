import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const StyledChip = styled("span", {
  shouldForwardProp: (prop) =>
    prop !== "chipVariant" && prop !== "chipColor" && prop !== "uppercase",
})(({ theme, chipVariant, chipColor, uppercase }) => {
  let baseColor = theme.palette.text.primary;
  let backgroundColor = theme.palette.background.elevated;
  let borderColor = "transparent";

  if (chipColor === "primary") {
    baseColor = theme.palette.primary.main;
    backgroundColor = theme.palette.primary.soft;
    borderColor = theme.palette.primary.soft;
  } else if (chipColor === "accent") {
    baseColor = theme.palette.accent.main;
    backgroundColor = theme.palette.accent.soft;
    borderColor = theme.palette.accent.soft;
  } else if (chipColor === "success") {
    baseColor = theme.palette.success.main;
    backgroundColor = `${theme.palette.success.main}12`;
    borderColor = "transparent";
  } else if (chipColor === "warning") {
    baseColor = theme.palette.warning.main;
    backgroundColor = `${theme.palette.warning.main}12`;
    borderColor = "transparent";
  } else if (chipColor === "error") {
    baseColor = theme.palette.error.main;
    backgroundColor = `${theme.palette.error.main}12`;
    borderColor = "transparent";
  } else if (chipColor === "info") {
    baseColor = theme.palette.info.main;
    backgroundColor = `${theme.palette.info.main}12`;
    borderColor = "transparent";
  }

  if (chipVariant === "filled") {
    if (chipColor === "primary") {
      backgroundColor = theme.palette.primary.main;
      baseColor = theme.palette.background.paper;
    } else if (chipColor === "accent") {
      backgroundColor = theme.palette.accent.main;
      baseColor = theme.palette.background.paper;
    } else if (chipColor === "success") {
      backgroundColor = theme.palette.success.main;
      baseColor = theme.palette.background.paper;
    } else if (chipColor === "error") {
      backgroundColor = theme.palette.error.main;
      baseColor = theme.palette.background.paper;
    } else {
      backgroundColor = theme.palette.text.primary;
      baseColor = theme.palette.background.paper;
    }
  } else if (chipVariant === "outlined") {
    backgroundColor = "transparent";
    borderColor =
      chipColor === "neutral" ? theme.palette.border.default : baseColor;
  }

  return {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    padding: `${theme.spacing(1)} ${theme.spacing(3)}`, // 4px 12px
    height: "24px",
    borderRadius: theme.radius.full,
    fontSize: "11px",
    fontFamily: theme.typography.fontFamily,
    fontWeight: theme.typography.weight.semibold,
    color: baseColor,
    backgroundColor: backgroundColor,
    border: `1px solid ${borderColor}`,
    whiteSpace: "nowrap",
    letterSpacing: uppercase ? "0.05em" : "0",
    textTransform: uppercase ? "uppercase" : "none",
    userSelect: "none",
    verticalAlign: "middle",
  };
});

export const Chip = ({
  label,
  variant = "soft",
  color = "neutral",
  uppercase = false,
  leftIcon,
  onDelete,
  className,
  ...props
}) => {
  return (
    <StyledChip
      chipVariant={variant}
      chipColor={color}
      uppercase={uppercase}
      className={className}
      {...props}
    >
      {leftIcon && (
        <span style={{ marginRight: "4px", display: "inline-flex", alignItems: "center" }}>
          {leftIcon}
        </span>
      )}
      {label}
      {onDelete && (
        <span
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
          style={{
            marginLeft: "6px",
            cursor: "pointer",
            fontWeight: "bold",
            display: "inline-flex",
            alignItems: "center",
            opacity: 0.6,
            fontSize: "13px",
            lineHeight: 1
          }}
          aria-label="Remove item"
        >
          &times;
        </span>
      )}
    </StyledChip>
  );
};

Chip.propTypes = {
  label: PropTypes.string.isRequired,
  variant: PropTypes.oneOf(["filled", "outlined", "soft"]),
  color: PropTypes.oneOf(["primary", "accent", "success", "warning", "error", "info", "neutral"]),
  uppercase: PropTypes.bool,
  leftIcon: PropTypes.node,
  onDelete: PropTypes.func,
  className: PropTypes.string,
};
