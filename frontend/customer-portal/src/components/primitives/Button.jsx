import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { motion } from "framer-motion";
import { hoverScale, activePress } from "../../animations/motion";
import { tokens } from "../../theme/tokens";

const StyledButton = styled("button", {
  shouldForwardProp: (prop) =>
    prop !== "buttonVariant" && prop !== "buttonState" && prop !== "buttonSize" && prop !== "fullWidth",
})(({ theme, buttonVariant, buttonState, buttonSize, fullWidth }) => {
  // Base default
  let backgroundColor = theme.palette?.primary?.main || tokens.color.primary.main;
  let textColor = "#FFFFFF";
  let borderColor = "transparent";
  let hoverBg = theme.palette?.primary?.dark || tokens.color.primary.hover;
  let hoverBorder = "transparent";
  let hoverText = textColor;
  let activeBg = theme.palette?.primary?.active || tokens.color.primary.active;
  let shadow = "0 1px 2px rgba(0, 0, 0, 0.05)";

  if (buttonVariant === "accent") {
    backgroundColor = theme.palette?.accent?.main || tokens.color.accent.main;
    textColor = "#FFFFFF";
    hoverBg = theme.palette?.accent?.hover || tokens.color.accent.hover;
    activeBg = theme.palette?.accent?.active || tokens.color.accent.active;
  } else if (buttonVariant === "secondary") {
    backgroundColor = theme.palette?.background?.paper || tokens.color.background.surface;
    textColor = theme.palette?.text?.primary || tokens.color.text.primary;
    borderColor = theme.palette?.border?.default || tokens.color.border.default;
    hoverBg = theme.palette?.background?.elevated || tokens.color.background.elevated;
    hoverBorder = theme.palette?.border?.strong || tokens.color.border.strong;
    shadow = theme.elevation?.subtle || tokens.elevation.subtle;
  } else if (buttonVariant === "outline") {
    backgroundColor = "transparent";
    textColor = theme.palette?.text?.primary || tokens.color.text.primary;
    borderColor = theme.palette?.border?.strong || tokens.color.border.strong;
    hoverBg = theme.palette?.background?.elevated || tokens.color.background.elevated;
    hoverBorder = theme.palette?.primary?.main || tokens.color.primary.main;
    hoverText = theme.palette?.primary?.main || tokens.color.primary.main;
    shadow = "none";
  } else if (buttonVariant === "ghost") {
    backgroundColor = "transparent";
    textColor = theme.palette?.text?.secondary || tokens.color.text.secondary;
    borderColor = "transparent";
    hoverBg = theme.palette?.background?.elevated || tokens.color.background.elevated;
    hoverText = theme.palette?.text?.primary || tokens.color.text.primary;
    shadow = "none";
  } else if (buttonVariant === "dark") {
    backgroundColor = theme.palette?.background?.darkNavySurface || "#0F172A";
    textColor = "#FFFFFF";
    borderColor = "rgba(255, 255, 255, 0.12)";
    hoverBg = "#1E293B";
    hoverBorder = "rgba(255, 255, 255, 0.25)";
    shadow = "0 4px 12px rgba(0, 0, 0, 0.2)";
  } else if (buttonVariant === "danger") {
    backgroundColor = theme.palette?.error?.main || tokens.color.status.error;
    textColor = "#FFFFFF";
    hoverBg = "#DC2626";
    activeBg = "#B91C1C";
  }

  // State overrides
  if (buttonState === "success") {
    backgroundColor = theme.palette?.success?.main || tokens.color.status.success;
    textColor = "#FFFFFF";
    borderColor = "transparent";
  } else if (buttonState === "error") {
    backgroundColor = theme.palette?.error?.main || tokens.color.status.error;
    textColor = "#FFFFFF";
    borderColor = "transparent";
  }

  // Sizing
  let padding = theme.spacing ? `${theme.spacing(3)} ${theme.spacing(6)}` : "12px 24px";
  let fontSize = "14px";
  let minHeight = "42px";
  let minWidth = "110px";

  if (buttonSize === "small" || buttonSize === "sm") {
    padding = theme.spacing ? `${theme.spacing(1.5)} ${theme.spacing(3.5)}` : "6px 14px";
    fontSize = "12px";
    minHeight = "32px";
    minWidth = "80px";
  } else if (buttonSize === "large" || buttonSize === "lg") {
    padding = theme.spacing ? `${theme.spacing(4)} ${theme.spacing(8)}` : "16px 32px";
    fontSize = "16px";
    minHeight = "50px";
    minWidth = "140px";
  }

  return {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    width: fullWidth ? "100%" : "auto",
    minWidth: minWidth,
    minHeight: minHeight,
    padding: padding,
    fontSize: fontSize,
    fontWeight: theme.typography?.weight?.semibold || 600,
    fontFamily: theme.typography?.fontFamily || tokens.typography.fontFamily.primary,
    color: textColor,
    backgroundColor: backgroundColor,
    border: `1px solid ${borderColor}`,
    borderRadius: theme.radius?.md || tokens.radius.md,
    cursor: "pointer",
    position: "relative",
    overflow: "hidden",
    userSelect: "none",
    verticalAlign: "middle",
    letterSpacing: "0.01em",
    boxShadow: shadow,
    textDecoration: "none",
    transition: `background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease`,

    "&:hover": {
      backgroundColor: hoverBg,
      borderColor: hoverBorder,
      color: hoverText,
      boxShadow: buttonVariant === "primary" ? (theme.elevation?.hover || tokens.elevation.hover) : (theme.elevation?.subtle || tokens.elevation.subtle),
    },

    "&:active": {
      backgroundColor: activeBg,
      transform: "scale(0.99)",
    },

    "&:focus-visible": {
      outline: `2px solid ${theme.palette?.primary?.main || tokens.color.primary.main}`,
      outlineOffset: "2px",
    },

    "&:disabled": {
      opacity: 0.5,
      cursor: "not-allowed",
      boxShadow: "none",
      backgroundColor: theme.palette?.background?.elevated || tokens.color.background.elevated,
      color: theme.palette?.text?.disabled || tokens.color.text.disabled,
      borderColor: theme.palette?.border?.default || tokens.color.border.default,
      pointerEvents: "none",
    },

    ...(buttonState === "loading" && {
      color: "transparent !important",
      pointerEvents: "none",
      cursor: "default",
      boxShadow: "none",
    }),
  };
});

