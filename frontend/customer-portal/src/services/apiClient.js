// API Client Abstraction for the ByteVault Media Platform
// Intercepts and routes queries to local mock DB or direct backend API Gateway

import { mockProducts } from "../features/products/mockData";

// Read configuration variables from Vite environment variables
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";
const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === "true"; // False by default, PostgreSQL backend is source of truth

// ----------------------------------------------------
// CUSTOM ENTERPRISE ERROR HIERARCHY
// ----------------------------------------------------
export class ApiError extends Error {
  constructor(status, message, details = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = "Session expired. Please log in again.", details = {}) {
    super(401, message, details);
    this.name = "UnauthorizedError";
  }
}

export class ForbiddenError extends ApiError {
  constructor(message = "You do not have authorization to access this resource.", details = {}) {
    super(403, message, details);
    this.name = "ForbiddenError";
  }
}

export class NotFoundError extends ApiError {
  constructor(message = "The requested resource could not be found.", details = {}) {
    super(404, message, details);
    this.name = "NotFoundError";
  }
}

export class ConflictError extends ApiError {
  constructor(message = "A state conflict occurred. Please review your request.", details = {}) {
    super(409, message, details);
    this.name = "ConflictError";
  }
}

export class RateLimitError extends ApiError {
  constructor(message = "Too many requests. Please throttle your client rate.", details = {}) {
    super(429, message, details);
    this.name = "RateLimitError";
  }
}

export class ServerError extends ApiError {
  constructor(message = "Internal Server Error. Please contact support.", status = 500, details = {}) {
    super(status, message, details);
    this.name = "ServerError";
  }
}

export class NetworkError extends Error {
  constructor(message = "Unable to connect to ByteVault. Please check your network connection.") {
    super(message);
    this.name = "NetworkError";
  }
}

export class TimeoutError extends Error {
  constructor(message = "The request timed out. Please try again.") {
    super(message);
    this.name = "TimeoutError";
  }
}

