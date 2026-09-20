import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const BaseSkeleton = styled("div", {
  shouldForwardProp: (prop) =>
    prop !== "skeletonVariant" &&
    prop !== "skeletonWidth" &&
    prop !== "skeletonHeight" &&
    prop !== "skeletonRadius" &&
    prop !== "isAnimated",
})(({ theme, skeletonVariant, skeletonWidth, skeletonHeight, skeletonRadius, isAnimated }) => {
  const width =
    typeof skeletonWidth === "number" ? `${skeletonWidth}px` : skeletonWidth || "100%";

  let height =
    typeof skeletonHeight === "number" ? `${skeletonHeight}px` : skeletonHeight;

  if (skeletonVariant === "text" && !height) {
    height = "1em";
  } else if (!height) {
    height = "100px";
  }

  let borderRadius = theme.radius[skeletonRadius];
  if (skeletonRadius === "none") borderRadius = "0px";

  if (skeletonVariant === "circular") {
    borderRadius = "50%";
  } else if (skeletonVariant === "text") {
    borderRadius = theme.radius.xs;
  }

  return {
    width: width,
    height: height,
    borderRadius: borderRadius,
    backgroundColor: theme.palette.background.elevated,
    position: "relative",
    overflow: "hidden",

    ...(isAnimated && {
      animation: "pulse 1.8s ease-in-out infinite",
      "@keyframes pulse": {
        "0%": { opacity: 0.6 },
        "50%": { opacity: 1 },
        "100%": { opacity: 0.6 },
      },
    }),
  };
});

export const Skeleton = ({
  variant = "rectangular",
  width,
  height,
  radius = "sm",
  animated = true,
  className,
  ...props
}) => {
  return (
    <BaseSkeleton
      skeletonVariant={variant}
      skeletonWidth={width}
      skeletonHeight={height}
      skeletonRadius={radius === "full" ? "full" : radius}
      isAnimated={animated}
      className={className}
      role="progressbar"
      aria-busy="true"
      {...props}
    />
  );
};

Skeleton.propTypes = {
  variant: PropTypes.oneOf(["text", "rectangular", "circular"]),
  width: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  height: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  radius: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl", "none", "full"]),
  animated: PropTypes.bool,
  className: PropTypes.string,
};
