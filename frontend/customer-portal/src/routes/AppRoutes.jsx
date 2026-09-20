import React, { useEffect, Suspense, lazy } from "react";
import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";

// Layout & Guards
import { AppLayout } from "../layouts/AppLayout";
import { ProtectedRoute, PublicRoute, RoleRoute } from "./RouteGuard";
import { slideUpVariants } from "../animations/motion";
import { Skeleton } from "../components/primitives/Skeleton";
import { Container } from "../components/primitives/Container";

// Lazy-loaded Pages
const Home = lazy(() => import("../pages/Home"));
const Catalog = lazy(() => import("../pages/Catalog"));
const ProductDetail = lazy(() => import("../pages/ProductDetail"));
const Cart = lazy(() => import("../pages/Cart"));
const Checkout = lazy(() => import("../pages/Checkout"));
const Account = lazy(() => import("../pages/Account"));
const Login = lazy(() => import("../pages/Login"));
const Register = lazy(() => import("../pages/Register"));
const ForgotPassword = lazy(() => import("../pages/ForgotPassword"));
const ResetPassword = lazy(() => import("../pages/ResetPassword"));
const EmailVerification = lazy(() => import("../pages/EmailVerification"));
const OrderConfirmation = lazy(() => import("../pages/OrderConfirmation"));
const OrderDetails = lazy(() => import("../pages/OrderDetails"));
const WishlistPage = lazy(() => import("../pages/WishlistPage"));
const InfoPages = lazy(() => import("../pages/InfoPages"));
const NotFound = lazy(() => import("../pages/NotFound"));
const Offline = lazy(() => import("../pages/Offline"));
const DesignSystemPlayground = lazy(() => import("../pages/DesignSystemPlayground"));
const AdminDashboard = lazy(() => import("../pages/AdminDashboard"));
const VendorDashboard = lazy(() => import("../pages/VendorDashboard"));

// Scroll restoration side effect
const ScrollRestoration = () => {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
};

// Animated route container
const AnimatedPage = ({ children }) => (
  <motion.div
    variants={slideUpVariants}
    initial="initial"
    animate="animate"
    exit="exit"
    style={{ minHeight: "100%" }}
  >
    {children}
  </motion.div>
);

// Fallback skeleton loader that fits inside the persistent layout shell
const LazyPageFallback = () => (
  <Container style={{ paddingTop: "80px", paddingBottom: "80px", textAlign: "center" }}>
    <Skeleton variant="rectangular" height={40} width="60%" style={{ margin: "0 auto 24px auto" }} />
    <Skeleton variant="rectangular" height={200} width="100%" style={{ marginBottom: "16px" }} />
    <Skeleton variant="rectangular" height={100} width="100%" />
  </Container>
);

const LazyPage = ({ Component }) => (
  <Suspense fallback={<LazyPageFallback />}>
    <Component />
  </Suspense>
);

