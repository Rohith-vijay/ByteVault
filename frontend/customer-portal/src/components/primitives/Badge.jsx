import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const BadgeContainer = styled("span")({
  position: "relative",
  display: "inline-flex",
  verticalAlign: "middle",
  flexShrink: 0,
});

const BadgeElement = styled("span", {
  shouldForwardProp: (prop) => prop !== "badgeColor" && prop !== "invisible",
})(({ theme, badgeColor, invisible }) => {
  let backgroundColor = theme.palette.primary.main;
  const textColor = theme.palette.background.paper;

  if (badgeColor === "accent") {
    backgroundColor = theme.palette.accent.main;
  } else if (badgeColor === "success") {
    backgroundColor = theme.palette.success.main;
  } else if (badgeColor === "error") {
    backgroundColor = theme.palette.error.main;
  }

  return {
    display: "flex",
    flexWrap: "wrap",
    alignContent: "center",
    alignItems: "center",
    justifyContent: "center",
    position: "absolute",
    top: 0,
    right: 0,
    boxSizing: "border-box",
    transform: "translate(50%, -50%)",
    transformOrigin: "100% 0%",
    height: "20px",
    minWidth: "20px",
    padding: "0 6px",
    borderRadius: "10px",
    backgroundColor: backgroundColor,
    color: textColor,
    fontFamily: theme.typography.fontFamily,
    fontWeight: theme.typography.weight.bold,
    fontSize: "10px",
    lineHeight: 1,
    zIndex: 1,
    border: `1px solid ${theme.palette.background.paper}`,
    opacity: invisible ? 0 : 1,
    scale: invisible ? 0 : 1,
    transition: `transform ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}, 
                 opacity ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}, 
                 scale ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}`,
    pointerEvents: "none",
  };
});

export const Badge = ({
  children,
  content,
  max = 99,
  color = "primary",
  invisible = false,
  className,
  ...props
}) => {
  const displayValue =
    typeof content === "number" && content > max ? `${max}+` : content;

  const showBadge = content !== undefined && content !== null && content !== "";

  return (
    <BadgeContainer className={className}>
      {children}
      {showBadge && (
        <BadgeElement
          badgeColor={color}
          invisible={invisible}
          role="status"
          aria-live="polite"
          {...props}
        >
          {displayValue}
        </BadgeElement>
      )}
    </BadgeContainer>
  );
};

Badge.propTypes = {
  children: PropTypes.node,
  content: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  max: PropTypes.number,
  color: PropTypes.oneOf(["primary", "accent", "success", "error"]),
  invisible: PropTypes.bool,
  className: PropTypes.string,
};
