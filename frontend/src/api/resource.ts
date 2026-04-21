import { request } from "./request";

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
  kbId: number;
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
  const token = sessionStorage.getItem("llm.token") || localStorage.getItem("llm.token");
  const url = `${baseURL}/api/resource/download/${id}`;

  try {
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    if (!response.ok) {
      if (response.status === 401) {
        alert("未登录或登录已过期，请重新登录");
        return;
      }
      throw new Error("下载失败");
    }

    const contentDisposition = response.headers.get("Content-Disposition");
    let filename = "download";
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*=UTF-8''(.+)/);
      if (match) {
        filename = decodeURIComponent(match[1]);
      } else {
        const match2 = contentDisposition.match(/filename="?(.+?)"?/);
        if (match2) {
          filename = decodeURIComponent(match2[1]);
        }
      }
    }

    const blob = await response.blob();
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);
  } catch (error) {
    console.error("Download failed:", error);
    alert("下载失败，请稍后重试");
  }
}

export async function addToKb(resourceId: number, kbId: number): Promise<void> {
  await request.post("/api/resource/add-to-kb", { resourceId, kbId });
}

export async function moveToKb(resourceId: number, kbId: number): Promise<void> {
  await request.post("/api/resource/move-to-kb", { resourceId, kbId });
}

export async function removeFromKb(resourceId: number): Promise<void> {
  await request.post("/api/resource/remove-from-kb", { resourceId });
}

export async function getMyResources(): Promise<UserResourceItem[]> {
  const res = await request.get("/api/resource/my-resources");
  return res.data?.data ?? res.data ?? [];
}

export async function isResourceAdded(resourceId: number): Promise<boolean> {
  const res = await request.get("/api/resource/is-added", { params: { resourceId } });
  return res.data?.data ?? res.data ?? false;
}
