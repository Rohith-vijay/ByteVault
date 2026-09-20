import React from "react";
import PropTypes from "prop-types";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../store/AuthContext";
import { Skeleton } from "../components/primitives/Skeleton";
import { Container } from "../components/primitives/Container";

/**
 * Route protection wrapper requiring authentication.
 * Redirects unauthenticated guests to login, preserving origin location.
 */
export const ProtectedRoute = ({ children }) => {
  const { loading, isAuthenticated } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <Container style={{ paddingTop: "120px", textAlign: "center" }}>
        <Skeleton variant="rectangular" height={40} width="60%" style={{ margin: "0 auto 16px auto" }} />
        <Skeleton variant="rectangular" height={150} width="100%" />
      </Container>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
};

ProtectedRoute.propTypes = {
  children: PropTypes.node.isRequired,
};

/**
 * Route protection wrapper restricted to guest users (e.g. login/register pages).
 * Redirects authenticated users to homepage.
 */
export const PublicRoute = ({ children }) => {
  const { loading, isAuthenticated } = useAuth();

  if (loading) {
    return (
      <Container style={{ paddingTop: "120px", textAlign: "center" }}>
        <Skeleton variant="rectangular" height={40} width="40%" style={{ margin: "0 auto" }} />
      </Container>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
};

PublicRoute.propTypes = {
  children: PropTypes.node.isRequired,
};

/**
 * Route protection wrapper checking user authorization roles.
 * Restricts access to matching customer roles (CUSTOMER, ADMIN, SELLER).
 */
export const RoleRoute = ({ children, allowedRoles = [] }) => {
  const { user, loading, isAuthenticated } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <Container style={{ paddingTop: "120px", textAlign: "center" }}>
        <Skeleton variant="rectangular" height={40} width="60%" style={{ margin: "0 auto 16px auto" }} />
        <Skeleton variant="rectangular" height={150} width="100%" />
      </Container>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles.length > 0 && (!user || !allowedRoles.includes(user.role))) {
    // Authenticated but unauthorized - redirect back to homepage
    return <Navigate to="/" replace />;
  }

  return children;
};

RoleRoute.propTypes = {
  children: PropTypes.node.isRequired,
  allowedRoles: PropTypes.arrayOf(PropTypes.string).isRequired,
};

// Keep RouteGuard as an alias to avoid breaking existing imports
export const RouteGuard = RoleRoute;

export default RouteGuard;
