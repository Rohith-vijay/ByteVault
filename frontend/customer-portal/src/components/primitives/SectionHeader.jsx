import React from "react";
import PropTypes from "prop-types";
import { styled } from "@mui/material/styles";

const HeaderWrapper = styled("div", {
  shouldForwardProp: (prop) => prop !== "headerAlign" && prop !== "lightMode",
})(({ theme, headerAlign }) => ({
  display: "flex",
  justifyContent: headerAlign === "center" ? "center" : "space-between",
  alignItems: headerAlign === "center" ? "center" : "flex-end",
  textAlign: headerAlign === "center" ? "center" : "left",
  width: "100%",
  marginBottom: theme.spacing(8),
  gap: theme.spacing(6),

  [theme.breakpoints.down("sm")]: {
    flexDirection: "column",
    alignItems: headerAlign === "center" ? "center" : "flex-start",
    marginBottom: theme.spacing(6),
  },
}));

const TextGroup = styled("div", {
  shouldForwardProp: (prop) => prop !== "headerAlign",
})(({ headerAlign }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: headerAlign === "center" ? "center" : "flex-start",
}));

const LabelText = styled("span", {
  shouldForwardProp: (prop) => prop !== "lightMode",
})(({ theme, lightMode }) => ({
  ...theme.typography.label,
  color: lightMode ? theme.palette.accent.light : theme.palette.primary.main,
  marginBottom: theme.spacing(2),
  display: "inline-flex",
  alignItems: "center",
  gap: "6px",
}));

const Heading = styled("h2", {
  shouldForwardProp: (prop) => prop !== "lightMode",
})(({ theme, lightMode }) => ({
  ...theme.typography.h2,
  color: lightMode ? "#FFFFFF" : theme.palette.text.primary,
  margin: 0,
  fontWeight: theme.typography.weight.bold,
  letterSpacing: "-0.015em",
}));

const Subheading = styled("p", {
  shouldForwardProp: (prop) => prop !== "lightMode",
})(({ theme, lightMode }) => ({
  ...theme.typography.body1,
  color: lightMode ? "rgba(255, 255, 255, 0.7)" : theme.palette.text.secondary,
  margin: `${theme.spacing(2)} 0 0 0`,
  maxWidth: "640px",
  lineHeight: 1.6,
}));

const ActionGroup = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  flexShrink: 0,

  [theme.breakpoints.down("sm")]: {
    width: "100%",
    justifyContent: "flex-start",
  },
}));

export const SectionHeader = ({
  title,
  subtitle,
  label,
  action,
  align = "left",
  light = false,
  className,
  ...props
}) => {
  return (
    <HeaderWrapper headerAlign={align} lightMode={light} className={className} {...props}>
      <TextGroup headerAlign={align}>
        {label && <LabelText lightMode={light}>{label}</LabelText>}
        <Heading lightMode={light}>{title}</Heading>
        {subtitle && <Subheading lightMode={light}>{subtitle}</Subheading>}
      </TextGroup>

      {action && <ActionGroup>{action}</ActionGroup>}
    </HeaderWrapper>
  );
};

SectionHeader.propTypes = {
  title: PropTypes.string.isRequired,
  subtitle: PropTypes.string,
  label: PropTypes.string,
  action: PropTypes.node,
  align: PropTypes.oneOf(["left", "center"]),
  light: PropTypes.bool,
  className: PropTypes.string,
};

export default SectionHeader;
