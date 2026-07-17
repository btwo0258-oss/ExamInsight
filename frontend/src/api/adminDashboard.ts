import { adminRequest as request } from "./adminRequest";

export const getStats = () => request.get("/api/admin/dashboard/stats") as unknown as Promise<any[]>;
export const getTrends = () => request.get("/api/admin/dashboard/trends") as unknown as Promise<any[]>;
export const getTypeDistribution = () => request.get("/api/admin/dashboard/types") as unknown as Promise<any[]>;
