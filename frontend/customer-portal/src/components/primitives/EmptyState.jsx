import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { motion } from "framer-motion";
import { Button } from "./Button";
import { fadeInVariants } from "../../animations/motion";

const CenteredContainer = styled("div")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  textAlign: "center",
  padding: `${theme.spacing(12)} ${theme.spacing(6)}`,
  maxWidth: "460px",
  margin: "0 auto",
}));

const IconOuterWrapper = styled("div")(({ theme }) => ({
  width: "80px",
  height: "80px",
  borderRadius: theme.radius.full,
  backgroundColor: theme.palette.background.elevated,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  marginBottom: theme.spacing(6),
  color: theme.palette.text.secondary,

  "& svg": {
    width: "40px",
    height: "40px",
  },
}));

const Title = styled("h3")(({ theme }) => ({
  ...theme.typography.h3,
  color: theme.palette.text.primary,
  margin: `0 0 ${theme.spacing(3)} 0`,
}));

const Description = styled("p")(({ theme }) => ({
  ...theme.typography.body2,
  color: theme.palette.text.secondary,
  margin: `0 0 ${theme.spacing(8)} 0`,
  lineHeight: 1.6,
}));

const MotionContainer = motion(CenteredContainer);

export const EmptyState = ({
  icon,
  title,
  description,
  actionText,
  onActionClick,
  className,
  ...props
}) => {
  return (
    <MotionContainer
      className={className}
      variants={fadeInVariants}
      initial="initial"
      animate="animate"
      exit="exit"
      {...props}
    >
      <IconOuterWrapper>
        {icon}
      </IconOuterWrapper>

      <Title>{title}</Title>
      <Description>{description}</Description>

      {actionText && onActionClick && (
        <Button variant="primary" onClick={onActionClick}>
          {actionText}
        </Button>
      )}
    </MotionContainer>
  );
};

EmptyState.propTypes = {
  icon: PropTypes.node.isRequired,
  title: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  actionText: PropTypes.string,
  onActionClick: PropTypes.func,
  className: PropTypes.string,
};
