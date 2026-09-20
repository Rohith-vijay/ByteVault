import React, { useState, useEffect, useMemo } from "react";
import { styled, useTheme } from "@mui/material/styles";
import { useSearchParams } from "react-router-dom";
import Box from "@mui/material/Box";
import Slider from "@mui/material/Slider";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import Drawer from "@mui/material/Drawer";
import {
  FilterList as FilterListIcon,
  Close as CloseIcon,
  Tune as TuneIcon,
  RestartAlt as ResetIcon
} from "@mui/icons-material";

import { Container } from "../components/primitives/Container";
import { Card } from "../components/primitives/Card";
import { Button } from "../components/primitives/Button";
import { Input } from "../components/primitives/Input";
import { Chip } from "../components/primitives/Chip";
import { Skeleton } from "../components/primitives/Skeleton";
import { EmptyState } from "../components/primitives/EmptyState";
import { Rating } from "../components/primitives/Rating";
import { ProductCard } from "../features/products/components/ProductCard/ProductCard";
import { productService } from "../services/productService";
import { useCart } from "../store/CartContext";
import { useWishlist } from "../store/WishlistContext";

const CatalogHero = styled("div")(({ theme }) => ({
  backgroundColor: "#FFFFFF",
  borderBottom: `1px solid ${theme.palette.border.default}`,
  paddingTop: theme.spacing(12),
  paddingBottom: theme.spacing(12),
}));

const CatalogLayout = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "280px 1fr",
  gap: theme.spacing(8),
  paddingTop: theme.spacing(8),
  paddingBottom: theme.spacing(20),

  [theme.breakpoints.down("lg")]: {
    gridTemplateColumns: "1fr",
  },
}));

const CatalogProductsGrid = styled("div")(({ theme }) => ({
  display: "grid",
  gridTemplateColumns: "repeat(3, 1fr)",
  gap: theme.spacing(6),
  [theme.breakpoints.down("xl")]: {
    gridTemplateColumns: "repeat(2, 1fr)",
  },
  [theme.breakpoints.down("sm")]: {
    gridTemplateColumns: "1fr",
  },
}));

const SidebarContainer = styled("aside")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(6),

  [theme.breakpoints.down("lg")]: {
    display: "none",
  },
}));

const FilterGroup = styled("div")(({ theme }) => ({
  borderBottom: `1px solid ${theme.palette.border.default}`,
  paddingBottom: theme.spacing(5),
  display: "flex",
  flexDirection: "column",
  gap: theme.spacing(3),
}));

const FilterTitle = styled("h4")(({ theme }) => ({
  margin: 0,
  fontSize: "13px",
  fontWeight: 700,
  textTransform: "uppercase",
  letterSpacing: "0.05em",
  color: theme.palette.text.primary,
}));

const TypeToggleGroup = styled("div")(({ theme }) => ({
  display: "flex",
  borderRadius: theme.radius.md,
  backgroundColor: theme.palette.background.elevated,
  padding: "4px",
  gap: "4px",
}));

const TypeToggleBtn = styled("button", {
  shouldForwardProp: (prop) => prop !== "active",
})(({ theme, active }) => ({
  flex: 1,
  padding: "6px 12px",
  fontSize: "12px",
  fontWeight: 600,
  borderRadius: theme.radius.sm,
  border: "none",
  cursor: "pointer",
  backgroundColor: active ? theme.palette.background.paper : "transparent",
  color: active ? theme.palette.text.primary : theme.palette.text.secondary,
  boxShadow: active ? "0 1px 3px rgba(0,0,0,0.08)" : "none",
  transition: "all 0.15s ease",
  "&:hover": {
    color: theme.palette.text.primary,
  }
}));

const TopBar = styled("div")(({ theme }) => ({
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  marginBottom: theme.spacing(6),
  gap: theme.spacing(4),
  flexWrap: "wrap",
}));

const ActiveChipsRow = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  gap: theme.spacing(2),
  flexWrap: "wrap",
  marginBottom: theme.spacing(6),
}));

