import { knowledgeBaseRepository } from '@/repositories/knowledgeBase'
import type { KnowledgeBaseDto } from '@/types/contracts/library'

export type KnowledgeBase = KnowledgeBaseDto

export function getKnowledgeBases(): Promise<KnowledgeBase[]> {
  return knowledgeBaseRepository.list()
}

export function getKnowledgeBase(id: number): Promise<KnowledgeBase> {
  return knowledgeBaseRepository.get(id)
}

export function createKnowledgeBase(payload: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
  return knowledgeBaseRepository.create(payload)
}

export function updateKnowledgeBase(payload: KnowledgeBase): Promise<KnowledgeBase> {
  return knowledgeBaseRepository.update(payload)
}

export function deleteKnowledgeBase(id: number): Promise<void> {
  return knowledgeBaseRepository.remove(id)
}

export function getKnowledgeBaseByExamAnalysisId(examAnalysisId: number): Promise<KnowledgeBase | null> {
  return knowledgeBaseRepository.findByExamAnalysisId(examAnalysisId)
}