// ----------------------------------------------------
// CORRELATION ID GENERATOR
// ----------------------------------------------------
const generateCorrelationId = () => {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Safe fallback UUID format
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

// ----------------------------------------------------
// LOCAL STORAGE MOCK DATABASE ROUTER
// ----------------------------------------------------
const initializeMockDb = () => {
  const defaultUsers = [
    {
      id: "usr_1",
      email: "customer@bytevault.com",
      password: "password123",
      name: "Alex Rivera (Customer)",
      role: "CUSTOMER",
      avatar: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&auto=format&fit=crop&q=80"
    },
    {
      id: "usr_2",
      email: "vendor@bytevault.com",
      password: "vendor123",
      name: "Marcus Vance (Vendor)",
      role: "VENDOR",
      avatar: "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100&auto=format&fit=crop&q=80"
    },
    {
      id: "usr_3",
      email: "admin@bytevault.com",
      password: "admin123",
      name: "Jane Smith (Admin)",
      role: "ADMIN",
      avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80"
    }
  ];

  if (!localStorage.getItem("bytevault_users")) {
    localStorage.setItem("bytevault_users", JSON.stringify(defaultUsers));
  } else {
    try {
      const currentUsers = JSON.parse(localStorage.getItem("bytevault_users")) || [];
      defaultUsers.forEach(du => {
        if (!currentUsers.some(u => u.email.toLowerCase() === du.email.toLowerCase())) {
          currentUsers.push(du);
        }
      });
      localStorage.setItem("bytevault_users", JSON.stringify(currentUsers));
    } catch {
      localStorage.setItem("bytevault_users", JSON.stringify(defaultUsers));
    }
  }

  // User-scoped address storage - DO NOT seed global addresses (causes isolation leakage)
  // Addresses are now keyed per user: bytevault_addresses_{userId}

  if (!localStorage.getItem("bytevault_products") || !localStorage.getItem("bytevault_products").includes("prod_8")) {
    const detailedProducts = mockProducts.map(p => {
      const isDigital = p.type === "DIGITAL" || p.type === "digital";
      return {
        ...p,
        type: isDigital ? "DIGITAL" : "PHYSICAL",
        description: isDigital 
          ? `Professional-grade software architecture asset. Includes standard modular template, deployment files, developer guides, and automated test coverages.`
          : `Ergonomic desktop companion. Manufactured from premium, long-lasting materials and custom tuned to maximize daily comfort and style.`,
        specs: isDigital ? {
          format: p.id === "prod_4" ? "Figma, PNG, SVG" : "ZIP (JS, React, HTML)",
          fileSize: p.id === "prod_4" ? "42.5 MB" : "158.2 MB",
          compatibility: "Web browsers, Figma, React 18+, Node 18+",
          license: "Commercial License (Single Seat)",
          version: "v1.4.0",
          updates: "Lifetime updates included"
        } : {
          weight: p.id === "prod_5" ? "1.2 kg" : "850g",
          dimensions: p.id === "prod_5" ? "32cm x 13cm x 4cm" : "48cm x 30cm x 15cm",
          material: p.id === "prod_5" ? "CNC Aluminum & PBT Keycaps" : "Waterproof Cordura Nylon",
          origin: "Imported",
          warranty: "2-Year Manufacturer Warranty"
        },
        reviews: [
          { id: "r1", author: "Sarah Connor", rating: 5, text: "Outstanding quality. Exactly what I needed for my professional setups.", date: "2026-08-10" },
          { id: "r2", author: "Marcus Aurelius", rating: 4, text: "Very solid build. Spacing and dimensions are highly ergonomic.", date: "2026-08-14" }
        ],
        faq: [
          { q: "What support is included?", a: "Every purchase includes detailed email support and access to our active developer documentation community." },
          { q: "Can I use this commercially?", a: "Yes, this license permits commercial usage for both single-developer and corporate client products." }
        ]
      };
    });

    const extraProducts = [
      {
        id: "prod_6",
        title: "Rust Microservices Architecture Blueprint",
        type: "DIGITAL",
        image: "https://images.unsplash.com/photo-1607799279861-4dd421887fb3?w=600&auto=format&fit=crop&q=80",
        price: 49.00,
        originalPrice: 75.00,
        rating: 4.9,
        ratingCount: 112,
        inStock: true,
        deliveryInfo: "Instant Download & E-Book PDF",
        category: "Software & Coding",
        description: "Build robust, safe, and lightning-fast microservices in Rust. Includes production templates, database migrations setup, and gRPC specs.",
        specs: {
          format: "PDF, EPUB, Source Code GitHub",
          fileSize: "89 MB",
          compatibility: "Rust Edition 2021+",
          license: "Personal Developer License",
          version: "v2.0.1",
          updates: "Free updates for 1 year"
        },
        reviews: [
          { id: "r1", author: "Linus T.", rating: 5, text: "Excellent architectural layout. Clean and precise.", date: "2026-08-20" }
        ],
        faq: [
          { q: "Are updates free?", a: "Yes, all updates within the first year of purchase are free." }
        ]
      },
      {
        id: "prod_7",
        title: "ByteVault Custom Leather Cord Organizer",
        type: "PHYSICAL",
        image: "https://images.unsplash.com/photo-1624996379697-f01d168b1a52?w=600&auto=format&fit=crop&q=80",
        price: 19.99,
        originalPrice: 24.99,
        rating: 4.3,
        ratingCount: 42,
        inStock: true,
        deliveryInfo: "Ships tomorrow",
        category: "Travel Gear",
        description: "Keep your cables, dongles, and power banks organized. Crafted from vegetable-tanned leather with heavy-duty brass snaps.",
        specs: {
          weight: "120g",
          dimensions: "15cm x 8cm x 2cm",
          material: "Vegetable-tanned leather",
          origin: "Local Crafted",
          warranty: "1-Year Warranty"
        },
        reviews: [
          { id: "r1", author: "David H.", rating: 4, text: "Very premium leather feel, keeps my desk tidy.", date: "2026-08-22" }
        ],
        faq: [
          { q: "How many cables can it hold?", a: "It comfortably manages up to 4 standard braided laptop or charging cables." }
        ]
      },
      {
        id: "prod_8",
        title: "Ultimate Figma Design System - Starter Pack",
        type: "DIGITAL",
        image: "https://images.unsplash.com/photo-1541462608141-2f58c6e68e98?w=600&auto=format&fit=crop&q=80",
        price: 39.00,
        rating: 4.6,
        ratingCount: 31,
        inStock: true,
        deliveryInfo: "Instant Figma Link",
        category: "Design Resources",
        description: "Kickstart UI projects in seconds. 500+ UI components, dark mode layouts, variables config, and responsive grid layouts.",
        specs: {
          format: "Figma File (.fig)",
          fileSize: "12 MB",
          compatibility: "Figma Desktop & Web",
          license: "Unlimited Project License",
          version: "v3.0.0",
          updates: "Lifetime updates"
        },
        reviews: [],
        faq: []
      }
    ];

    localStorage.setItem("bytevault_products", JSON.stringify([...detailedProducts, ...extraProducts]));
  }

  // NOTE: Downloads and Orders are NOT globally seeded.
  // They are scoped per user: bytevault_downloads_{userId}, bytevault_orders_{userId}
  // Global keys (bytevault_downloads, bytevault_orders) are ONLY used as legacy fallback
  // and are kept empty so new users always see correct empty states.
  
  // Seed DEMO data ONLY for the built-in demo account (usr_1 = customer@bytevault.com)
  const DEMO_USER_ID = "usr_1";
  const demoOrdersKey = `bytevault_orders_${DEMO_USER_ID}`;
  const demoDownloadsKey = `bytevault_downloads_${DEMO_USER_ID}`;

  if (!localStorage.getItem(demoOrdersKey)) {
    localStorage.setItem(demoOrdersKey, JSON.stringify([
      {
        id: "ord_998124",
        userId: DEMO_USER_ID,
        createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
        totals: { subtotal: 184.00, tax: 14.72, shipping: 0.00, total: 198.72 },
        items: [
          {
            id: "prod_6", productId: "prod_6",
            title: "Rust Microservices Architecture Blueprint",
            price: 49.00, quantity: 1, type: "DIGITAL",
            image: "https://images.unsplash.com/photo-1607799279861-4dd421887fb3?w=600&auto=format&fit=crop&q=80",
          },
          {
            id: "prod_2", productId: "prod_2",
            title: "Minimalist Full-Grain Leather Backpack",
            price: 135.00, quantity: 1, type: "PHYSICAL",
            image: "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80",
          }
        ],
        status: "IN_TRANSIT",
        fulfillmentStatus: "IN_TRANSIT",
        trackingSteps: [
          { label: "Processing & Payment Cleared", date: new Date(Date.now() - 86400000 * 5).toISOString(), completed: true },
          { label: "Packed in Warehouse", date: new Date(Date.now() - 86400000 * 4).toISOString(), completed: true },
          { label: "Shipped", date: new Date(Date.now() - 86400000 * 3).toISOString(), completed: true },
          { label: "In Transit", date: new Date(Date.now() - 86400000 * 2).toISOString(), completed: true },
          { label: "Delivered", date: "", completed: false }
        ]
      }
    ]));
  }

  if (!localStorage.getItem(demoDownloadsKey)) {
    localStorage.setItem(demoDownloadsKey, JSON.stringify([
      {
        id: "dl_1", productId: "prod_8",
        title: "Ultimate Figma Design System - Starter Pack",
        image: "https://images.unsplash.com/photo-1541462608141-2f58c6e68e98?w=600&auto=format&fit=crop&q=80",
        fileSize: "12 MB", format: "Figma File (.fig)",
        downloadCount: 3, status: "active",
        licenseKey: "BV-FIGM-DEMO-2026",
        purchaseDate: "August 2026",
        unlockedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      },
      {
        id: "dl_2", productId: "prod_6",
        title: "Rust Microservices Architecture Blueprint",
        image: "https://images.unsplash.com/photo-1607799279861-4dd421887fb3?w=600&auto=format&fit=crop&q=80",
        fileSize: "89 MB", format: "ZIP (PDF & Rust code)",
        downloadCount: 0, status: "active",
        licenseKey: "BV-RUST-DEMO-2026",
        purchaseDate: "August 2026",
        unlockedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
      }
    ]));
  }

  // Global fallback stubs (empty — new users always start fresh)
  if (!localStorage.getItem("bytevault_downloads")) {
    localStorage.setItem("bytevault_downloads", JSON.stringify([]));
  }
  if (!localStorage.getItem("bytevault_orders")) {
    localStorage.setItem("bytevault_orders", JSON.stringify([]));
  }
  if (!localStorage.getItem("bytevault_vendor_profiles")) {
    localStorage.setItem("bytevault_vendor_profiles", JSON.stringify([
      {
        id: "vp_1",
        userId: "usr_2",
        email: "vendor@bytevault.com",
        storeName: "HyperScale Studios",
        storeSlug: "hyperscale-studios",
        storeDescription: "High-performance distributed systems, architectures, and design tokens.",
        logoUrl: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=100&auto=format&fit=crop&q=80",
        supportEmail: "support@hyperscale.io",
        businessTaxId: "EU-VAT-892189",
        payoutInfo: "bank_acct: ****4920",
        status: "APPROVED",
        reviewedBy: "usr_3",
        reviewedAt: new Date(Date.now() - 86400000 * 10).toISOString(),
        createdAt: new Date(Date.now() - 86400000 * 15).toISOString(),
      },
      {
        id: "vp_2",
        userId: "usr_pending_1",
        email: "alex.creator@bytevault.com",
        storeName: "Pixel Craft Systems",
        storeSlug: "pixel-craft-systems",
        storeDescription: "Design blueprints and production-ready React component libraries.",
        logoUrl: "",
        supportEmail: "alex.creator@bytevault.com",
        businessTaxId: "US-EIN-992144",
        payoutInfo: "stripe: acct_8829104",
        status: "PENDING_APPROVAL",
        createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
      }
    ]));
  }
};


if (USE_MOCK_API) {
  initializeMockDb();
}

const delay = (ms = 350) => new Promise(resolve => setTimeout(resolve, ms));
const getDbData = (key) => JSON.parse(localStorage.getItem(key) || "[]");
const setDbData = (key, data) => localStorage.setItem(key, JSON.stringify(data));

// Intercept queries and route locally to mock datasets
const handleMockRequest = async (method, url, body = null, headers = {}) => {
  await delay();

  const cleanUrl = url.split("?")[0];
  const queryParams = new URLSearchParams(url.includes("?") ? url.split("?")[1] : "");

  let authUserId = null;
  const authHeader = headers["Authorization"] || headers["authorization"];
  if (authHeader && authHeader.startsWith("Bearer ")) {
    try {
      const token = authHeader.split(" ")[1];
      const payload = JSON.parse(atob(token.split(".")[1]));
      authUserId = payload.id;
    } catch {
      throw new UnauthorizedError("Mock JWT Token is invalid or signature check failed.");
    }
  }

  // AUTH ENDPOINTS
  if (cleanUrl === "/auth/login" && method === "POST") {
    const { email, password } = body;
    const users = getDbData("bytevault_users");
    const matched = users.find(u => u.email.toLowerCase() === (email || "").toLowerCase() && u.password === password);
    if (!matched) {
      throw new UnauthorizedError("Invalid credentials. Please verify your email and password.");
    }
    const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }));
    const payload = btoa(JSON.stringify({ id: matched.id, email: matched.email, role: matched.role }));
    const token = `${header}.${payload}.signature`;
    
    const cleanUser = { ...matched };
    delete cleanUser.password;
    return { token, user: cleanUser };
  }

  if (cleanUrl === "/auth/register" && method === "POST") {
    const { name, email, password, role } = body;
    const users = getDbData("bytevault_users");
    if (users.some(u => u.email.toLowerCase() === email.toLowerCase())) {
      throw new ConflictError("Registration failed: Email address is already in use.");
    }
    const userRole = role === "VENDOR" ? "VENDOR" : "CUSTOMER";
    const newUser = {
      id: `usr_${Date.now()}`,
      name,
      email: email.toLowerCase(),
      password,
      role: userRole,
      avatar: `https://images.unsplash.com/photo-${1535713875000 + Math.floor(Math.random() * 999)}?w=100&auto=format&fit=crop&q=80`
    };
    users.push(newUser);
    setDbData("bytevault_users", users);

    const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" }));
    const payload = btoa(JSON.stringify({ id: newUser.id, email: newUser.email, role: newUser.role }));
    const token = `${header}.${payload}.signature`;

    const cleanUser = { ...newUser };
    delete cleanUser.password;
    return { token, user: cleanUser };
  }

  if (cleanUrl === "/auth/refresh" && method === "POST") {
    const token = localStorage.getItem("bytevault_auth_token");
    if (!token) throw new UnauthorizedError("Refresh session failed: Access token missing.");
    return { token };
  }

  if (cleanUrl === "/auth/logout" && method === "POST") {
    try {
      localStorage.removeItem("bytevault_cart_guest");
      if (authUserId) {
        localStorage.removeItem(`bytevault_cart_${authUserId}`);
      }
      Object.keys(localStorage).forEach((key) => {
        if (key.startsWith("bytevault_cart")) {
          localStorage.removeItem(key);
        }
      });
    } catch {}
    return { success: true };
  }

  // USERS
  if (cleanUrl === "/users/me" && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const users = getDbData("bytevault_users");
    const matched = users.find(u => u.id === authUserId);
    if (!matched) throw new NotFoundError("User profile details not found.");
    const cleanUser = { ...matched };
    delete cleanUser.password;
    return cleanUser;
  }

  // PRODUCTS
  if (cleanUrl === "/products" && method === "GET") {
    let products = getDbData("bytevault_products");

    const minPrice = parseFloat(queryParams.get("minPrice") || "0");
    const maxPrice = parseFloat(queryParams.get("maxPrice") || "Infinity");
    products = products.filter(p => p.price >= minPrice && p.price <= maxPrice);

    const minRating = parseFloat(queryParams.get("minRating") || "0");
    products = products.filter(p => p.rating >= minRating);

    const typeFilter = queryParams.get("type");
    if (typeFilter && typeFilter !== "ALL") {
      products = products.filter(p => p.type.toUpperCase() === typeFilter.toUpperCase());
    }

    const categoryFilter = queryParams.get("category");
    if (categoryFilter && categoryFilter !== "All") {
      products = products.filter(p => p.category === categoryFilter);
    }

    const inStock = queryParams.get("inStockOnly") === "true";
    if (inStock) {
      products = products.filter(p => p.inStock);
    }

    const searchQ = queryParams.get("search");
    if (searchQ) {
      const q = searchQ.toLowerCase();
      products = products.filter(p => 
        p.title.toLowerCase().includes(q) || 
        p.category.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q)
      );
    }

    // Filter out non-published products for the public catalog
    products = products.filter(p => !p.status || p.status === "PUBLISHED");

    const sortBy = queryParams.get("sortBy") || "trending";
    if (sortBy === "price_asc") {
      products.sort((a, b) => a.price - b.price);
    } else if (sortBy === "price_desc") {
      products.sort((a, b) => b.price - a.price);
    } else if (sortBy === "rating") {
      products.sort((a, b) => b.rating - a.rating);
    } else if (sortBy === "newest") {
      products.sort((a, b) => b.id.localeCompare(a.id));
    }

    return products;
  }

  // --- VENDOR PRODUCT ENDPOINTS (MOCK) ---
  if (cleanUrl === "/products/vendor/my-products" && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const products = getDbData("bytevault_products");
    const statusParam = queryParams.get("status");
    let vendorProducts = products.filter(p => p.vendorId === authUserId || (!p.vendorId && authUserId === "usr_2"));
    if (statusParam) {
      vendorProducts = vendorProducts.filter(p => p.status === statusParam);
    }
    return vendorProducts;
  }

  if (cleanUrl.startsWith("/products/vendor/") && cleanUrl.endsWith("/publish") && method === "POST") {
    if (!authUserId) throw new UnauthorizedError();
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx].status = "PUBLISHED";
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  if (cleanUrl.startsWith("/products/vendor/") && cleanUrl.endsWith("/deactivate") && method === "POST") {
    if (!authUserId) throw new UnauthorizedError();
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx].status = "DEACTIVATED";
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  if (cleanUrl.startsWith("/products/vendor/") && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matched = products.find(p => p.id === id);
    if (!matched) throw new NotFoundError("Product not found.");
    return matched;
  }

  if (cleanUrl === "/products/vendor" && method === "POST") {
    if (!authUserId) throw new UnauthorizedError();
    const publishNow = queryParams.get("publish") === "true";
    const products = getDbData("bytevault_products");
    const newProduct = {
      ...body,
      id: `prod_${Date.now()}`,
      vendorId: authUserId,
      status: publishNow ? "PUBLISHED" : (body.status || "DRAFT"),
      rating: 5.0,
      ratingCount: 0,
      inStock: publishNow,
      createdAt: new Date().toISOString()
    };
    products.unshift(newProduct);
    setDbData("bytevault_products", products);
    return newProduct;
  }

  if (cleanUrl.startsWith("/products/vendor/") && method === "PUT") {
    if (!authUserId) throw new UnauthorizedError();
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx] = { ...products[matchedIdx], ...body, updatedAt: new Date().toISOString() };
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  // --- ADMIN MODERATION ENDPOINTS (MOCK) ---
  if (cleanUrl === "/products/admin/all" && method === "GET") {
    const products = getDbData("bytevault_products");
    const statusParam = queryParams.get("status");
    if (statusParam) {
      return products.filter(p => p.status === statusParam);
    }
    return products;
  }

  if (cleanUrl.startsWith("/products/admin/") && cleanUrl.endsWith("/takedown") && method === "POST") {
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx].status = "DEACTIVATED";
    products[matchedIdx].moderationReason = body.reason || "Moderated by Admin";
    products[matchedIdx].moderatedBy = authUserId || "admin";
    products[matchedIdx].moderatedAt = new Date().toISOString();
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  if (cleanUrl.startsWith("/products/admin/") && cleanUrl.endsWith("/reactivate") && method === "POST") {
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx].status = "PUBLISHED";
    products[matchedIdx].moderationReason = null;
    products[matchedIdx].moderatedBy = authUserId || "admin";
    products[matchedIdx].moderatedAt = new Date().toISOString();
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  if (cleanUrl.startsWith("/products/admin/") && method === "GET") {
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const matched = products.find(p => p.id === id);
    if (!matched) throw new NotFoundError("Product not found.");
    return matched;
  }

  if (cleanUrl.startsWith("/products/") && method === "GET") {
    const id = cleanUrl.split("/").pop();
    const products = getDbData("bytevault_products");
    const matched = products.find(p => p.id === id);
    if (!matched) throw new NotFoundError("Product not found.");
    return matched;
  }

  if (cleanUrl === "/products" && method === "POST") {
    // Admin mock creation
    if (!authUserId) throw new UnauthorizedError();
    const products = getDbData("bytevault_products");
    const newProduct = {
      ...body,
      id: `prod_${Date.now()}`,
      status: "PUBLISHED",
      rating: 5.0,
      ratingCount: 0,
      inStock: true
    };
    products.push(newProduct);
    setDbData("bytevault_products", products);
    return newProduct;
  }

  if (cleanUrl.startsWith("/products/") && method === "PUT") {
    // Admin mock update
    if (!authUserId) throw new UnauthorizedError();
    const id = cleanUrl.split("/").pop();
    const products = getDbData("bytevault_products");
    const matchedIdx = products.findIndex(p => p.id === id);
    if (matchedIdx === -1) throw new NotFoundError("Product not found.");
    products[matchedIdx] = { ...products[matchedIdx], ...body };
    setDbData("bytevault_products", products);
    return products[matchedIdx];
  }

  // CART ENDPOINTS (User Isolated)
  if (cleanUrl === "/cart" && method === "GET") {
    const userCartKey = authUserId ? `bytevault_cart_${authUserId}` : "bytevault_cart_guest";
    const cart = JSON.parse(localStorage.getItem(userCartKey) || '{"items":[]}');
    return cart;
  }

  if (cleanUrl === "/cart" && method === "POST") {
    const userCartKey = authUserId ? `bytevault_cart_${authUserId}` : "bytevault_cart_guest";
    localStorage.setItem(userCartKey, JSON.stringify(body));
    return body;
  }

  if (cleanUrl === "/cart/items" && method === "POST") {
    const userCartKey = authUserId ? `bytevault_cart_${authUserId}` : "bytevault_cart_guest";
    const cart = JSON.parse(localStorage.getItem(userCartKey) || '{"items":[]}');
    const { productId, quantity = 1 } = body || {};
    const products = getDbData("bytevault_products");
    const product = products.find(p => p.id === productId);
    if (product) {
      const existing = cart.items.find(i => i.id === productId);
      if (existing) {
        if (product.type !== "DIGITAL") {
          existing.quantity += quantity;
        }
      } else {
        cart.items.push({
          id: product.id,
          title: product.title,
          price: product.price,
          type: product.type || "DIGITAL",
          image: product.image,
          quantity: product.type === "DIGITAL" ? 1 : quantity
        });
      }
      localStorage.setItem(userCartKey, JSON.stringify(cart));
    }
    return cart;
  }

  if (cleanUrl.startsWith("/cart/items/") && method === "DELETE") {
    const userCartKey = authUserId ? `bytevault_cart_${authUserId}` : "bytevault_cart_guest";
    const productId = cleanUrl.replace("/cart/items/", "");
    const cart = JSON.parse(localStorage.getItem(userCartKey) || '{"items":[]}');
    cart.items = (cart.items || []).filter(i => i.id !== productId);
    localStorage.setItem(userCartKey, JSON.stringify(cart));
    return cart;
  }

  if (cleanUrl === "/cart" && method === "DELETE") {
    const userCartKey = authUserId ? `bytevault_cart_${authUserId}` : "bytevault_cart_guest";
    try {
      localStorage.removeItem(userCartKey);
      localStorage.removeItem("bytevault_cart_guest");
      Object.keys(localStorage).forEach((key) => {
        if (key.startsWith("bytevault_cart")) {
          localStorage.removeItem(key);
        }
      });
    } catch {}
    return { items: [] };
  }

  // ORDERS
  if (cleanUrl === "/orders" && method === "GET") {
    // User-isolated orders: keyed by authenticated userId
    if (authUserId) {
      const userOrders = JSON.parse(localStorage.getItem(`bytevault_orders_${authUserId}`) || "[]");
      return userOrders;
    }
    return [];
  }

  if (cleanUrl === "/downloads" && method === "GET") {
    // User-isolated downloads: keyed by authenticated userId
    if (authUserId) {
      const userDownloads = JSON.parse(localStorage.getItem(`bytevault_downloads_${authUserId}`) || "[]");
      return userDownloads;
    }
    return [];
  }

  if (cleanUrl === "/orders" && method === "POST") {
    const currentUserId = authUserId || body?.userId || "usr_guest";
    // User-isolated: each user has their own orders list
    const userOrdersKey = `bytevault_orders_${currentUserId}`;
    const orders = JSON.parse(localStorage.getItem(userOrdersKey) || "[]");
    const items = Array.isArray(body?.items) ? body.items : [];
    const isAllDigital = items.length > 0 && items.every(i => (i.type || "").toUpperCase() === "DIGITAL");

    const newOrder = {
      ...body,
      id: `ord_${Math.floor(100000 + Math.random() * 900000)}`,
      userId: currentUserId,
      createdAt: new Date().toISOString(),
      fulfillmentStatus: isAllDigital ? "DELIVERED" : "PROCESSING",
      trackingSteps: isAllDigital 
        ? [
            { label: "Fulfillment Initialized", date: new Date().toISOString(), completed: true },
            { label: "License Issued", date: new Date().toISOString(), completed: true },
            { label: "Instant Access Delivered", date: new Date().toISOString(), completed: true }
          ]
        : [
            { label: "Processing & Payment Cleared", date: new Date().toISOString(), completed: true },
            { label: "Packed in Warehouse", date: "", completed: false },
            { label: "Shipped", date: "", completed: false },
            { label: "In Transit", date: "", completed: false },
            { label: "Delivered", date: "", completed: false }
          ]
    };
    orders.push(newOrder);
    localStorage.setItem(userOrdersKey, JSON.stringify(orders));

    // Entitlement grant for digital items — user-isolated
    const userDownloadsKey = `bytevault_downloads_${currentUserId}`;
    const digitalItems = items.filter(i => (i.type || "").toUpperCase() === "DIGITAL");
    if (digitalItems.length > 0) {
      const downloads = JSON.parse(localStorage.getItem(userDownloadsKey) || "[]");
      digitalItems.forEach(item => {
        const prodId = item.productId || item.id;
        const licenseKey = `BV-${Math.random().toString(36).substring(2, 6).toUpperCase()}-${Math.random().toString(36).substring(2, 6).toUpperCase()}`;
        if (!downloads.some(d => d.productId === prodId)) {
          downloads.push({
            id: `dl_${Date.now()}_${prodId}`,
            productId: prodId,
            title: item.title || "Digital Asset",
            image: item.image || "",
            fileSize: item.fileSize || "158.2 MB",
            format: item.format || "ZIP",
            licenseKey,
            downloadCount: 0,
            status: "active",
            unlockedAt: new Date().toISOString()
          });
        }
        const purchaseDate = new Date().toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
        if (!downloads.some(d => d.productId === prodId)) {
          downloads.push({
            id: `dl_${Date.now()}_${prodId}`,
            productId: prodId,
            title: item.title || "Digital Asset",
            image: item.image || "",
            fileSize: item.fileSize || "158.2 MB",
            format: item.format || "ZIP",
            licenseKey,
            downloadCount: 0,
            status: "active",
            purchaseDate,
            unlockedAt: new Date().toISOString()
          });
        }
      });
      localStorage.setItem(userDownloadsKey, JSON.stringify(downloads));
    }
    return newOrder;
  }

  if (cleanUrl.startsWith("/orders/") && method === "GET") {
    const id = cleanUrl.split("/").pop();
    // Search in user-scoped orders first, then fall back to global
    const userOrdersKey = authUserId ? `bytevault_orders_${authUserId}` : null;
    let orders = userOrdersKey ? JSON.parse(localStorage.getItem(userOrdersKey) || "[]") : [];
    if (!orders.length) orders = JSON.parse(localStorage.getItem("bytevault_orders") || "[]");
    const found = orders.find(o => o.id === id);
    if (!found) throw new NotFoundError("Order detail could not be retrieved.");
    return found;
  }

  // PAYMENTS (Supports Razorpay and Card Fallback)
  if (cleanUrl === "/payments" && method === "POST") {
    const { cardNumber, amount, currency } = body || {};
    if (cardNumber && cardNumber.replace(/\s/g, "").endsWith("4444")) {
      throw new ConflictError("Simulated payment transaction decline. Try alternate credentials.");
    }
    const mockRzpOrderId = `order_mock_${Math.random().toString(36).substring(2, 10)}`;
    return { 
      orderId: mockRzpOrderId,
      amount: amount ? Math.round(amount * 100) : 10000,
      currency: currency || "INR",
      key: "rzp_test_sandbox_default",
      transactionId: `txn_${Date.now()}`, 
      status: "CREATED", 
      message: "Razorpay sandbox order created successfully." 
    };
  }

  if (cleanUrl === "/payments/verify" && method === "POST") {
    return { 
      status: "SUCCESS",
      success: true, 
      message: "Payment verified successfully via Razorpay sandbox.", 
      verifiedAt: new Date().toISOString() 
    };
  }

  // FULFILLMENT — User-isolated entitlements
  if (cleanUrl === "/entitlements" && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const userDownloadsKey = `bytevault_downloads_${authUserId}`;
    return JSON.parse(localStorage.getItem(userDownloadsKey) || "[]");
  }

  if (cleanUrl.startsWith("/downloads/") && method === "GET") {
    const productId = cleanUrl.split("/").pop();
    const downloads = getDbData("bytevault_downloads");
    let matched = downloads.find(d => d.productId === productId || d.id === productId);
    
    if (!matched) {
      const products = getDbData("bytevault_products");
      const prod = products.find(p => p.id === productId);
      if (prod) {
        matched = {
          id: `dl_${Date.now()}_${prod.id}`,
          productId: prod.id,
          title: prod.title,
          format: prod.specs?.format || "ZIP",
          fileSize: prod.specs?.fileSize || "120 MB",
          licenseKey: `BV-${Math.random().toString(36).substring(2, 6).toUpperCase()}-${Math.random().toString(36).substring(2, 6).toUpperCase()}`,
          downloadCount: 0
        };
        downloads.push(matched);
      }
    }
    
    if (!matched) throw new NotFoundError("No entitlement asset matches this product ID.");
    
    matched.downloadCount = (matched.downloadCount || 0) + 1;
    setDbData("bytevault_downloads", downloads);

    const safeTitle = (matched.title || "asset").toLowerCase().replace(/[^a-z0-9]/g, "_");
    return { 
      downloadUrl: `http://localhost:8080/api/v1/downloads/files/${matched.id}?sig=sec_${Math.random().toString(36).substring(2, 10)}`,
      fileName: `${safeTitle}.zip`,
      title: matched.title,
      licenseKey: matched.licenseKey || "BV-PRO-ENTITLEMENT",
      fileSize: matched.fileSize || "120 MB",
      format: matched.format || "ZIP"
    };
  }

  // ADDRESSES — User-isolated: each user's addresses stored under bytevault_addresses_{userId}
  if ((cleanUrl === "/users/addresses" || cleanUrl === "/users/me/addresses") && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const addrKey = `bytevault_addresses_${authUserId}`;
    return JSON.parse(localStorage.getItem(addrKey) || "[]");
  }

  if ((cleanUrl === "/users/addresses" || cleanUrl === "/users/me/addresses") && method === "POST") {
    if (!authUserId) throw new UnauthorizedError();
    const addrKey = `bytevault_addresses_${authUserId}`;
    const addresses = JSON.parse(localStorage.getItem(addrKey) || "[]");
    const newAddress = {
      ...body,
      id: `addr_${Date.now()}`,
      isDefault: addresses.length === 0 ? true : body.isDefault || false
    };
    if (newAddress.isDefault) {
      addresses.forEach(a => a.isDefault = false);
    }
    addresses.push(newAddress);
    localStorage.setItem(addrKey, JSON.stringify(addresses));
    return newAddress;
  }

  if ((cleanUrl.startsWith("/users/addresses/") || cleanUrl.startsWith("/users/me/addresses/")) && method === "DELETE") {
    if (!authUserId) throw new UnauthorizedError();
    const addrKey = `bytevault_addresses_${authUserId}`;
    const id = cleanUrl.split("/").pop();
    let addresses = JSON.parse(localStorage.getItem(addrKey) || "[]");
    addresses = addresses.filter(a => a.id !== id);
    if (addresses.length > 0 && !addresses.some(a => a.isDefault)) {
      addresses[0].isDefault = true;
    }
    localStorage.setItem(addrKey, JSON.stringify(addresses));
    return { success: true };
  }

  // VENDOR ENDPOINTS
  if (cleanUrl === "/products/vendor/my-products" && method === "GET") {
    const products = getDbData("bytevault_products");
    return products.filter(p => p.vendorId === authUserId || !p.vendorId);
  }

  if (cleanUrl === "/products/vendor" && method === "POST") {
    const products = getDbData("bytevault_products");
    const newP = {
      ...body,
      id: `prod_${Date.now()}`,
      vendorId: authUserId,
      status: "PENDING_APPROVAL",
      rating: 5.0,
      ratingCount: 0,
      inStock: true
    };
    products.unshift(newP);
    setDbData("bytevault_products", products);
    return newP;
  }

  if (cleanUrl === "/orders/vendor/my-sales" && method === "GET") {
    const orders = getDbData("bytevault_orders");
    const sales = [];
    orders.forEach(o => {
      (o.items || []).forEach(item => {
        sales.push({
          orderItemId: `item_${Math.random().toString(36).substring(2, 8)}`,
          orderId: o.id,
          orderNumber: o.id,
          productId: item.id || item.productId,
          productName: item.title || item.productName || "Product",
          sku: item.sku || "SKU-BV-001",
          productType: item.type || "DIGITAL",
          unitPrice: item.price || 49.00,
          quantity: item.quantity || 1,
          subtotal: (item.price || 49.00) * (item.quantity || 1),
          orderStatus: o.fulfillmentStatus || "PAID",
          customerEmail: o.shippingAddress?.email || "customer@bytevault.com",
          customerName: o.shippingAddress?.name || "Customer",
          createdAt: o.createdAt || new Date().toISOString()
        });
      });
    });
    return sales;
  }

  if (cleanUrl === "/payments/vendor/earnings" && method === "GET") {
    return {
      vendorId: authUserId,
      grossEarnings: 14820.00,
      platformFees: 1482.00,
      netEarnings: 13338.00,
      availableBalance: 3420.50,
      pendingSettlement: 1840.00,
      totalOrdersCount: 28,
      ledgerEntries: [
        { id: "led_1", orderId: "ord_998124", grossAmount: 184.00, platformFee: 18.40, netAmount: 165.60, status: "AVAILABLE", createdAt: new Date().toISOString() },
        { id: "led_2", orderId: "ord_998125", grossAmount: 89.00, platformFee: 8.90, netAmount: 80.10, status: "PENDING_SETTLEMENT", createdAt: new Date().toISOString() }
      ]
    };
  }

  // ADMIN EXCEL IMPORT & PRODUCTS
  if (cleanUrl === "/products/admin/all" && method === "GET") {
    return getDbData("bytevault_products");
  }

  if (cleanUrl.includes("/products/admin/") && cleanUrl.endsWith("/approve") && method === "PUT") {
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const p = products.find(prod => prod.id === id);
    if (p) p.status = "ACTIVE";
    setDbData("bytevault_products", products);
    return p;
  }

  if (cleanUrl.includes("/products/admin/") && cleanUrl.endsWith("/reject") && method === "PUT") {
    const id = cleanUrl.split("/")[3];
    const products = getDbData("bytevault_products");
    const p = products.find(prod => prod.id === id);
    if (p) p.status = "REJECTED";
    setDbData("bytevault_products", products);
    return p;
  }

  if (cleanUrl === "/products/admin/import/preview" && method === "POST") {
    return {
      totalRows: 5,
      validRows: 4,
      invalidRows: 1,
      previewRows: [
        { rowNumber: 1, name: "Enterprise Next.js Boilerplate", price: 79.00, currency: "INR", sku: "SKU-NXT-01", productType: "DIGITAL", categoryName: "Software & Coding", valid: true, errors: [] },
        { rowNumber: 2, name: "KeyPro Walnut Wrist Rest", price: 34.00, currency: "INR", sku: "SKU-KEY-02", productType: "PHYSICAL", categoryName: "Peripherals", valid: true, errors: [] },
        { rowNumber: 3, name: "Incomplete Asset", price: 0, currency: "INR", sku: "", productType: "DIGITAL", categoryName: "", valid: false, errors: ["Missing SKU", "Invalid price"] },
        { rowNumber: 4, name: "Go Microservice Blueprint", price: 49.00, currency: "INR", sku: "SKU-GO-04", productType: "DIGITAL", categoryName: "Software & Coding", valid: true, errors: [] },
        { rowNumber: 5, name: "Figma Dark Mode Wireframes", price: 29.00, currency: "INR", sku: "SKU-FIG-05", productType: "DIGITAL", categoryName: "Design", valid: true, errors: [] }
      ]
    };
  }

  if (cleanUrl === "/products/admin/import/confirm" && method === "POST") {
    const rows = body?.rows || [];
    const products = getDbData("bytevault_products");
    let imported = 0;
    rows.forEach(r => {
      if (r.valid !== false) {
        products.push({
          id: `prod_imp_${Date.now()}_${imported}`,
          title: r.name,
          price: r.price,
          type: r.productType || "DIGITAL",
          sku: r.sku,
          category: r.categoryName || "General",
          inStock: true,
          rating: 5.0,
          ratingCount: 0,
          status: "ACTIVE"
        });
        imported++;
      }
    });
    setDbData("bytevault_products", products);
    return { totalRows: rows.length, importedCount: imported, failedCount: 0, errors: [] };
  }

  // SUPPORT TICKETS
  if (!localStorage.getItem("bytevault_support_tickets")) {
    localStorage.setItem("bytevault_support_tickets", JSON.stringify([
      {
        id: "tick_101",
        userId: "usr_1",
        userEmail: "customer@bytevault.com",
        subject: "Download signature question for Rust Blueprint",
        description: "Need guidance on verifying the SHA-256 signature in Windows PowerShell.",
        category: "TECHNICAL_SUPPORT",
        status: "OPEN",
        priority: "MEDIUM",
        createdAt: new Date(Date.now() - 86400000).toISOString(),
        messages: [
          { id: "msg_1", senderId: "usr_1", senderEmail: "customer@bytevault.com", message: "Need guidance on verifying the SHA-256 signature in Windows PowerShell.", isAdminReply: false, createdAt: new Date(Date.now() - 86400000).toISOString() }
        ]
      }
    ]));
  }

  if (cleanUrl === "/support/tickets/my" && method === "GET") {
    const tickets = getDbData("bytevault_support_tickets");
    return tickets.filter(t => t.userId === authUserId || !authUserId || authUserId === "usr_1");
  }

  if (cleanUrl === "/support/tickets/admin" && method === "GET") {
    return getDbData("bytevault_support_tickets");
  }

  if (cleanUrl === "/support/tickets" && method === "POST") {
    const tickets = getDbData("bytevault_support_tickets");
    const newTicket = {
      id: `tick_${Date.now().toString().substring(6)}`,
      userId: authUserId || "usr_1",
      userEmail: body.userEmail || "customer@bytevault.com",
      subject: body.subject,
      description: body.description,
      category: body.category || "GENERAL_INQUIRY",
      status: "OPEN",
      priority: body.priority || "MEDIUM",
      createdAt: new Date().toISOString(),
      messages: [
        { id: `msg_${Date.now()}`, senderId: authUserId || "usr_1", senderEmail: body.userEmail || "customer@bytevault.com", message: body.description, isAdminReply: false, createdAt: new Date().toISOString() }
      ]
    };
    tickets.unshift(newTicket);
    setDbData("bytevault_support_tickets", tickets);
    return newTicket;
  }

  if (cleanUrl.startsWith("/support/tickets/") && cleanUrl.endsWith("/messages") && method === "POST") {
    const parts = cleanUrl.split("/");
    const ticketId = parts[3];
    const tickets = getDbData("bytevault_support_tickets");
    const t = tickets.find(tick => tick.id === ticketId);
    if (!t) throw new NotFoundError("Support ticket not found.");
    const newMsg = {
      id: `msg_${Date.now()}`,
      senderId: authUserId || "usr_admin",
      senderEmail: body.senderEmail || "support@bytevault.com",
      message: body.message,
      isAdminReply: body.isAdminReply || false,
      createdAt: new Date().toISOString()
    };
    t.messages = t.messages || [];
    t.messages.push(newMsg);
    setDbData("bytevault_support_tickets", tickets);
    return newMsg;
  }

  if (cleanUrl.startsWith("/support/tickets/") && cleanUrl.includes("/status") && method === "PUT") {
    const parts = cleanUrl.split("/");
    const ticketId = parts[3];
    const newStatus = queryParams.get("status") || "RESOLVED";
    const tickets = getDbData("bytevault_support_tickets");
    const t = tickets.find(tick => tick.id === ticketId);
    if (t) t.status = newStatus;
    setDbData("bytevault_support_tickets", tickets);
    return t;
  }

  if (cleanUrl === "/users/profile" && method === "PUT") {
    if (!authUserId) throw new UnauthorizedError();
    const users = getDbData("bytevault_users");
    const matchedIdx = users.findIndex(u => u.id === authUserId);
    if (matchedIdx === -1) throw new NotFoundError();
    users[matchedIdx] = { ...users[matchedIdx], ...body };
    setDbData("bytevault_users", users);
    const cleanUser = { ...users[matchedIdx] };
    delete cleanUser.password;
    return cleanUser;
  }

  // VENDOR PROFILE & ADMIN VENDOR ENDPOINTS
  if (cleanUrl === "/users/me/vendor-profile" && method === "GET") {
    if (!authUserId) throw new UnauthorizedError();
    const profiles = getDbData("bytevault_vendor_profiles");
    let p = profiles.find(vp => vp.userId === authUserId);
    if (!p) {
      const users = getDbData("bytevault_users");
      const u = users.find(usr => usr.id === authUserId);
      p = {
        id: `vp_${Date.now()}`,
        userId: authUserId,
        email: u?.email || "vendor@bytevault.com",
        storeName: u?.name ? `${u.name}'s Store` : "Vendor Store",
        storeSlug: `store-${authUserId.substring(0, 6)}`,
        storeDescription: "New seller on ByteVault Media marketplace.",
        status: "PENDING_APPROVAL",
        createdAt: new Date().toISOString()
      };
      profiles.push(p);
      setDbData("bytevault_vendor_profiles", profiles);
    }
    return p;
  }

  if (cleanUrl === "/users/me/vendor-profile" && method === "PUT") {
    if (!authUserId) throw new UnauthorizedError();
    const profiles = getDbData("bytevault_vendor_profiles");
    let p = profiles.find(vp => vp.userId === authUserId);
    if (!p) {
      p = {
        id: `vp_${Date.now()}`,
        userId: authUserId,
        status: "PENDING_APPROVAL",
        createdAt: new Date().toISOString(),
        ...body
      };
      profiles.push(p);
    } else {
      Object.assign(p, body, { updatedAt: new Date().toISOString() });
    }
    setDbData("bytevault_vendor_profiles", profiles);
    return p;
  }

  if (cleanUrl === "/users/admin/vendors/pending" && method === "GET") {
    const profiles = getDbData("bytevault_vendor_profiles");
    return profiles.filter(p => p.status === "PENDING_APPROVAL");
  }

  if (cleanUrl === "/users/admin/vendors" && method === "GET") {
    const statusFilter = queryParams.get("status");
    const profiles = getDbData("bytevault_vendor_profiles");
    if (statusFilter) {
      return profiles.filter(p => p.status === statusFilter.toUpperCase());
    }
    return profiles;
  }

  if (cleanUrl.startsWith("/users/admin/vendors/") && cleanUrl.endsWith("/approve") && method === "POST") {
    const parts = cleanUrl.split("/");
    const vendorId = parts[4];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.id === vendorId || vp.userId === vendorId);
    if (!p) throw new NotFoundError("Vendor profile not found");
    p.status = "APPROVED";
    p.reviewedBy = authUserId || "usr_3";
    p.reviewedAt = new Date().toISOString();
    p.rejectionReason = null;
    p.suspensionReason = null;
    setDbData("bytevault_vendor_profiles", profiles);
    return p;
  }

  if (cleanUrl.startsWith("/users/admin/vendors/") && cleanUrl.endsWith("/reject") && method === "POST") {
    const parts = cleanUrl.split("/");
    const vendorId = parts[4];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.id === vendorId || vp.userId === vendorId);
    if (!p) throw new NotFoundError("Vendor profile not found");
    p.status = "REJECTED";
    p.rejectionReason = body?.reason || "Application rejected by administration";
    p.reviewedBy = authUserId || "usr_3";
    p.reviewedAt = new Date().toISOString();
    setDbData("bytevault_vendor_profiles", profiles);
    return p;
  }

  if (cleanUrl.startsWith("/users/admin/vendors/") && cleanUrl.endsWith("/suspend") && method === "POST") {
    const parts = cleanUrl.split("/");
    const vendorId = parts[4];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.id === vendorId || vp.userId === vendorId);
    if (!p) throw new NotFoundError("Vendor profile not found");
    p.status = "SUSPENDED";
    p.suspensionReason = body?.reason || "Suspended by administration";
    p.reviewedBy = authUserId || "usr_3";
    p.reviewedAt = new Date().toISOString();
    setDbData("bytevault_vendor_profiles", profiles);
    return p;
  }

  if (cleanUrl.startsWith("/users/admin/vendors/") && cleanUrl.endsWith("/reactivate") && method === "POST") {
    const parts = cleanUrl.split("/");
    const vendorId = parts[4];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.id === vendorId || vp.userId === vendorId);
    if (!p) throw new NotFoundError("Vendor profile not found");
    p.status = "APPROVED";
    p.suspensionReason = null;
    p.reviewedBy = authUserId || "usr_3";
    p.reviewedAt = new Date().toISOString();
    setDbData("bytevault_vendor_profiles", profiles);
    return p;
  }

  if (cleanUrl.startsWith("/users/admin/vendors/") && method === "GET") {
    const parts = cleanUrl.split("/");
    const vendorId = parts[4];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.id === vendorId || vp.userId === vendorId);
    if (!p) throw new NotFoundError("Vendor profile not found");
    return p;
  }

  if (cleanUrl.startsWith("/users/stores/") && method === "GET") {
    const slug = cleanUrl.split("/")[3];
    const profiles = getDbData("bytevault_vendor_profiles");
    const p = profiles.find(vp => vp.storeSlug === slug);
    if (!p) throw new NotFoundError("Store not found");
    return p;
  }


  throw new NotFoundError(`Interceptors missing for endpoint: ${method} ${cleanUrl}`);
};