export const Catalog = () => {
  const theme = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();
  const { addItem } = useCart();
  const { toggleWishlist, isWishlisted } = useWishlist();

  const getInitialParam = (key, fallback) => searchParams.get(key) || fallback;

  const [localSearch, setLocalSearch] = useState(() => getInitialParam("search", ""));
  const [search, setSearch] = useState(() => getInitialParam("search", ""));
  const [category, setCategory] = useState(() => getInitialParam("category", "All"));
  const [type, setType] = useState(() => getInitialParam("type", "ALL").toUpperCase());
  const [minRating, setMinRating] = useState(() => parseInt(getInitialParam("minRating", "0"), 10));
  const [inStockOnly, setInStockOnly] = useState(() => getInitialParam("inStockOnly", "false") === "true");
  const [sortBy, setSortBy] = useState(() => getInitialParam("sortBy", "trending"));
  
  const [priceRange, setPriceRange] = useState(() => {
    const min = parseInt(getInitialParam("minPrice", "0"), 10);
    const max = parseInt(getInitialParam("maxPrice", "50000"), 10);
    return [min, max];
  });

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(localSearch);
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch]);

  useEffect(() => {
    const nextParams = {};
    if (search) nextParams.search = search;
    if (category && category !== "All") nextParams.category = category;
    if (type && type !== "ALL") nextParams.type = type.toLowerCase();
    if (priceRange[0] > 0) nextParams.minPrice = priceRange[0].toString();
    if (priceRange[1] < 50000) nextParams.maxPrice = priceRange[1].toString();
    if (minRating > 0) nextParams.minRating = minRating.toString();
    if (inStockOnly) nextParams.inStockOnly = "true";
    if (sortBy && sortBy !== "trending") nextParams.sortBy = sortBy;

    setSearchParams(nextParams);
  }, [search, category, type, priceRange, minRating, inStockOnly, sortBy, setSearchParams]);

  useEffect(() => {
    const fetchCatalogData = async () => {
      setLoading(true);
      try {
        const [prodList, catList] = await Promise.all([
          productService.getProducts(),
          productService.getCategories(),
        ]);
        setProducts(prodList);
        setCategories(Array.from(new Set(catList)));
      } catch (err) {
        console.error("Catalog fetch failed", err);
      } finally {
        setLoading(false);
      }
    };
    fetchCatalogData();
  }, []);

  const filteredProducts = useMemo(() => {
    return products.filter((p) => {
      if (search && !p.title.toLowerCase().includes(search.toLowerCase()) && !p.category?.toLowerCase().includes(search.toLowerCase())) {
        return false;
      }
      if (category && category !== "All" && p.category !== category) {
        return false;
      }
      if (type && type !== "ALL" && p.type.toUpperCase() !== type) {
        return false;
      }
      if (p.price < priceRange[0] || p.price > priceRange[1]) {
        return false;
      }
      if (minRating > 0 && (p.rating || 0) < minRating) {
        return false;
      }
      if (inStockOnly && !p.inStock) {
        return false;
      }
      return true;
    }).sort((a, b) => {
      if (sortBy === "price_asc") return a.price - b.price;
      if (sortBy === "price_desc") return b.price - a.price;
      if (sortBy === "rating") return (b.rating || 0) - (a.rating || 0);
      if (sortBy === "newest") return b.id.localeCompare(a.id);
      return 0; // Default trending
    });
  }, [products, search, category, type, priceRange, minRating, inStockOnly, sortBy]);

  const handleResetFilters = () => {
    setLocalSearch("");
    setSearch("");
    setCategory("All");
    setType("ALL");
    setPriceRange([0, 50000]);
    setMinRating(0);
    setInStockOnly(false);
    setSortBy("trending");
  };

  const hasActiveFilters = 
    Boolean(search) || 
    category !== "All" || 
    type !== "ALL" || 
    priceRange[0] > 0 || 
    priceRange[1] < 50000 || 
    minRating > 0 || 
    inStockOnly;

  const renderFilterControls = () => (
    <Box display="flex" flexDirection="column" gap={5}>
      {/* 1. Type Segment */}
      <FilterGroup>
        <FilterTitle>Product Type</FilterTitle>
        <TypeToggleGroup>
          <TypeToggleBtn active={type === "ALL"} onClick={() => setType("ALL")}>All</TypeToggleBtn>
          <TypeToggleBtn active={type === "DIGITAL"} onClick={() => setType("DIGITAL")}>Digital</TypeToggleBtn>
          <TypeToggleBtn active={type === "PHYSICAL"} onClick={() => setType("PHYSICAL")}>Gear</TypeToggleBtn>
        </TypeToggleGroup>
      </FilterGroup>

      {/* 2. Categories */}
      <FilterGroup>
        <FilterTitle>Category</FilterTitle>
        <Box display="flex" flexDirection="column" gap={1}>
          {categories.map((cat) => (
            <Box
              key={cat}
              onClick={() => setCategory(cat)}
              style={{
                padding: "6px 10px",
                borderRadius: "8px",
                fontSize: "13px",
                fontWeight: category === cat ? 600 : 400,
                color: category === cat ? theme.palette.primary.main : theme.palette.text.secondary,
                backgroundColor: category === cat ? theme.palette.primary.soft : "transparent",
                cursor: "pointer",
                transition: "all 0.15s ease",
              }}
            >
              {cat}
            </Box>
          ))}
        </Box>
      </FilterGroup>

      {/* 3. Price Range Slider */}
      <FilterGroup>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <FilterTitle>Price Range</FilterTitle>
          <span style={{ fontSize: "12px", fontWeight: 600, color: theme.palette.text.primary }}>
            ₹{priceRange[0]} — ₹{priceRange[1].toLocaleString()}
          </span>
        </Box>
        <Slider
          value={priceRange}
          onChange={(e, val) => setPriceRange(val)}
          valueLabelDisplay="auto"
          valueLabelFormat={(v) => `₹${v.toLocaleString()}`}
          min={0}
          max={50000}
          step={100}
          sx={{
            color: theme.palette.primary.main,
            "& .MuiSlider-thumb": {
              boxShadow: "0 2px 6px rgba(0,0,0,0.15)",
            }
          }}
        />
      </FilterGroup>

      {/* 4. Minimum Rating */}
      <FilterGroup>
        <FilterTitle>Customer Rating</FilterTitle>
        <Box display="flex" flexDirection="column" gap={1.5}>
          {[4, 3, 2].map((r) => (
            <Box
              key={r}
              onClick={() => setMinRating(minRating === r ? 0 : r)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                cursor: "pointer",
                padding: "4px 8px",
                borderRadius: "6px",
                backgroundColor: minRating === r ? theme.palette.primary.soft : "transparent",
              }}
            >
              <Rating value={r} size="xs" />
              <span style={{ fontSize: "12px", color: theme.palette.text.secondary }}>& up</span>
            </Box>
          ))}
        </Box>
      </FilterGroup>

      {/* 5. In-Stock Switch */}
      <FilterGroup>
        <FormControlLabel
          control={
            <Checkbox
              checked={inStockOnly}
              onChange={(e) => setInStockOnly(e.target.checked)}
              sx={{ color: theme.palette.primary.main, "&.Mui-checked": { color: theme.palette.primary.main } }}
            />
          }
          label={<span style={{ fontSize: "13px", fontWeight: 500 }}>In-Stock Ready</span>}
        />
      </FilterGroup>

      {hasActiveFilters && (
        <Button 
          variant="outline" 
          size="sm" 
          fullWidth 
          onClick={handleResetFilters}
          leftIcon={<ResetIcon style={{ fontSize: "16px" }} />}
        >
          Reset All Filters
        </Button>
      )}
    </Box>
  );

  return (
    <>
      <CatalogHero>
        <Container maxWidth="xxl">
          <Box mb={2}>
            <Chip label="DEVELOPER MARKETPLACE" color="primary" variant="filled" uppercase />
          </Box>
          <h1 style={{ fontSize: "32px", fontWeight: 800, margin: "0 0 8px 0", color: theme.palette.text.primary, letterSpacing: "-0.02em" }}>
            Explore Verified Engineering Assets & Gear
          </h1>
          <p style={{ margin: 0, fontSize: "15px", color: theme.palette.text.secondary, maxWidth: "680px" }}>
            Browse production-ready software blueprints, UI kits, developer architectures, and tactile desk hardware.
          </p>
        </Container>
      </CatalogHero>

      <Container maxWidth="xxl">
        <CatalogLayout>
          {/* Desktop Filter Sidebar */}
          <SidebarContainer>
            <Card padding={6} radius="lg" elevation="subtle">
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <Box display="flex" alignItems="center" gap={1}>
                  <TuneIcon style={{ fontSize: "18px", color: theme.palette.text.primary }} />
                  <span style={{ fontSize: "14px", fontWeight: 700 }}>Filter Assets</span>
                </Box>
                {hasActiveFilters && (
                  <span 
                    onClick={handleResetFilters} 
                    style={{ fontSize: "11px", color: theme.palette.primary.main, cursor: "pointer", fontWeight: 600 }}
                  >
                    Clear All
                  </span>
                )}
              </Box>
              {renderFilterControls()}
            </Card>
          </SidebarContainer>

          {/* Catalog Content Area */}
          <div>
            <TopBar>
              <Box display="flex" alignItems="center" gap={3} flexGrow={1} maxWidth={{ xs: "100%", md: "400px" }}>
                <Input
                  fullWidth
                  placeholder="Search titles, architectures, gear..."
                  value={localSearch}
                  onChange={(e) => setLocalSearch(e.target.value)}
                />
              </Box>

              <Box display="flex" alignItems="center" gap={3} width={{ xs: "100%", sm: "auto" }} justifyContent="space-between">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setMobileFilterOpen(true)}
                  sx={{ display: { xs: "inline-flex", lg: "none" } }}
                  leftIcon={<FilterListIcon style={{ fontSize: "16px" }} />}
                >
                  Filters
                </Button>

                <Box display="flex" alignItems="center" gap={2}>
                  <span style={{ fontSize: "13px", color: theme.palette.text.secondary, whiteSpace: "nowrap" }}>Sort by:</span>
                  <Select
                    size="small"
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value)}
                    sx={{
                      fontSize: "13px",
                      borderRadius: "8px",
                      backgroundColor: theme.palette.background.paper,
                      minWidth: "160px"
                    }}
                  >
                    <MenuItem value="trending">Trending Picks</MenuItem>
                    <MenuItem value="newest">Newest Releases</MenuItem>
                    <MenuItem value="price_asc">Price: Low to High</MenuItem>
                    <MenuItem value="price_desc">Price: High to Low</MenuItem>
                    <MenuItem value="rating">Highest Rated</MenuItem>
                  </Select>
                </Box>
              </Box>
            </TopBar>

            {/* Active Filters Chips */}
            {hasActiveFilters && (
              <ActiveChipsRow>
                <span style={{ fontSize: "12px", color: theme.palette.text.muted }}>Active filters:</span>
                {search && (
                  <Chip label={`"${search}"`} onDelete={() => { setLocalSearch(""); setSearch(""); }} />
                )}
                {category !== "All" && (
                  <Chip label={category} onDelete={() => setCategory("All")} />
                )}
                {type !== "ALL" && (
                  <Chip label={type === "DIGITAL" ? "Digital Only" : "Gear Only"} onDelete={() => setType("ALL")} />
                )}
                {(priceRange[0] > 0 || priceRange[1] < 50000) && (
                  <Chip label={`₹${priceRange[0]} - ₹${priceRange[1].toLocaleString()}`} onDelete={() => setPriceRange([0, 50000])} />
                )}
                {minRating > 0 && (
                  <Chip label={`${minRating}★ & up`} onDelete={() => setMinRating(0)} />
                )}
                {inStockOnly && (
                  <Chip label="In Stock Only" onDelete={() => setInStockOnly(false)} />
                )}
              </ActiveChipsRow>
            )}

            <Box mb={4} display="flex" justifyContent="space-between" alignItems="center">
              <span style={{ fontSize: "13px", color: theme.palette.text.secondary, fontWeight: 500 }}>
                Showing <strong>{filteredProducts.length}</strong> verified products
              </span>
            </Box>

            {/* Grid or Empty State */}
            {loading ? (
              <CatalogProductsGrid>
                {[1, 2, 3, 4, 5, 6].map(i => (
                  <div key={i}>
                    <Skeleton variant="rectangular" height={220} radius="lg" style={{ marginBottom: "12px" }} />
                    <Skeleton variant="text" width="75%" style={{ marginBottom: "6px" }} />
                    <Skeleton variant="text" width="40%" />
                  </div>
                ))}
              </CatalogProductsGrid>
            ) : filteredProducts.length === 0 ? (
              <EmptyState
                title="No matching marketplace assets found"
                description="Try broadening your search query, adjusting your price slider, or clearing the active category filters."
                action={
                  <Button variant="primary" onClick={handleResetFilters}>
                    Clear All Filters
                  </Button>
                }
              />
            ) : (
              <CatalogProductsGrid>
                {filteredProducts.map((prod) => (
                  <ProductCard
                    key={prod.id}
                    product={prod}
                    onAddToCart={addItem}
                    onWishlistToggle={toggleWishlist}
                    isWishlisted={isWishlisted(prod.id)}
                  />
                ))}
              </CatalogProductsGrid>
            )}
          </div>
        </CatalogLayout>
      </Container>

      {/* Mobile Filters Drawer */}
      <Drawer
        anchor="left"
        open={mobileFilterOpen}
        onClose={() => setMobileFilterOpen(false)}
        PaperProps={{
          style: { width: "300px", padding: "24px" }
        }}
      >
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
          <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700 }}>Filter Assets</h3>
          <Button variant="ghost" size="sm" onClick={() => setMobileFilterOpen(false)} style={{ minWidth: "auto", padding: "4px" }}>
            <CloseIcon />
          </Button>
        </Box>
        {renderFilterControls()}
        <Box mt={4}>
          <Button variant="primary" fullWidth onClick={() => setMobileFilterOpen(false)}>
            Apply Filters ({filteredProducts.length})
          </Button>
        </Box>
      </Drawer>
    </>
  );
};

export default Catalog;
