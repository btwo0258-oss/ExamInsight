import { request, USER_KEY } from "./request";
import { mockEnabled } from "@/mock";

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

type MockUser = {
  id: number;
  username: string;
  password: string;
  nickname?: string;
  avatar?: string | null;
};

const MOCK_USERS_KEY = "llm.mock.users";

function loadMockUsers(): MockUser[] {
  const raw = localStorage.getItem(MOCK_USERS_KEY);
  if (!raw) {
    const seeded: MockUser[] = [
      { id: 1, username: "admin", password: "123456", nickname: "Admin", avatar: null },
    ];
    saveMockUsers(seeded);
    return seeded;
  }
  try {
    const parsed = JSON.parse(raw) as MockUser[];
    if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    const seeded: MockUser[] = [
      { id: 1, username: "admin", password: "123456", nickname: "Admin", avatar: null },
    ];
    saveMockUsers(seeded);
    return seeded;
  } catch {
    const seeded: MockUser[] = [
      { id: 1, username: "admin", password: "123456", nickname: "Admin", avatar: null },
    ];
    saveMockUsers(seeded);
    return seeded;
  }
}

function saveMockUsers(users: MockUser[]) {
  localStorage.setItem(MOCK_USERS_KEY, JSON.stringify(users));
}

function makeToken() {
  return `mock_${Date.now()}_${Math.random().toString(16).slice(2)}`;
}

function toApiUser(u: MockUser): ApiUser {
  return { id: u.id, username: u.username, nickname: u.nickname ?? null, avatar: u.avatar ?? null };
}

function mockLogin(payload: { username: string; password: string }): AuthResult {
  const u = payload.username.trim();
  const p = payload.password;
  if (!u || !p) throw new Error("请输入账号和密码");
  const users = loadMockUsers();
  const found = users.find((x) => x.username === u);
  if (!found || found.password !== p) throw new Error("账号或密码错误");
  return { token: makeToken(), user: toApiUser(found) };
}

function mockRegister(payload: {
  username: string;
  password: string;
  nickname?: string;
}): AuthResult {
  const u = payload.username.trim();
  const p = payload.password;
  const n = payload.nickname?.trim();
  if (!u) throw new Error("请输入账号");
  if (!n) throw new Error("请输入昵称");
  if (!p) throw new Error("请输入密码");

  const users = loadMockUsers();
  if (users.some((x) => x.username === u)) {
    throw new Error("账号已存在");
  }

  const next: MockUser = { id: Date.now(), username: u, password: p, nickname: n, avatar: null };
  users.unshift(next);
  saveMockUsers(users);
  return { token: makeToken(), user: toApiUser(next) };
}

function normalizeAuthPayload(resData: any): AuthResult {
  // 后端返回格式: { code, message, data: { id, username, nickname, avatar, token, lastLoginTime } }
  const d = resData?.data || resData;

  const token = d?.token;
  if (!token) {
    throw new Error("Invalid auth response: no token");
  }

  const user: ApiUser = {
    id: d.id,
    username: d.username,
    nickname: d.nickname ?? null,
    avatar: d.avatar ?? null,
  };

  return { token, user };
}

export async function login(payload: { username: string; password: string }): Promise<AuthResult> {
  // ✅ 这里必须加 .value ！！！
  if (mockEnabled.value) {
    return mockLogin(payload);
  }
  try {
    const res = await request.post("/api/user/login", payload);
    return normalizeAuthPayload(res.data);
  } catch (err) {
    const status = (err as { response?: { status?: number } })?.response?.status;
    if (status === 404) return mockLogin(payload);
    throw err;
  }
}

export async function register(payload: {
  username: string;
  password: string;
  nickname?: string;
}): Promise<AuthResult> {
  // ✅ 这里必须加 .value ！！！
  if (mockEnabled.value) {
    return mockRegister(payload);
  }
  try {
    const res = await request.post("/api/user/register", payload);
    return normalizeAuthPayload(res.data);
  } catch (err) {
    const status = (err as { response?: { status?: number } })?.response?.status;
    if (status === 404) return mockRegister(payload);
    throw err;
  }
}

export async function updateProfile(payload: { nickname: string }): Promise<ApiUser> {
  if (mockEnabled.value) {
    const userStr = sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY);
    if (!userStr) throw new Error("未登录");
    const user = JSON.parse(userStr) as ApiUser;
    user.nickname = payload.nickname;
    return user;
  }
  await request.put("/api/user/update", payload);
  const res = await request.get("/api/user/info");
  const payloadData = res.data;
  const dataObj = payloadData?.data || payloadData;
  return {
    id: dataObj?.id,
    username: dataObj?.username,
    nickname: dataObj?.nickname ?? null,
    avatar: dataObj?.avatar ?? null,
  } as ApiUser;
}

export async function forgotPassword(username: string): Promise<{ message: string }> {
  if (mockEnabled.value) {
    return { message: '密码重置申请已提交，请等待管理员处理' };
  }
  const res = await request.post("/api/user/forgot-password", { username });
  return res.data;
}

export async function getSettings(): Promise<{ theme: string; defaultModel: string }> {
  if (mockEnabled.value) {
    return { theme: 'light', defaultModel: 'deepseek-chat' };
  }
  const res = await request.get("/api/user/settings");
  return res.data?.data || res.data;
}

export async function updateSettings(payload: { theme?: string; defaultModel?: string }): Promise<void> {
  if (mockEnabled.value) {
    return;
  }
  await request.put("/api/user/settings", payload);
}