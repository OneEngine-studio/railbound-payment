const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export interface ApiResponse<T = any> {
  data?: T;
  error?: {
    code: string;
    message: string;
    correlationId: string;
  };
}

export async function apiRequest<T = any>(
  endpoint: string,
  options: RequestInit & { idempotencyKey?: string } = {}
): Promise<T> {
  const token = typeof window !== "undefined" ? localStorage.getItem("railbound_token") : null;
  
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> || {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  if (options.idempotencyKey) {
    headers["Idempotency-Key"] = options.idempotencyKey;
  }

  const url = `${API_BASE_URL}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 204) {
    return {} as T;
  }

  const responseText = await response.text();
  let json: any = null;
  if (responseText) {
    try {
      json = JSON.parse(responseText);
    } catch {
      json = { raw: responseText };
    }
  }

  if (!response.ok) {
    const errorMsg = json?.message || `Request failed with status ${response.status}`;
    const err = new Error(errorMsg) as any;
    err.code = json?.code || "HTTP_ERROR";
    err.correlationId = json?.correlationId;
    err.status = response.status;
    throw err;
  }

  return json as T;
}

// API methods
export const api = {
  // Auth
  getMe: () => apiRequest("/auth/me"),
  logout: () => apiRequest("/auth/logout", { method: "POST" }),
  mockLogin: (nakamaUserId: string, displayName: string, role: string) =>
    apiRequest("/auth/mock-token", {
      method: "POST",
      body: JSON.stringify({ nakamaUserId, displayName, role }),
    }),

  // Wallet
  getWallet: () => apiRequest("/wallet/me"),
  getLedger: (page = 0, size = 20) => apiRequest(`/wallet/me/ledger?page=${page}&size=${size}`),

  // Bundles
  getBundles: () => apiRequest("/coin-bundles"),

  nakamaLogin: (token: string) =>
    apiRequest("/auth/nakama-login", {
      method: "POST",
      body: JSON.stringify({ token }),
    }),

  // Payments
  getPaymentProviders: () => apiRequest("/payments/providers"),
  getHostedForm: (orderId: string, checkoutToken: string, billing: Record<string, string>) =>
    apiRequest(`/payments/orders/${encodeURIComponent(orderId)}/hosted-form`, {
      method: "POST",
      body: JSON.stringify({ checkoutToken, billing }),
    }),
  createCheckoutSession: (bundleSku: string, provider = "mock", idempotencyKey?: string) =>
    apiRequest("/payments/checkout-sessions", {
      method: "POST",
      body: JSON.stringify({ bundleSku, provider }),
      idempotencyKey,
    }),
  getOrder: (orderId: string) => apiRequest(`/payments/orders/${orderId}`),
  getMyOrders: () => apiRequest("/payments/orders"),
  mockCompleteOrder: (orderId: string) =>
    apiRequest(`/payments/mock-complete/${orderId}`, { method: "POST" }),

  // Shop
  getShopItems: (category?: string) =>
    apiRequest(`/shop/items${category ? `?category=${category}` : ""}`),
  getShopItem: (sku: string) => apiRequest(`/shop/items/${sku}`),
  getCategories: () => apiRequest("/shop/categories"),
  purchaseItem: (itemSku: string, idempotencyKey?: string) =>
    apiRequest("/shop/purchases", {
      method: "POST",
      body: JSON.stringify({ itemSku }),
      idempotencyKey,
    }),
  getMyPurchases: () => apiRequest("/shop/purchases"),

  // Inventory
  getMyInventory: () => apiRequest("/inventory/me"),
  equipItem: (itemSku: string, slot: string, weaponSku?: string) =>
    apiRequest("/inventory/me/equip", {
      method: "POST",
      body: JSON.stringify({ itemSku, slot, weaponSku }),
    }),
  unequipItem: (slot: string, weaponSku?: string) =>
    apiRequest("/inventory/me/unequip", {
      method: "POST",
      body: JSON.stringify({ slot, weaponSku }),
    }),

  // News
  getNews: () => apiRequest("/news"),
  getAnnouncement: (id: string) => apiRequest(`/news/${id}`),

  // Game Sync
  gameSync: () => apiRequest("/game/sync"),

  // Admin
  adminGetSummary: () => apiRequest("/admin/analytics/summary"),
  adminGetOrders: () => apiRequest("/admin/orders"),
  adminGetPlayers: () => apiRequest("/admin/players"),
  adminGetAuditLogs: () => apiRequest("/admin/audit-logs"),
  adminGetRefunds: () => apiRequest("/admin/refunds"),
  adminCreateRefund: (orderId: string, reason: string) =>
    apiRequest("/admin/refunds", { method: "POST", body: JSON.stringify({ orderId, reason }) }),
  adminRetryRefund: (refundId: string) =>
    apiRequest(`/admin/refunds/${refundId}/retry`, { method: "POST" }),
  adminConfirmRefund: (refundId: string, providerRefundId?: string) =>
    apiRequest(`/admin/refunds/${refundId}/confirm`, {
      method: "POST",
      body: JSON.stringify({ providerRefundId: providerRefundId || "" }),
    }),
  adminGetOrdersInReview: () => apiRequest("/admin/payments/review"),
  adminReconcileOrder: (orderId: string) =>
    apiRequest(`/admin/payments/${orderId}/reconcile`, { method: "POST" }),
  adminResolveReview: (orderId: string, approve: boolean, note: string) =>
    apiRequest(`/admin/payments/${orderId}/review`, {
      method: "POST",
      body: JSON.stringify({ approve, note }),
    }),
  adminGetItems: () => apiRequest("/admin/items"),
  adminCreateItem: (item: any) =>
    apiRequest("/admin/items", { method: "POST", body: JSON.stringify(item) }),
  adminPublishItem: (sku: string) =>
    apiRequest(`/admin/items/${sku}/publish`, { method: "POST" }),
  adminArchiveItem: (sku: string) =>
    apiRequest(`/admin/items/${sku}/archive`, { method: "POST" }),
  adminGetAnnouncements: () => apiRequest("/admin/announcements"),
  adminCreateAnnouncement: (announcement: any) =>
    apiRequest("/admin/announcements", { method: "POST", body: JSON.stringify(announcement) }),
  adminPublishAnnouncement: (id: string) =>
    apiRequest(`/admin/announcements/${id}/publish`, { method: "POST" }),
};
