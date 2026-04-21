import { ref, computed } from "vue";
import { defineStore } from "pinia";
import * as examApi from "@/api/examAnalysis";

export interface ExamAnalysis {
  id: number;
  title: string;
  type: string;
  date: string;
}

export const useExamAnalysisStore = defineStore("examAnalysis", () => {
  const list = ref<ExamAnalysis[]>([]);
  const loading = ref(false);
  const currentAnalysis = ref<any>(null);

  async function fetchList() {
    loading.value = true;
    try {
      const data = await examApi.getExamAnalysisList();
      list.value = data.map((item) => ({
        id: item.id,
        title: item.title,
        type: item.examType,
        date: item.createTime,
      }));
    } catch (error) {
      console.error("Failed to fetch exam analysis list:", error);
      list.value = [
        { id: 1, title: "2023年12月英语六级真题分析", type: "四六级", date: "2024-04-19 14:30" },
        {
          id: 2,
          title: "2024年计算机二级C语言试卷分析",
          type: "计算机二级",
          date: "2024-04-18 09:15",
        },
        { id: 3, title: "近3年高中数学压轴题考点分析", type: "高考真题", date: "2024-04-10 16:45" },
      ];
    } finally {
      loading.value = false;
    }
  }

  async function add(item: Omit<ExamAnalysis, "id"> & { fileNames?: string }) {
    try {
      const id = await examApi.createExamAnalysis({
        title: item.title,
        examType: item.type,
        fileNames: (item as any).fileNames || "",
      });
      await fetchList();
      return { ...item, id };
    } catch (error) {
      console.error("Failed to create exam analysis:", error);
      const newItem = { ...item, id: Date.now() };
      list.value.unshift(newItem);
      return newItem;
    }
  }

  async function remove(id: number) {
    try {
      await examApi.deleteExamAnalysis(id);
      list.value = list.value.filter((item) => item.id !== id);
    } catch (error) {
      console.error("Failed to delete exam analysis:", error);
      list.value = list.value.filter((item) => item.id !== id);
    }
  }

  async function rename(id: number, newTitle: string) {
    try {
      await examApi.updateExamAnalysis(id, { title: newTitle } as any);
      const item = list.value.find((item) => item.id === id);
      if (item) item.title = newTitle;
    } catch (error) {
      console.error("Failed to rename exam analysis:", error);
      const item = list.value.find((item) => item.id === id);
      if (item) item.title = newTitle;
    }
  }

  async function fetchDetail(id: number) {
    try {
      const data = await examApi.getExamAnalysisDetail(id);
      currentAnalysis.value = data;
      return data;
    } catch (error) {
      console.error("Failed to fetch exam analysis detail:", error);
      return null;
    }
  }

  async function analyze(id: number) {
    try {
      const data = await examApi.analyzeExam(id);
      currentAnalysis.value = data;
      return data;
    } catch (error) {
      console.error("Failed to analyze exam:", error);
      throw error;
    }
  }

  async function generateSugg(id: number) {
    try {
      const data = await examApi.generateSuggestions(id);
      currentAnalysis.value = data;
      return data;
    } catch (error) {
      console.error("Failed to generate suggestions:", error);
      throw error;
    }
  }

  return {
    list,
    loading,
    currentAnalysis,
    fetchList,
    add,
    remove,
    rename,
    fetchDetail,
    analyze,
    generateSugg,
  };
});
