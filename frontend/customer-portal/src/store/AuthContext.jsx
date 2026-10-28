import React, { createContext, useContext, useState, useEffect } from "react";
import PropTypes from "prop-types";
import { authService } from "../services/authService";
import apiClient from "../services/apiClient";

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Decodes JWT payload for immediate state restoration
  const parseJwt = (jwtToken) => {
    try {
      if (!jwtToken) return null;
      const base64Url = jwtToken.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch {
      return null;
    }
  };

  // Extracts authoritative user object from backend response and JWT claims
  const extractUser = (res, fallbackToken) => {
    if (!res && !fallbackToken) return null;
    const token = res?.token || fallbackToken || localStorage.getItem("bytevault_auth_token");
    const payload = token ? parseJwt(token) : null;

    let role = res?.role;
    if (!role && payload) {
      if (Array.isArray(payload.roles) && payload.roles.length > 0) {
        role = payload.roles[0].replace(/^ROLE_/, '');
      } else if (payload.role) {
        role = String(payload.role).replace(/^ROLE_/, '');
      }
    }
    role = role || "CUSTOMER";

    const userSrc = (res?.user && typeof res.user === "object") ? res.user : (res || {});

    return {
      id: userSrc.userId || userSrc.id || payload?.sub || payload?.id || payload?.userId,
      userId: userSrc.userId || userSrc.id || payload?.sub || payload?.id || payload?.userId,
      email: userSrc.email || payload?.email || payload?.sub,
      name: userSrc.fullName || userSrc.name || payload?.name || userSrc.email || "User",
      fullName: userSrc.fullName || userSrc.name || payload?.name || userSrc.email || "User",
      role: role,
      storeName: userSrc.storeName || payload?.storeName
    };
  };

  // Logs out the user session and cleans up user artifacts
  const logout = async () => {
    const currentUserId = user?.id;
    try {
      await apiClient.delete("/cart").catch(() => {});
      await authService.logout().catch(() => {});
    } catch {
      // Ignore network errors on sign out
    }
    setUser(null);
    setToken(null);
    localStorage.removeItem("bytevault_auth_token");
    localStorage.removeItem("bytevault_refresh_token");
    localStorage.removeItem("bytevault_user");
    if (currentUserId) {
      localStorage.removeItem(`bytevault_cart_${currentUserId}`);
    }
    localStorage.removeItem("bytevault_cart_guest");
    try {
      Object.keys(localStorage).forEach((key) => {
        if (key.startsWith("bytevault_cart")) {
          localStorage.removeItem(key);
        }
      });
    } catch {}
    window.dispatchEvent(new CustomEvent("bytevault_logout"));
  };

  // Restore session from localStorage on startup
  const restoreSession = async () => {
    setLoading(true);
    setError(null);
    const storedToken = localStorage.getItem("bytevault_auth_token");
    const storedUserStr = localStorage.getItem("bytevault_user");
    
    if (storedToken) {
      setToken(storedToken);
      let initialUser = null;
      if (storedUserStr) {
        try {
          initialUser = JSON.parse(storedUserStr);
        } catch {}
      }
      if (!initialUser) {
        initialUser = extractUser(null, storedToken);
      }
      if (initialUser) {
        setUser(initialUser);
      }

      // Verify or refresh session in background with API Gateway
      try {
        const profile = await apiClient.get("/users/me");
        if (profile) {
          const mergedUser = {
            ...(initialUser || {}),
            ...profile,
            id: profile.userId || profile.id || initialUser?.id,
            name: profile.fullName || initialUser?.name || "User",
            role: initialUser?.role || profile.role || "CUSTOMER"
          };
          setUser(mergedUser);
          localStorage.setItem("bytevault_user", JSON.stringify(mergedUser));
        }
      } catch (err) {
        console.warn("Session verification warning on startup:", err?.message || err);
        if (err?.status === 401) {
          try {
            await refreshSession();
          } catch {
            logout();
          }
        }
      }
    }
    setLoading(false);
  };

  // Perform session token refresh rotation
  const refreshSession = async () => {
    try {
      const storedRefresh = localStorage.getItem("bytevault_refresh_token");
      if (!storedRefresh) throw new Error("No refresh token available");
      const res = await authService.refresh(storedRefresh);
      const userObj = extractUser(res, res?.token) || user;
      if (res?.token) {
        setToken(res.token);
        localStorage.setItem("bytevault_auth_token", res.token);
      }
      if (res?.refreshToken) {
        localStorage.setItem("bytevault_refresh_token", res.refreshToken);
      }
      if (userObj) {
        setUser(userObj);
        localStorage.setItem("bytevault_user", JSON.stringify(userObj));
      }
      return userObj;
    } catch (err) {
      console.error("Token session refresh failure", err);
      logout();
      throw err;
    }
  };

  useEffect(() => {
    restoreSession();
  }, []);

  // Listen to silent refresh failures from the apiClient interceptors
  useEffect(() => {
    const handleUnauthorizedEvent = () => {
      logout();
    };
    window.addEventListener("bytevault_unauthorized", handleUnauthorizedEvent);
    return () => window.removeEventListener("bytevault_unauthorized", handleUnauthorizedEvent);
  }, []);

  const login = async (email, password) => {
    setError(null);
    setLoading(true);
    try {
      const res = await authService.login(email, password);
      const userObj = extractUser(res, res?.token);
      setUser(userObj);
      setToken(res?.token || null);
      if (res?.token) localStorage.setItem("bytevault_auth_token", res.token);
      if (res?.refreshToken) localStorage.setItem("bytevault_refresh_token", res.refreshToken);
      if (userObj) localStorage.setItem("bytevault_user", JSON.stringify(userObj));
      setLoading(false);
      return userObj;
    } catch (err) {
      setLoading(false);
      setError(err.message || "Login failed.");
      throw err;
    }
  };

  const register = async (nameOrPayload, email, password, role = "CUSTOMER", vendorData = {}) => {
    setError(null);
    setLoading(true);
    try {
      let payload;
      if (typeof nameOrPayload === "object") {
        payload = nameOrPayload;
      } else {
        payload = { name: nameOrPayload, fullName: nameOrPayload, email, password, role, ...vendorData };
      }
      const res = await authService.register(payload);
      const userObj = extractUser(res, res?.token);
      setUser(userObj);
      setToken(res?.token || null);
      if (res?.token) localStorage.setItem("bytevault_auth_token", res.token);
      if (res?.refreshToken) localStorage.setItem("bytevault_refresh_token", res.refreshToken);
      if (userObj) localStorage.setItem("bytevault_user", JSON.stringify(userObj));
      setLoading(false);
      return userObj;
    } catch (err) {
      setLoading(false);
      setError(err.message || "Registration failed.");
      throw err;
    }
  };


  const value = {
    user,
    currentUser: user, // Alias mapping
    token,
    accessToken: token, // Alias mapping
    loading,
    error,
    login,
    register,
    logout,
    restoreSession,
    refreshSession,
    isAuthenticated: !!user,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

AuthProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};

export default AuthContext;
