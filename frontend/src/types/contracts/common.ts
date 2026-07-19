export type EntityId = number;

export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
  requestId?: string;
  errorCode?: string;
};

export type PageResult<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
};

export type AsyncJobStatus = "pending" | "running" | "succeeded" | "failed" | "cancelled";

export type AsyncJob<T> = {
  jobId: string;
  status: AsyncJobStatus;
  progress?: number;
  result?: T;
  errorCode?: string;
  errorMessage?: string;
};
