import React, { createContext, useContext, useState, useEffect } from "react";
import PropTypes from "prop-types";
import { cartService } from "../services/cartService";
import { useAuth } from "./AuthContext";

export const CartContext = createContext(null);

export const CartProvider = ({ children }) => {
  const { user } = useAuth();
  const [cartItems, setCartItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const cartStorageKey = user?.id ? `bytevault_cart_${user.id}` : "bytevault_cart_guest";

  // Load cart state from service layer on mount or user switch
  const loadCart = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await cartService.getCart();
      const items = Array.isArray(data) ? data : (data?.items && Array.isArray(data.items) ? data.items : []);
      setCartItems(items);
    } catch (err) {
      console.warn("Failed to retrieve cart items from service, fallback to isolated store", err);
      try {
        const stored = localStorage.getItem(cartStorageKey);
        if (stored) {
          const parsed = JSON.parse(stored);
          const items = Array.isArray(parsed) ? parsed : (parsed?.items && Array.isArray(parsed.items) ? parsed.items : []);
          setCartItems(items);
        } else {
          setCartItems([]);
        }
      } catch {
        setCartItems([]);
      }
    } finally {
      setLoading(false);
    }
  };

  // Reload cart whenever authenticated user context changes
  useEffect(() => {
    loadCart();
  }, [user?.id]);

  // Listen to explicit logout event to immediately purge in-memory cart
  useEffect(() => {
    const handleLogout = () => {
      setCartItems([]);
      try {
        localStorage.removeItem("bytevault_cart_guest");
        if (user?.id) {
          localStorage.removeItem(`bytevault_cart_${user.id}`);
        }
        Object.keys(localStorage).forEach((key) => {
          if (key.startsWith("bytevault_cart")) {
            localStorage.removeItem(key);
          }
        });
      } catch {}
    };

    window.addEventListener("bytevault_logout", handleLogout);
    return () => window.removeEventListener("bytevault_logout", handleLogout);
  }, [user?.id]);

  const totals = cartService.calculateTotals(cartItems);

  // Sync updates back to the service layer and isolated fallback storage
  const syncCart = async (nextItems) => {
    setCartItems(nextItems);
    try {
      await cartService.saveCart({ items: nextItems });
    } catch (err) {
      console.warn("Failed to sync cart changes to service layer", err);
    }
    try {
      localStorage.setItem(cartStorageKey, JSON.stringify(nextItems));
    } catch (e) {
      console.error("Failed to write cart backup to local recovery store", e);
    }
  };


  // Adds a product to the basket
  const addItem = (product) => {
    const existingIndex = cartItems.findIndex((item) => item.id === product.id);
    const isDigital = product.type === "DIGITAL" || product.type === "digital";
    const itemType = isDigital ? "DIGITAL" : "PHYSICAL";
    const alreadyInCart = existingIndex > -1;

    const updated = [...cartItems];
    if (alreadyInCart) {
      // Enforce digital restriction: Quantity is always capped at 1
      if (itemType === "DIGITAL") {
        // Already in cart — fire a Go To Cart toast
        window.dispatchEvent(new CustomEvent("bytevault_toast", {
          detail: { message: `Already in your cart.`, actionLabel: "Go to Cart", actionHref: "/cart", type: "info" }
        }));
        return;
      }
      
      // Physical products increment quantity
      updated[existingIndex] = {
        ...updated[existingIndex],
        quantity: updated[existingIndex].quantity + 1,
      };
    } else {
      // Add as new item
      updated.push({
        id: product.id,
        title: product.title,
        price: product.price,
        originalPrice: product.originalPrice,
        image: product.image,
        type: itemType,
        quantity: 1,
        deliveryInfo: product.deliveryInfo,
        inStock: product.inStock
      });
    }
    syncCart(updated);
    // Emit toast with "Go to Cart" action button
    window.dispatchEvent(new CustomEvent("bytevault_toast", {
      detail: { 
        message: `${product.title?.slice(0, 40) || "Item"} added to cart!`,
        actionLabel: "Go to Cart",
        actionHref: "/cart",
        type: "success"
      }
    }));
  };

  // Adjusts item quantities
  const updateQuantity = (productId, quantity) => {
    const updated = cartItems.map((item) => {
      if (item.id === productId) {
        // Digital items cannot exceed a quantity of 1
        if (item.type === "DIGITAL") {
          return { ...item, quantity: 1 };
        }
        return { ...item, quantity: Math.max(1, quantity) };
      }
      return item;
    });
    syncCart(updated);
  };

  // Removes item from cart
  const removeItem = (productId) => {
    const updated = cartItems.filter((item) => item.id !== productId);
    syncCart(updated);
    window.dispatchEvent(new CustomEvent("bytevault_toast", {
      detail: { message: `Removed from cart.` }
    }));
  };

  // Empties cart
  const clearCart = () => {
    setCartItems([]);
    syncCart([]);
    try {
      localStorage.removeItem(cartStorageKey);
      localStorage.removeItem("bytevault_cart_guest");
    } catch {}
  };

  // Removes a batch of items by id array (used post-checkout for partial purchases)
  const removeItems = (productIds) => {
    const idSet = new Set(productIds);
    const updated = cartItems.filter((item) => !idSet.has(item.id));
    syncCart(updated);
  };

  // Check if a product is already in the cart
  const isInCart = (productId) => cartItems.some((item) => item.id === productId);

  const value = {
    cartItems,
    totals,
    loading,
    error,
    addItem,
    updateQuantity,
    removeItem,
    removeItems,
    isInCart,
    clearCart,
    retryLoad: loadCart
  };

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
};

CartProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error("useCart must be used within a CartProvider");
  }
  return context;
};

export default CartContext;
