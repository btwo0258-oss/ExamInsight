import { request, sessionFetch } from "./request";
import { downloadBlob } from "@/utils/download";

export interface ResourceItem {
  id: number;
  title: string;
  category: string;
  year: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  filePath: string;
  description: string;
  downloadCount: number;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface UserResourceItem {
  id: number;
  userId: number;
  resourceId: number;
  knowledgeBaseId: number;
  createTime: string;
}

export async function getResourceList(params?: {
  category?: string;
  year?: number;
}): Promise<ResourceItem[]> {
  const res = await request.get("/api/resource/list", { params });
  return res.data?.data ?? res.data ?? [];
}

export async function getResourceDetail(id: number): Promise<ResourceItem> {
  const res = await request.get(`/api/resource/${id}`);
  return res.data?.data ?? res.data;
}

export async function downloadResource(id: number): Promise<void> {
  const baseURL = import.meta.env.VITE_API_BASE_URL ?? "";
  const url = `${baseURL}/api/resource/download/${id}`;

  try {
    const response = await sessionFetch(url);

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error("未登录或登录已过期，请重新登录");
      }
      if (response.status === 404) {
        throw new Error("文件不存在，请联系管理员重新上传");
      }
      const errorText = await response.text();
      throw new Error(`下载失败 (${response.status}): ${errorText || "请稍后重试"}`);
    }

    const contentDisposition = response.headers.get("Content-Disposition");
    let filename = "download";
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*=UTF-8''(.+)/);
      if (match?.[1]) {
        filename = decodeURIComponent(match[1]);
      } else {
        const match2 = contentDisposition.match(/filename="?(.+?)"?/);
        if (match2?.[1]) {
          filename = decodeURIComponent(match2[1]);
        }
      }
    }

    const blob = await response.blob();
    downloadBlob(blob, filename);
  } catch (error) {
    console.error("Download failed:", error);
    throw error;
  }
}

export async function addToKb(resourceId: number, knowledgeBaseId: number): Promise<void> {
  await request.post("/api/resource/add-to-kb", { resourceId, kbId: knowledgeBaseId });
}

export async function moveToKb(resourceId: number, knowledgeBaseId: number): Promise<void> {
  await request.post("/api/resource/move-to-kb", { resourceId, kbId: knowledgeBaseId });
}

export async function removeFromKb(resourceId: number): Promise<void> {
  await request.post("/api/resource/remove-from-kb", { resourceId });
}

export async function getMyResources(): Promise<UserResourceItem[]> {
  const res = await request.get("/api/resource/my-resources");
  const items = (res.data?.data ?? res.data ?? []) as Array<UserResourceItem & { kbId?: number }>;
  return items.map((item) => ({ ...item, knowledgeBaseId: item.knowledgeBaseId ?? item.kbId ?? 0 }));
}

export async function isResourceAdded(resourceId: number): Promise<boolean> {
  const res = await request.get("/api/resource/is-added", { params: { resourceId } });
  return res.data?.data ?? res.data ?? false;
}
