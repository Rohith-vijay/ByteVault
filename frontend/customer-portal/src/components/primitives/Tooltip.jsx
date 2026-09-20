import React from "react";
import PropTypes from "prop-types";
import MuiTooltip from "@mui/material/Tooltip";
import { styled } from "@mui/material/styles";

const StyledMuiTooltip = styled(({ className, ...props }) => (
  <MuiTooltip {...props} classes={{ popper: className }} />
))(({ theme }) => ({
  "& .MuiTooltip-tooltip": {
    backgroundColor: theme.palette.text.primary,
    color: theme.palette.background.paper,
    borderRadius: theme.radius.sm,
    padding: `${theme.spacing(1.5)} ${theme.spacing(3)}`,
    fontSize: "12px",
    fontFamily: theme.typography.fontFamily,
    boxShadow: theme.elevation.popover,
    letterSpacing: "0.01em",
  },
  "& .MuiTooltip-arrow": {
    color: theme.palette.text.primary,
  },
}));

export const Tooltip = ({ children, ...props }) => {
  return <StyledMuiTooltip {...props}>{children}</StyledMuiTooltip>;
};

Tooltip.propTypes = {
  children: PropTypes.node.isRequired,
  title: PropTypes.node.isRequired,
  placement: PropTypes.oneOf([
    "bottom-end",
    "bottom-start",
    "bottom",
    "left-end",
    "left-start",
    "left",
    "right-end",
    "right-start",
    "right",
    "top-end",
    "top-start",
    "top",
  ]),
  arrow: PropTypes.bool,
};
