import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const InputContainer = styled("div")(({ fullWidth }) => ({
  display: "flex",
  flexDirection: "column",
  width: fullWidth ? "100%" : "auto",
  gap: "6px",
}));

const Label = styled("label")(({ theme }) => ({
  fontFamily: theme.typography.label.fontFamily,
  fontWeight: theme.typography.label.fontWeight,
  fontSize: "12px",
  letterSpacing: "0.05em",
  textTransform: "uppercase",
  color: theme.palette.text.secondary,
}));

const StyledInput = styled("input", {
  shouldForwardProp: (prop) => prop !== "state" && prop !== "fullWidth",
})(({ theme, state, fullWidth }) => {
  let borderColor = theme.palette.border.default;
  let focusBorderColor = theme.palette.primary.main;
  let focusShadow = `0 0 0 3px ${theme.palette.primary.soft}`;

  if (state === "error") {
    borderColor = theme.palette.error.main;
    focusBorderColor = theme.palette.error.main;
    focusShadow = `0 0 0 3px ${theme.palette.error.main}1A`;
  } else if (state === "success") {
    borderColor = theme.palette.success.main;
    focusBorderColor = theme.palette.success.main;
    focusShadow = `0 0 0 3px ${theme.palette.success.main}1A`;
  }

  return {
    width: fullWidth ? "100%" : "280px",
    padding: `${theme.spacing(3)} ${theme.spacing(4)}`, // 12px 16px
    fontSize: theme.typography.body2.fontSize,
    fontFamily: theme.typography.fontFamily,
    color: theme.palette.text.primary,
    backgroundColor: theme.palette.background.paper,
    border: `1px solid ${borderColor}`,
    borderRadius: theme.radius.md,
    boxShadow: theme.elevation.none,
    transition: `border-color ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}, 
                 box-shadow ${theme.transitions.duration.shorter}ms ${theme.transitions.easing.easeInOut}`,

    "&::placeholder": {
      color: theme.palette.text.muted,
      opacity: 1,
    },

    "&:hover": {
      borderColor: state === "default" ? theme.palette.border.strong : borderColor,
    },

    "&:focus": {
      outline: "none",
      borderColor: focusBorderColor,
      boxShadow: focusShadow,
    },

    "&:disabled": {
      backgroundColor: theme.palette.background.elevated,
      color: theme.palette.text.disabled,
      borderColor: theme.palette.border.default,
      cursor: "not-allowed",
    },
  };
});

const HelperText = styled("span", {
  shouldForwardProp: (prop) => prop !== "state",
})(({ theme, state }) => ({
  fontSize: "12px",
  fontFamily: theme.typography.fontFamily,
  color:
    state === "error"
      ? theme.palette.error.main
      : state === "success"
      ? theme.palette.success.main
      : theme.palette.text.secondary,
  lineHeight: 1.4,
}));

export const Input = React.forwardRef(
  (
    {
      label,
      helperText,
      inputState = "default",
      fullWidth = false,
      disabled = false,
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = React.useId();
    const inputId = id || generatedId;
    const helperTextId = `${inputId}-helper`;

    return (
      <InputContainer fullWidth={fullWidth}>
        {label && <Label htmlFor={inputId}>{label}</Label>}
        <StyledInput
          ref={ref}
          id={inputId}
          state={inputState}
          fullWidth={fullWidth}
          disabled={disabled}
          aria-describedby={helperText ? helperTextId : undefined}
          aria-invalid={inputState === "error"}
          {...props}
        />
        {helperText && (
          <HelperText id={helperTextId} state={inputState}>
            {helperText}
          </HelperText>
        )}
      </InputContainer>
    );
  }
);

Input.displayName = "Input";

Input.propTypes = {
  label: PropTypes.string,
  helperText: PropTypes.string,
  inputState: PropTypes.oneOf(["default", "error", "success"]),
  fullWidth: PropTypes.bool,
  disabled: PropTypes.bool,
  id: PropTypes.string,
};
