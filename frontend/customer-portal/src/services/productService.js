// Product Service Wrapper calling apiClient
import apiClient from "./apiClient";

const defaultImages = {
  "E-Books": "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&auto=format&fit=crop&q=80",
  "Developer Software": "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&auto=format&fit=crop&q=80",
  "Hardware & Security": "https://images.unsplash.com/photo-1563770660941-20978e870e26?w=600&auto=format&fit=crop&q=80",
  "Audio & Media": "https://images.unsplash.com/photo-1539185441755-769473a23570?w=600&auto=format&fit=crop&q=80"
};

const normalizeProduct = (p) => {
  if (!p) return p;
  const isDigital = (p.productType || p.type || "").toUpperCase() === "DIGITAL";
  const cat = p.categoryName || p.category || (isDigital ? "Developer Software" : "Hardware & Security");
  const fallbackImg = defaultImages[cat] || (isDigital 
    ? "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=600&auto=format&fit=crop&q=80" 
    : "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&auto=format&fit=crop&q=80");
  
  return {
    ...p,
    id: p.id ? String(p.id) : `prod_${Date.now()}`,
    title: p.title || p.name || "Untitled Product",
    name: p.name || p.title || "Untitled Product",
    type: isDigital ? "DIGITAL" : "PHYSICAL",
    productType: isDigital ? "DIGITAL" : "PHYSICAL",
    category: cat,
    categoryName: cat,
    price: typeof p.price === "number" ? p.price : parseFloat(p.price) || 0,
    image: p.image || fallbackImg,
    status: p.status || "PUBLISHED",
    inStock: p.status ? p.status === "PUBLISHED" : (p.inStock !== false),
    rating: p.rating || 4.8,
    ratingCount: p.ratingCount || 36,
    specs: p.specs || (isDigital ? {
      format: p.fileType || "PDF / ZIP",
      fileSize: p.fileSize ? `${(p.fileSize / 1048576).toFixed(1)} MB` : "24 MB",
      version: p.fileVersion || "v1.0.0",
      compatibility: "Cross-Platform, Cloud Native",
      license: "Single Developer License"
    } : {
      weight: p.physicalWeight ? `${p.physicalWeight} kg` : (p.weight ? `${p.weight} kg` : "45g"),
      dimensions: p.physicalDimensions || (p.length ? `${p.length}x${p.width}x${p.height} cm` : "45 x 18 x 5 mm"),
      sku: p.sku || p.physicalSku || "BV-SKU-001",
      origin: "Certified Enterprise Fab",
      warranty: "2-Year Manufacturer Warranty"
    }),
    reviews: p.reviews || [
      { id: "r1", author: "Verified Engineer", rating: 5, text: "Robust build and excellent performance in production.", date: "2026-08-28" }
    ],
    faq: p.faq || [
      { q: "What is included with this product?", a: "Access to complete artifacts, cryptographic verification keys, and continuous maintenance updates." }
    ]
  };
};

