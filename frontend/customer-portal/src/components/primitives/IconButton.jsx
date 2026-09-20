import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { motion } from "framer-motion";
import Tooltip from "@mui/material/Tooltip";
import { hoverScale, activePress } from "../../animations/motion";

const StyledIconButton = styled("button", {
  shouldForwardProp: (prop) => prop !== "iconSize" && prop !== "iconVariant",
})(({ theme, iconSize, iconVariant }) => {
  let buttonPadding = theme.spacing(2);
  let innerIconSize = 24;

  if (iconSize === "xs") {
    buttonPadding = theme.spacing(1);
    innerIconSize = 16;
  } else if (iconSize === "sm") {
    buttonPadding = theme.spacing(1.5);
    innerIconSize = 20;
  } else if (iconSize === "md") {
    buttonPadding = theme.spacing(2);
    innerIconSize = 24;
  } else if (iconSize === "lg") {
    buttonPadding = theme.spacing(2.5);
    innerIconSize = 32;
  } else if (iconSize === "xl") {
    buttonPadding = theme.spacing(3);
    innerIconSize = 48;
  }

  let backgroundColor = "transparent";
  let border = "1px solid transparent";
  let textColor = theme.palette.text.primary;

  if (iconVariant === "outlined") {
    border = `1px solid ${theme.palette.border.default}`;
  } else if (iconVariant === "filled") {
    backgroundColor = theme.palette.background.elevated;
    textColor = theme.palette.text.primary;
  }

  return {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    padding: buttonPadding,
    borderRadius: theme.radius.full,
    border: border,
    backgroundColor: backgroundColor,
    color: textColor,
    cursor: "pointer",
    userSelect: "none",
    position: "relative",
    boxShadow: iconVariant === "filled" ? theme.elevation.subtle : "none",
    transition: `background-color ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}, 
                 border-color ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut},
                 color ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}`,

    "& svg": {
      width: `${innerIconSize}px`,
      height: `${innerIconSize}px`,
    },

    "&:hover": {
      backgroundColor: iconVariant === "filled" ? theme.palette.border.default : theme.palette.background.elevated,
      color: theme.palette.primary.main,
    },

    "&:active": {
      backgroundColor: theme.palette.border.strong,
    },

    "&:focus-visible": {
      outline: `2px solid ${theme.palette.primary.main}`,
      outlineOffset: "2px",
    },

    "&:disabled": {
      opacity: 0.4,
      cursor: "not-allowed",
      pointerEvents: "none",
    },
  };
});

const MotionIconButtonContainer = motion(StyledIconButton);

export const IconButton = React.forwardRef(
  (
    {
      children,
      "aria-label": ariaLabel,
      size = "md",
      variant = "flat",
      tooltipText,
      disabled = false,
      className,
      ...props
    },
    ref
  ) => {
    const isInteractive = !disabled;

    const buttonElement = (
      <MotionIconButtonContainer
        ref={ref}
        aria-label={ariaLabel}
        iconSize={size}
        iconVariant={variant}
        disabled={disabled}
        className={className}
        whileHover={isInteractive ? hoverScale() : undefined}
        whileTap={isInteractive ? activePress() : undefined}
        {...props}
      >
        {children}
      </MotionIconButtonContainer>
    );

    if (tooltipText && !disabled) {
      return (
        <Tooltip title={tooltipText} placement="top" arrow>
          {buttonElement}
        </Tooltip>
      );
    }

    return buttonElement;
  }
);

IconButton.displayName = "IconButton";

IconButton.propTypes = {
  children: PropTypes.node.isRequired,
  "aria-label": PropTypes.string.isRequired, // Enforce accessibility
  size: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl"]),
  variant: PropTypes.oneOf(["flat", "outlined", "filled"]),
  tooltipText: PropTypes.string,
  disabled: PropTypes.bool,
  className: PropTypes.string,
};
