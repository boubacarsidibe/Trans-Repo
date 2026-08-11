import { createContext, useContext, useState, type ReactNode } from "react";
import { clearSession, getStoredUser, getToken, saveSession, type StoredUser } from "../api/client";
import { login as loginRequest } from "../api/endpoints";

interface AuthContextValue {
	user: StoredUser | null;
	isAuthenticated: boolean;
	login: (email: string, password: string) => Promise<void>;
	logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
	const [user, setUser] = useState<StoredUser | null>(() => (getToken() ? getStoredUser() : null));

	async function login(email: string, password: string) {
		const auth = await loginRequest(email, password);
		saveSession(auth);
		setUser({ id: auth.id, username: auth.username, email: auth.email, role: auth.role });
	}

	function logout() {
		clearSession();
		setUser(null);
	}

	return (
		<AuthContext.Provider value={{ user, isAuthenticated: user !== null, login, logout }}>
			{children}
		</AuthContext.Provider>
	);
}

export function useAuth(): AuthContextValue {
	const context = useContext(AuthContext);
	if (!context) {
		throw new Error("useAuth must be used within an AuthProvider.");
	}
	return context;
}
