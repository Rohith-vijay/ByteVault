import React, { createContext, useContext, useState, useEffect } from "react";
import PropTypes from "prop-types";

export const WishlistContext = createContext(null);

export const WishlistProvider = ({ children }) => {
  const [wishlistItems, setWishlistItems] = useState(() => {
    try {
      const data = localStorage.getItem("bytevault_wishlist_items");
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  });

  // Save changes to local storage whenever item triggers occur
  useEffect(() => {
    try {
      localStorage.setItem("bytevault_wishlist_items", JSON.stringify(wishlistItems));
    } catch (e) {
      console.error("Failed to save wishlist to localStorage", e);
    }
  }, [wishlistItems]);

  const toggleWishlist = (product) => {
    setWishlistItems((prev) => {
      const exists = prev.some((item) => item.id === product.id);
      if (exists) {
        window.dispatchEvent(new CustomEvent("bytevault_toast", {
          detail: { message: `Removed from wishlist.` }
        }));
        return prev.filter((item) => item.id !== product.id);
      }
      window.dispatchEvent(new CustomEvent("bytevault_toast", {
        detail: { message: `Added to wishlist!` }
      }));
      return [...prev, product];
    });
  };

  const isWishlisted = (productId) => {
    return wishlistItems.some((item) => item.id === productId);
  };

  const clearWishlist = () => {
    setWishlistItems([]);
  };

  const value = {
    wishlistItems,
    toggleWishlist,
    isWishlisted,
    clearWishlist,
  };

  return (
    <WishlistContext.Provider value={value}>{children}</WishlistContext.Provider>
  );
};

WishlistProvider.propTypes = {
  children: PropTypes.node.isRequired,
};

export const useWishlist = () => {
  const context = useContext(WishlistContext);
  if (!context) {
    throw new Error("useWishlist must be used within a WishlistProvider");
  }
  return context;
};