const AppRoutesContent = () => {
  const location = useLocation();
  const isDev = import.meta.env.DEV;

  return (
    <>
      <ScrollRestoration />
      <AnimatePresence mode="wait">
        <Routes location={location} key={location.pathname}>
          {/* Public Views */}
          <Route path="/" element={<AppLayout><AnimatedPage><LazyPage Component={Home} /></AnimatedPage></AppLayout>} />
          <Route path="/catalog" element={<AppLayout><AnimatedPage><LazyPage Component={Catalog} /></AnimatedPage></AppLayout>} />
          <Route path="/products/:id" element={<AppLayout><AnimatedPage><LazyPage Component={ProductDetail} /></AnimatedPage></AppLayout>} />
          <Route path="/cart" element={<AppLayout><AnimatedPage><LazyPage Component={Cart} /></AnimatedPage></AppLayout>} />
          <Route path="/wishlist" element={<AppLayout><AnimatedPage><LazyPage Component={WishlistPage} /></AnimatedPage></AppLayout>} />
          
          {/* Informational Static Pages */}
          <Route path="/about" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/faq" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/contact" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/terms" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/privacy" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/refund" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/refund-policy" element={<AppLayout><AnimatedPage><LazyPage Component={InfoPages} /></AnimatedPage></AppLayout>} />
          <Route path="/offline" element={<AppLayout><AnimatedPage><LazyPage Component={Offline} /></AnimatedPage></AppLayout>} />

          {/* Guest Auth Views */}
          <Route 
            path="/login" 
            element={
              <PublicRoute>
                <AppLayout><AnimatedPage><LazyPage Component={Login} /></AnimatedPage></AppLayout>
              </PublicRoute>
            } 
          />
          <Route 
            path="/register" 
            element={
              <PublicRoute>
                <AppLayout><AnimatedPage><LazyPage Component={Register} /></AnimatedPage></AppLayout>
              </PublicRoute>
            } 
          />
          <Route 
            path="/forgot-password" 
            element={
              <PublicRoute>
                <AppLayout><AnimatedPage><LazyPage Component={ForgotPassword} /></AnimatedPage></AppLayout>
              </PublicRoute>
            } 
          />
          <Route 
            path="/reset-password" 
            element={
              <PublicRoute>
                <AppLayout><AnimatedPage><LazyPage Component={ResetPassword} /></AnimatedPage></AppLayout>
              </PublicRoute>
            } 
          />
          <Route 
            path="/email-verification" 
            element={
              <PublicRoute>
                <AppLayout><AnimatedPage><LazyPage Component={EmailVerification} /></AnimatedPage></AppLayout>
              </PublicRoute>
            } 
          />

          {/* Customer Cabinet Protected Views */}
          <Route 
            path="/checkout" 
            element={
              <ProtectedRoute>
                <AppLayout><AnimatedPage><LazyPage Component={Checkout} /></AnimatedPage></AppLayout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/account" 
            element={
              <ProtectedRoute>
                <AppLayout><AnimatedPage><LazyPage Component={Account} /></AnimatedPage></AppLayout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/order-confirmation/:id" 
            element={
              <ProtectedRoute>
                <AppLayout><AnimatedPage><LazyPage Component={OrderConfirmation} /></AnimatedPage></AppLayout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/orders" 
            element={
              <ProtectedRoute>
                <Navigate to="/account?tab=orders" replace />
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/orders/:id" 
            element={
              <ProtectedRoute>
                <AppLayout><AnimatedPage><LazyPage Component={OrderDetails} /></AnimatedPage></AppLayout>
              </ProtectedRoute>
            } 
          />

          {/* Development Sandbox */}
          {isDev ? (
            <Route path="/design-system" element={<AnimatedPage><LazyPage Component={DesignSystemPlayground} /></AnimatedPage>} />
          ) : (
            <Route path="/design-system" element={<Navigate to="/" replace />} />
          )}

          {/* Role Restricted Admin and Vendor Dashboards */}
          <Route 
            path="/admin/*" 
            element={
              <RoleRoute allowedRoles={["ADMIN"]}>
                <AppLayout><AnimatedPage><LazyPage Component={AdminDashboard} /></AnimatedPage></AppLayout>
              </RoleRoute>
            } 
          />
          <Route 
            path="/vendor/*" 
            element={
              <RoleRoute allowedRoles={["ADMIN", "VENDOR", "SELLER"]}>
                <AppLayout><AnimatedPage><LazyPage Component={VendorDashboard} /></AnimatedPage></AppLayout>
              </RoleRoute>
            } 
          />
          <Route 
            path="/seller/*" 
            element={<Navigate to="/vendor" replace />} 
          />

          {/* Fallback Catch-all 404 */}
          <Route path="*" element={<AppLayout><AnimatedPage><LazyPage Component={NotFound} /></AnimatedPage></AppLayout>} />
        </Routes>
      </AnimatePresence>
    </>
  );
};

export const AppRoutes = () => {
  return (
    <BrowserRouter>
      <AppRoutesContent />
    </BrowserRouter>
  );
};

export default AppRoutes;