const SpinnerOverlay = styled("span")({
  position: "absolute",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  top: 0,
  bottom: 0,
  left: 0,
  right: 0,
});

const Spinner = styled("span")(() => ({
  width: "18px",
  height: "18px",
  border: `2px solid currentColor`,
  borderTopColor: "transparent",
  borderRadius: "50%",
  display: "inline-block",
  animation: "spin 0.8s linear infinite",
  "@keyframes spin": {
    "0%": { transform: "rotate(0deg)" },
    "100%": { transform: "rotate(360deg)" },
  },
}));

const MotionButtonContainer = motion.create ? motion.create(StyledButton) : motion(StyledButton);

export const Button = React.forwardRef(
  (
    {
      children,
      variant = "primary",
      size = "md",
      state = "default",
      fullWidth = false,
      leftIcon,
      rightIcon,
      disabled,
      className,
      ...props
    },
    ref
  ) => {
    const isInteractive = state === "default" && !disabled;

    return (
      <MotionButtonContainer
        ref={ref}
        buttonVariant={variant}
        buttonSize={size}
        buttonState={state}
        fullWidth={fullWidth}
        disabled={disabled || state === "loading"}
        className={className}
        whileHover={isInteractive ? hoverScale() : undefined}
        whileTap={isInteractive ? activePress() : undefined}
        aria-busy={state === "loading"}
        aria-live="polite"
        {...props}
      >
        {state === "loading" && (
          <SpinnerOverlay>
            <Spinner />
          </SpinnerOverlay>
        )}

        {leftIcon && (
          <span style={{ marginRight: "8px", display: "inline-flex", alignItems: "center" }}>
            {leftIcon}
          </span>
        )}
        <span>{children}</span>
        {rightIcon && (
          <span style={{ marginLeft: "8px", display: "inline-flex", alignItems: "center" }}>
            {rightIcon}
          </span>
        )}
      </MotionButtonContainer>
    );
  }
);

Button.displayName = "Button";

Button.propTypes = {
  children: PropTypes.node.isRequired,
  variant: PropTypes.oneOf(["primary", "accent", "secondary", "outline", "ghost", "dark", "danger"]),
  size: PropTypes.oneOf(["small", "sm", "medium", "md", "large", "lg"]),
  state: PropTypes.oneOf(["default", "loading", "success", "error"]),
  fullWidth: PropTypes.bool,
  leftIcon: PropTypes.node,
  rightIcon: PropTypes.node,
  disabled: PropTypes.bool,
  className: PropTypes.string,
};

export default Button;