export const productService = {
  // Public Catalog: Queries published products with filtering, search, and sorting
  getProducts: async (filters = {}) => {
    const params = new URLSearchParams();
    if (filters.search) params.append("search", filters.search);
    if (filters.category && filters.category !== "All") params.append("category", filters.category);
    if (filters.type) params.append("type", filters.type);
    
    if (filters.minPrice !== undefined) {
      params.append("minPrice", filters.minPrice.toString());
    }
    if (filters.maxPrice !== undefined) {
      params.append("maxPrice", filters.maxPrice.toString());
    }
    if (filters.minRating !== undefined) {
      params.append("minRating", filters.minRating.toString());
    }
    if (filters.inStockOnly) {
      params.append("inStockOnly", "true");
    }
    if (filters.sortBy) {
      params.append("sortBy", filters.sortBy);
    }

    const queryStr = params.toString() ? `?${params.toString()}` : "";
    const res = await apiClient.get(`/products${queryStr}`);
    if (Array.isArray(res)) {
      return res.map(normalizeProduct);
    }
    return res;
  },

  // Public Catalog: Retrieves specific published product details
  getProductById: async (id) => {
    const res = await apiClient.get(`/products/${id}`);
    return normalizeProduct(res);
  },

  // --- VENDOR OPERATIONS (Approved Vendors Only) ---

  // Vendor: Get all products owned by authenticated vendor
  getMyProducts: async (status) => {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";
    const res = await apiClient.get(`/products/vendor/my-products${query}`);
    if (Array.isArray(res)) {
      return res.map(normalizeProduct);
    }
    return res;
  },

  // Vendor: Get single product owned by authenticated vendor
  getVendorProductById: async (id) => {
    const res = await apiClient.get(`/products/vendor/${id}`);
    return normalizeProduct(res);
  },

  // Vendor: Create product (direct publish or draft)
  createVendorProduct: async (productData, publish = false) => {
    const query = `?publish=${publish ? "true" : "false"}`;
    const res = await apiClient.post(`/products/vendor${query}`, productData);
    return normalizeProduct(res);
  },

  // Vendor: Update own product
  updateVendorProduct: async (id, productData) => {
    const res = await apiClient.put(`/products/vendor/${id}`, productData);
    return normalizeProduct(res);
  },

  // Vendor: Publish own draft product
  publishVendorProduct: async (id) => {
    const res = await apiClient.post(`/products/vendor/${id}/publish`);
    return normalizeProduct(res);
  },

  // Vendor: Deactivate own product
  deactivateVendorProduct: async (id) => {
    const res = await apiClient.post(`/products/vendor/${id}/deactivate`);
    return normalizeProduct(res);
  },

  // --- ADMIN MODERATION OPERATIONS ---

  // Admin: Get all products across all vendors and statuses
  getAllProductsAdmin: async (status) => {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";
    const res = await apiClient.get(`/products/admin/all${query}`);
    if (Array.isArray(res)) {
      return res.map(normalizeProduct);
    }
    return res;
  },

  // Admin: Get specific product by ID
  getProductByIdAdmin: async (id) => {
    const res = await apiClient.get(`/products/admin/${id}`);
    return normalizeProduct(res);
  },

  // Admin: Take down / deactivate a product with reason
  takedownProduct: async (id, reason) => {
    const res = await apiClient.post(`/products/admin/${id}/takedown`, { reason });
    return normalizeProduct(res);
  },

  // Admin: Reactivate a deactivated product
  reactivateProduct: async (id) => {
    const res = await apiClient.post(`/products/admin/${id}/reactivate`);
    return normalizeProduct(res);
  },

  // Retrieves lists of distinct categories from catalog
  getCategories: async () => {
    try {
      const response = await apiClient.get("/categories");
      if (Array.isArray(response)) {
        const catNames = response.map(c => typeof c === "string" ? c : c.name).filter(Boolean);
        if (catNames.length > 0) return ["All", ...new Set(catNames)];
      }
    } catch {
      // Fallback
    }
    try {
      const products = await apiClient.get("/products");
      const cats = Array.isArray(products) ? products.map(p => p.categoryName || p.category).filter(Boolean) : [];
      return ["All", ...new Set(cats)];
    } catch {
      return ["All", "E-Books", "Developer Software", "Hardware & Security", "Audio & Media"];
    }
  },

  // Retrieves related products
  getRelatedProducts: async (productId, limit = 4) => {
    try {
      const current = await productService.getProductById(productId);
      const products = await productService.getProducts();
      return (Array.isArray(products) ? products : [])
        .filter(p => p.id !== productId && ((p.categoryName || p.category) === (current.categoryName || current.category) || p.productType === current.productType))
        .slice(0, limit);
    } catch {
      return [];
    }
  },

  // Retrieves raw category entities [{ id, name, slug, description }]
  getCategoriesList: async () => {
    const res = await apiClient.get("/categories");
    return Array.isArray(res) ? res : [];
  }
};

export default productService;
