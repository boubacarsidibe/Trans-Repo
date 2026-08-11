import axios, { type AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { AuthResponse } from "../types/api";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const TOKEN_KEY = "monitoring.token";
const REFRESH_TOKEN_KEY = "monitoring.refreshToken";
const USER_KEY = "monitoring.user";

export interface StoredUser {
	id: number;
	username: string;
	email: string;
	role: string;
}

export function saveSession(auth: AuthResponse) {
	localStorage.setItem(TOKEN_KEY, auth.token);
	localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
	localStorage.setItem(
		USER_KEY,
		JSON.stringify({ id: auth.id, username: auth.username, email: auth.email, role: auth.role }),
	);
}

export function clearSession() {
	localStorage.removeItem(TOKEN_KEY);
	localStorage.removeItem(REFRESH_TOKEN_KEY);
	localStorage.removeItem(USER_KEY);
}

export function getToken(): string | null {
	return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): StoredUser | null {
	const raw = localStorage.getItem(USER_KEY);
	return raw ? JSON.parse(raw) : null;
}

export const apiClient = axios.create({ baseURL: BASE_URL });

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
	const token = getToken();
	if (token) {
		config.headers.set("Authorization", `Bearer ${token}`);
	}
	return config;
});

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
	const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
	if (!refreshToken) {
		throw new Error("No refresh token available.");
	}

	const response = await axios.post<AuthResponse>(`${BASE_URL}/api/auth/refresh`, { refreshToken });
	saveSession(response.data);
	return response.data.token;
}

apiClient.interceptors.response.use(
	(response) => response,
	async (error: AxiosError) => {
		const originalRequest = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

		if (error.response?.status === 401 && originalRequest && !originalRequest._retried) {
			originalRequest._retried = true;
			try {
				refreshPromise ??= refreshAccessToken().finally(() => {
					refreshPromise = null;
				});
				const newToken = await refreshPromise;
				originalRequest.headers.set("Authorization", `Bearer ${newToken}`);
				return apiClient(originalRequest);
			} catch {
				clearSession();
				window.location.href = "/login";
			}
		}

		return Promise.reject(error);
	},
);
