import React, { createContext, useContext, useEffect, useState } from "react";
import { api } from "./api";

export interface UserSession {
  userId: string;
  nakamaUserId: string;
  displayName: string;
  role: string;
}

export interface WalletState {
  realCoins: number;
  gameCoins: number;
}

interface AuthContextType {
  user: UserSession | null;
  wallet: WalletState | null;
  token: string | null;
  loading: boolean;
  isAdmin: boolean;
  loginAs: (type: "PLAYER" | "ADMIN" | "CUSTOM", customId?: string, customName?: string) => Promise<void>;
  refreshWallet: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserSession | null>(null);
  const [wallet, setWallet] = useState<WalletState | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshWallet = async () => {
    try {
      const data = await api.getWallet();
      setWallet({
        realCoins: data.realCoins ?? 0,
        gameCoins: data.gameCoins ?? 0,
      });
    } catch {
      // Ignore wallet fetch errors if unauthenticated
    }
  };

  const loginAs = async (
    type: "PLAYER" | "ADMIN" | "CUSTOM",
    customId?: string,
    customName?: string
  ) => {
    let nakamaId = "00000000-0000-0000-0000-000000000001";
    let name = "ViperActual";
    let role = "PLAYER";

    if (type === "ADMIN") {
      nakamaId = "00000000-0000-0000-0000-000000000099";
      name = "QuartermasterAdmin";
      role = "ADMIN";
    } else if (type === "CUSTOM" && customId) {
      nakamaId = customId;
      name = customName || "Operative";
      role = "PLAYER";
    }

    try {
      const res = await api.mockLogin(nakamaId, name, role);
      localStorage.setItem("railbound_token", res.token);
      setToken(res.token);
      setUser(res.user);
      await refreshWallet();
    } catch (err) {
      console.error("Login failed", err);
    }
  };

  const logout = () => {
    localStorage.removeItem("railbound_token");
    setToken(null);
    setUser(null);
    setWallet(null);
  };

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem("railbound_token");
      if (storedToken) {
        setToken(storedToken);
        try {
          const u = await api.getMe();
          setUser(u);
          await refreshWallet();
          setLoading(false);
          return;
        } catch {
          localStorage.removeItem("railbound_token");
        }
      }
      
      // Auto-login as default demo player ViperActual if starting fresh
      await loginAs("PLAYER");
      setLoading(false);
    };

    initAuth();
  }, []);

  const isAdmin = user?.role === "ADMIN" || user?.role === "SUPER_ADMIN" || user?.role === "CONTENT_MANAGER";

  return (
    <AuthContext.Provider
      value={{
        user,
        wallet,
        token,
        loading,
        isAdmin,
        loginAs,
        refreshWallet,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
