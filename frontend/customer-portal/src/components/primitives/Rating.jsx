import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import StarIcon from "@mui/icons-material/Star";
import StarBorderIcon from "@mui/icons-material/StarBorder";
import StarHalfIcon from "@mui/icons-material/StarHalf";

const RatingContainer = styled("div")({
  display: "inline-flex",
  alignItems: "center",
  gap: "4px",
});

const StarWrapper = styled("span", {
  shouldForwardProp: (prop) => prop !== "isInteractive" && prop !== "starSize",
})(({ theme, isInteractive, starSize }) => ({
  display: "inline-flex",
  color: theme.palette.warning.main || "#F59E0B",
  cursor: isInteractive ? "pointer" : "inherit",
  transition: `transform ${theme.transitions.duration.shortest}ms ${theme.transitions.easing.easeInOut}`,

  "& svg": {
    width: `${starSize}px`,
    height: `${starSize}px`,
  },

  "&:hover": {
    transform: isInteractive ? "scale(1.15)" : "none",
  },
}));

const CountText = styled("span")(({ theme }) => ({
  fontSize: "12px",
  fontFamily: theme.typography.fontFamily,
  color: theme.palette.text.secondary,
  marginLeft: "4px",
}));

export const Rating = ({
  value,
  count,
  readOnly = true,
  size = "xs",
  onChange,
  className,
  ...props
}) => {
  let starSize = 16;
  if (size === "sm") starSize = 20;
  if (size === "md") starSize = 24;

  const renderStars = () => {
    const stars = [];
    const roundedValue = Math.round(value * 2) / 2;

    for (let i = 1; i <= 5; i++) {
      const starIndex = i;
      const isInteractive = !readOnly && onChange !== undefined;

      let starElement = <StarBorderIcon />;
      if (roundedValue >= starIndex) {
        starElement = <StarIcon />;
      } else if (roundedValue === starIndex - 0.5) {
        starElement = <StarHalfIcon />;
      }

      stars.push(
        <StarWrapper
          key={starIndex}
          isInteractive={isInteractive}
          starSize={starSize}
          onClick={() => isInteractive && onChange(starIndex)}
          role={isInteractive ? "button" : undefined}
          aria-label={isInteractive ? `Rate ${starIndex} stars` : undefined}
          tabIndex={isInteractive ? 0 : -1}
          onKeyDown={(e) => {
            if (isInteractive && (e.key === "Enter" || e.key === " ")) {
              e.preventDefault();
              onChange(starIndex);
            }
          }}
        >
          {starElement}
        </StarWrapper>
      );
    }
    return stars;
  };

  return (
    <RatingContainer
      className={className}
      aria-label={`Rating: ${value} out of 5 stars`}
      {...props}
    >
      {renderStars()}
      {count !== undefined && <CountText>({count})</CountText>}
    </RatingContainer>
  );
};

Rating.propTypes = {
  value: PropTypes.number.isRequired,
  count: PropTypes.number,
  readOnly: PropTypes.bool,
  size: PropTypes.oneOf(["xs", "sm", "md"]),
  onChange: PropTypes.func,
  className: PropTypes.string,
};