// ----------------------------------------------------
// CORE REQUEST EXECUTION BLOCK
// ----------------------------------------------------
const request = async (method, url, data = null, options = {}) => {
  // If Mock API mode is enabled, intercept and process locally
  if (USE_MOCK_API) {
    return handleMockRequest(method, url, data, options.headers || {});
  }

  const correlationId = generateCorrelationId();
  const token = localStorage.getItem("bytevault_auth_token");
  
  const headers = {
    "Content-Type": "application/json",
    "X-Correlation-ID": correlationId,
    ...(token ? { "Authorization": `Bearer ${token}` } : {}),
    ...(options.headers || {})
  };

  const controller = new AbortController();
  // 10 seconds timeout rule
  const timeoutMs = options.timeout || 10000;
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${url}`, {
      ...options,
      method,
      headers,
      signal: controller.signal,
      body: data ? JSON.stringify(data) : undefined
    });
    
    clearTimeout(timeoutId);

    if (!response.ok) {
      const errBody = await response.json().catch(() => ({}));
      const errMsg = errBody.message || (response.status === 401 ? "Invalid credentials. Please verify your email and password." : `HTTP Server returned error status ${response.status}`);
      
      const isLoginOrAuth = url.includes("/auth/login") || url.includes("/auth/register") || url.includes("/auth/refresh");
      if (response.status === 401 && !isLoginOrAuth) {
        try {
          const refreshRes = await fetch(`${API_BASE_URL}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" }
          });
          if (refreshRes.ok) {
            const { token: newToken } = await refreshRes.json();
            localStorage.setItem("bytevault_auth_token", newToken);
            
            const retryResponse = await fetch(`${API_BASE_URL}${url}`, {
              ...options,
              method,
              headers: {
                ...headers,
                "Authorization": `Bearer ${newToken}`
              },
              signal: controller.signal,
              body: data ? JSON.stringify(data) : undefined
            });
            if (retryResponse.ok) {
              if (retryResponse.status === 204) return null;
              return retryResponse.json();
            }
          }
        } catch (e) {
          console.warn("Auto token refresh failed", e);
        }
        
        localStorage.removeItem("bytevault_auth_token");
        window.dispatchEvent(new Event("bytevault_unauthorized"));
      }

      if ((response.status === 503 || response.status === 502) && USE_MOCK_API) {
        console.warn(`Gateway returned ${response.status} for ${url}. Executing resilient local fallback handler.`);
        return handleMockRequest(method, url, data, options.headers || {});
      }

      switch (response.status) {
        case 401:
          throw new UnauthorizedError(errMsg, errBody);
        case 403:
          throw new ForbiddenError(errMsg, errBody);
        case 404:
          throw new NotFoundError(errMsg, errBody);
        case 409:
          throw new ConflictError(errMsg, errBody);
        case 429:
          throw new RateLimitError(errMsg, errBody);
        default:
          if (response.status >= 500) {
            throw new ServerError(errMsg, response.status, errBody);
          }
          throw new ApiError(response.status, errMsg, errBody);
      }
    }

    if (response.status === 204) return null;
    const json = await response.json();
    const payload = (json && typeof json === 'object' && 'data' in json) ? json.data : json;
    if (payload && typeof payload === 'object' && Array.isArray(payload.content)) {
      return payload.content;
    }
    return payload;
  } catch (error) {
    clearTimeout(timeoutId);

    if (error.name === "AbortError") {
      throw new TimeoutError("The request to ByteVault server timed out after 10 seconds.");
    }
    if (error instanceof ApiError) {
      throw error;
    }
    if (USE_MOCK_API) {
      console.warn(`Downstream service unavailable for ${url}. Executing resilient local fallback handler.`);
      return handleMockRequest(method, url, data, options.headers || {});
    }
    throw new NetworkError(error.message || `Failed to reach ${url}`);
  }
};

export const apiClient = {
  get: (url, options = {}) => request("GET", url, null, options),
  post: (url, data = null, options = {}) => request("POST", url, data, options),
  put: (url, data = null, options = {}) => request("PUT", url, data, options),
  patch: (url, data = null, options = {}) => request("PATCH", url, data, options),
  delete: (url, options = {}) => request("DELETE", url, options)
};

export default apiClient;
