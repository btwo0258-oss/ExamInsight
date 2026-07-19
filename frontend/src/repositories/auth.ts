import { request, USER_KEY } from "@/api/request";
import { isMockDataSource } from "@/config/dataSource";

export type ApiUser = {
  id: number;
  username: string;
  nickname?: string | null;
  avatar?: string | null;
};

export type AuthResult = {
  token: string;
  user: ApiUser;
};

type RegisterRequest = { username: string; password: string; nickname?: string };
type UserSettings = { theme: string; defaultModel: string };
type MockUser = ApiUser & { password: string };

export interface AuthRepository {
  login(payload: { username: string; password: string }): Promise<AuthResult>;
  register(payload: RegisterRequest): Promise<AuthResult>;
  updateProfile(payload: { nickname: string }): Promise<ApiUser>;
  forgotPassword(username: string): Promise<{ message: string }>;
  getSettings(): Promise<UserSettings>;
  updateSettings(payload: Partial<UserSettings>): Promise<void>;
}

const MOCK_USERS_KEY = "examinsight.mock.v1.auth.users";
const MOCK_SETTINGS_KEY = "examinsight.ui.settings";

function loadMockUsers(): MockUser[] {
  const fallback: MockUser[] = [
    { id: 1, username: "admin", password: "123456", nickname: "Admin", avatar: null },
  ];
  try {
    const users = JSON.parse(sessionStorage.getItem(MOCK_USERS_KEY) || "[]") as MockUser[];
    if (users.length) return users;
  } catch {}
  sessionStorage.setItem(MOCK_USERS_KEY, JSON.stringify(fallback));
  return fallback;
}

function saveMockUsers(users: MockUser[]) {
  sessionStorage.setItem(MOCK_USERS_KEY, JSON.stringify(users));
}

function publicUser(user: MockUser): ApiUser {
  return {
    id: user.id,
    username: user.username,
    nickname: user.nickname ?? null,
    avatar: user.avatar ?? null,
  };
}

function normalizeAuth(payload: unknown): AuthResult {
  const wrapper = payload as { data?: Record<string, unknown>; code?: number; message?: string };
  const data = (wrapper?.data ?? payload) as Record<string, unknown>;
  if (!data?.token) throw new Error("Invalid auth response: no token");
  const nestedUser =
    data.user && typeof data.user === "object" ? (data.user as Record<string, unknown>) : data;
  return {
    token: String(data.token),
    user: {
      id: Number(nestedUser.id),
      username: String(nestedUser.username ?? ""),
      nickname:
        nestedUser.nickname === undefined || nestedUser.nickname === null
          ? null
          : String(nestedUser.nickname),
      avatar:
        nestedUser.avatar === undefined || nestedUser.avatar === null
          ? null
          : String(nestedUser.avatar),
    },
  };
}

const mockAuthRepository: AuthRepository = {
  async login(payload) {
    const user = loadMockUsers().find((item) => item.username === payload.username.trim());
    if (!user || user.password !== payload.password) throw new Error("账号或密码错误");
    return {
      token: `mock_${Date.now()}_${Math.random().toString(16).slice(2)}`,
      user: publicUser(user),
    };
  },
  async register(payload) {
    const username = payload.username.trim();
    const nickname = payload.nickname?.trim();
    if (!username || !nickname || !payload.password) throw new Error("请完整填写账号、昵称和密码");
    const users = loadMockUsers();
    if (users.some((item) => item.username === username)) throw new Error("账号已存在");
    const user: MockUser = {
      id: Date.now(),
      username,
      password: payload.password,
      nickname,
      avatar: null,
    };
    users.unshift(user);
    saveMockUsers(users);
    return {
      token: `mock_${Date.now()}_${Math.random().toString(16).slice(2)}`,
      user: publicUser(user),
    };
  },
  async updateProfile(payload) {
    const raw = sessionStorage.getItem(USER_KEY) ?? localStorage.getItem(USER_KEY);
    if (!raw) throw new Error("未登录");
    return { ...(JSON.parse(raw) as ApiUser), nickname: payload.nickname };
  },
  async forgotPassword() {
    return { message: "密码重置申请已提交，请等待管理员处理" };
  },
  async getSettings() {
    const fallback: UserSettings = {
      theme: localStorage.getItem("llm.theme") === "dark" ? "dark" : "light",
      defaultModel: "deepseek-chat",
    };
    try {
      return { ...fallback, ...JSON.parse(localStorage.getItem(MOCK_SETTINGS_KEY) || "{}") };
    } catch {
      return fallback;
    }
  },
  async updateSettings(payload) {
    const current = await this.getSettings();
    localStorage.setItem(MOCK_SETTINGS_KEY, JSON.stringify({ ...current, ...payload }));
  },
};

const apiAuthRepository: AuthRepository = {
  async login(payload) {
    return normalizeAuth((await request.post("/api/user/login", payload)).data);
  },
  async register(payload) {
    return normalizeAuth((await request.post("/api/user/register", payload)).data);
  },
  async updateProfile(payload) {
    await request.put("/api/user/update", payload);
    const response = await request.get("/api/user/info");
    const wrapper = response.data as { data?: Record<string, unknown> };
    const data = (wrapper?.data ?? response.data) as Record<string, unknown>;
    return {
      id: Number(data.id),
      username: String(data.username ?? ""),
      nickname: data.nickname === undefined ? null : String(data.nickname),
      avatar: data.avatar === undefined || data.avatar === null ? null : String(data.avatar),
    };
  },
  async forgotPassword(username) {
    return (await request.post("/api/user/forgot-password", { username })).data as {
      message: string;
    };
  },
  async getSettings() {
    const response = await request.get("/api/user/settings");
    const wrapper = response.data as { data?: UserSettings };
    return wrapper?.data ?? (response.data as UserSettings);
  },
  async updateSettings(payload) {
    await request.put("/api/user/settings", payload);
  },
};

export const authRepository = isMockDataSource ? mockAuthRepository : apiAuthRepository;
