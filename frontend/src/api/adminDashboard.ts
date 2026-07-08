import { adminRequest as request } from "./adminRequest";

export const getStats = () => request.get("/api/admin/dashboard/stats");
export const getTrends = () => request.get("/api/admin/dashboard/trends");
export const getTypeDistribution = () => request.get("/api/admin/dashboard/types");
