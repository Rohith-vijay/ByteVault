import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { tokens } from "../../theme/tokens";

const StyledContainer = styled("div", {
  shouldForwardProp: (prop) => prop !== "maxContainerWidth" && prop !== "hasGutters",
})(({ theme, maxContainerWidth, hasGutters }) => {
  let maxWidthVal = "none";
  if (maxContainerWidth !== "fluid") {
    maxWidthVal = theme?.containerWidth?.[maxContainerWidth] || tokens.containerWidth[maxContainerWidth] || "1200px";
  }

  const paddingX = hasGutters
    ? {
        paddingLeft: theme.spacing ? theme.spacing(4) : "16px",
        paddingRight: theme.spacing ? theme.spacing(4) : "16px",
        [theme.breakpoints?.up("sm") || "@media (min-width:640px)"]: {
          paddingLeft: theme.spacing ? theme.spacing(6) : "24px",
          paddingRight: theme.spacing ? theme.spacing(6) : "24px",
        },
        [theme.breakpoints?.up("md") || "@media (min-width:768px)"]: {
          paddingLeft: theme.spacing ? theme.spacing(8) : "32px",
          paddingRight: theme.spacing ? theme.spacing(8) : "32px",
        },
      }
    : {};

  return {
    width: "100%",
    marginLeft: "auto",
    marginRight: "auto",
    maxWidth: maxWidthVal,
    boxSizing: "border-box",
    ...paddingX,
  };
});

export const Container = ({
  children,
  maxWidth = "lg",
  gutters = true,
  className,
  ...props
}) => {
  return (
    <StyledContainer
      maxContainerWidth={maxWidth}
      hasGutters={gutters}
      className={className}
      {...props}
    >
      {children}
    </StyledContainer>
  );
};

Container.propTypes = {
  children: PropTypes.node.isRequired,
  maxWidth: PropTypes.oneOf(["sm", "md", "lg", "xl", "xxl", "fluid"]),
  gutters: PropTypes.bool,
  className: PropTypes.string,
};

export default Container;
