import { request } from "./request";

export interface ExamAnalysisItem {
  id: number;
  userId: number;
  title: string;
  examType: string;
  fileNames: string;
  keyPoints: string | null;
  questionDistribution: string | null;
  suggestions: string | null;
  mindMapId: number | null;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface ExamAnalysisCreateReq {
  title: string;
  examType: string;
  fileNames: string;
}

export async function getExamAnalysisList(): Promise<ExamAnalysisItem[]> {
  const res = await request.get("/api/exam-analysis/list");
  return res.data?.data ?? res.data ?? [];
}

export async function getExamAnalysisDetail(id: number): Promise<ExamAnalysisItem> {
  const res = await request.get(`/api/exam-analysis/${id}`);
  return res.data?.data ?? res.data;
}

export async function createExamAnalysis(
  title: string,
  examType: string,
  fileNames: string,
  files: File[],
): Promise<number> {
  const fd = new FormData();
  fd.append("title", title);
  fd.append("examType", examType);
  fd.append("fileNames", fileNames);
  files.forEach((file) => {
    fd.append("files", file);
  });
  const res = await request.post("/api/exam-analysis/create", fd, {
    headers: { "Content-Type": "multipart/form-data" },
    timeout: 120000,
  });
  return res.data?.data ?? res.data;
}

export async function updateExamAnalysis(
  id: number,
  data: Partial<ExamAnalysisItem>,
): Promise<void> {
  await request.put(`/api/exam-analysis/${id}`, data);
}

export async function deleteExamAnalysis(id: number): Promise<void> {
  await request.delete(`/api/exam-analysis/${id}`);
}

export async function analyzeExam(id: number): Promise<ExamAnalysisItem> {
  const res = await request.post(`/api/exam-analysis/${id}/analyze`, {}, { timeout: 120000 });
  return res.data?.data ?? res.data;
}

export async function generateSuggestions(id: number): Promise<ExamAnalysisItem> {
  const res = await request.post(`/api/exam-analysis/${id}/suggestions`, {}, { timeout: 120000 });
  return res.data?.data ?? res.data;
}
