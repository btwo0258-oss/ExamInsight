import { request } from "./request";

export type ModelInfo = {
  id?: string;
  name: string;
  label?: string;
  displayName?: string;
  description?: string;
  enabled?: boolean;
  capabilities?: Array<"chat" | "reasoning" | "vision">;
};

export async function listModels(): Promise<ModelInfo[]> {
  const res = await request.get("/api/config/model");
  return (res.data?.data ?? res.data) as ModelInfo[];
}
