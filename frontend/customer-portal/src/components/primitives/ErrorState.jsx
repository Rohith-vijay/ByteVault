import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";
import { ErrorOutlineOutlined as ErrorOutlineIcon } from "@mui/icons-material";
import { Button } from "./Button";

const FlexContainer = styled("div")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  textAlign: "center",
  padding: `${theme.spacing(12)} ${theme.spacing(6)}`,
  maxWidth: "460px",
  margin: "0 auto",
}));

const ErrorIconWrapper = styled("div")(({ theme }) => ({
  color: theme.palette.error.main,
  marginBottom: theme.spacing(6),

  "& svg": {
    width: "48px",
    height: "48px",
  },
}));

const ErrorTitle = styled("h3")(({ theme }) => ({
  ...theme.typography.h3,
  color: theme.palette.text.primary,
  margin: `0 0 ${theme.spacing(3)} 0`,
}));

const ErrorMessage = styled("p")(({ theme }) => ({
  ...theme.typography.body2,
  color: theme.palette.text.secondary,
  margin: `0 0 ${theme.spacing(8)} 0`,
  lineHeight: 1.6,
}));

export const ErrorState = ({
  title = "Something went wrong",
  message = "An error occurred while loading this section. Please try again or refresh the page.",
  onRetry,
  retryText = "Try Again",
  className,
  ...props
}) => {
  return (
    <FlexContainer className={className} role="alert" aria-live="assertive" {...props}>
      <ErrorIconWrapper>
        <ErrorOutlineIcon aria-hidden="true" />
      </ErrorIconWrapper>

      <ErrorTitle>{title}</ErrorTitle>
      <ErrorMessage>{message}</ErrorMessage>

      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          {retryText}
        </Button>
      )}
    </FlexContainer>
  );
};

ErrorState.propTypes = {
  title: PropTypes.string,
  message: PropTypes.string,
  onRetry: PropTypes.func,
  retryText: PropTypes.string,
  className: PropTypes.string,
};
