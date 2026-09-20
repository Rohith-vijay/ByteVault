import React from "react";
import PropTypes from "prop-types";
import { ErrorState } from "./primitives/ErrorState";
import { Container } from "./primitives/Container";
import logger from "../services/loggerService";

export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    logger.error("React Component Tree Render Crash", error, {
      componentStack: errorInfo?.componentStack
    });
  }

  render() {
    if (this.state.hasError) {
      return (
        <Container style={{ paddingTop: "120px", paddingBottom: "120px" }}>
          <ErrorState
            title="Something Went Wrong"
            message={this.state.error?.message || "We encountered an unexpected rendering error. Please reload or reset session storage."}
            onRetry={() => {
              localStorage.removeItem("bytevault_users");
              localStorage.removeItem("bytevault_auth_token");
              window.location.href = "/login";
            }}
            retryText="Reset Session & Go to Login"
          />
        </Container>
      );
    }

    return this.props.children;
  }
}

ErrorBoundary.propTypes = {
  children: PropTypes.node.isRequired,
};

export default ErrorBoundary;
