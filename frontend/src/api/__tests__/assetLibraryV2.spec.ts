import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({ request: requestMock }))

import { listAssets, uploadAsset } from '@/api/assetLibraryV2'

describe('V2 asset library API', () => {
  beforeEach(() => {
    Object.values(requestMock).forEach((mock) => mock.mockReset())
  })

  it('uses the frozen V2 list contract and forwards the cursor', async () => {
    requestMock.get.mockResolvedValue({ data: { items: [], nextCursor: null } })

    await listAssets('trash', 'next-page', 40)

    expect(requestMock.get).toHaveBeenCalledWith('/api/v2/assets', {
      params: { view: 'trash', limit: 40, cursor: 'next-page' },
    })
  })

  it('uploads exactly the part size returned by the backend before completing', async () => {
    requestMock.post
      .mockResolvedValueOnce({
        data: {
          uploadId: 'UPLOAD01',
          originalFilename: 'notes.txt',
          status: 'INITIATED',
          expectedSize: 10,
          uploadedBytes: 0,
          partSize: 6,
          expectedPartCount: 2,
          expiresAt: '2026-08-09T20:00:00Z',
        },
      })
      .mockResolvedValueOnce({
        data: {
          uploadId: 'UPLOAD01',
          status: 'COMPLETED',
          asset: { assetId: 'ASSET01', name: 'notes.txt', status: 'ACTIVE' },
          version: {
            versionId: 'VERSION01',
            versionNumber: 1,
            status: 'QUARANTINED',
            mimeType: 'text/plain',
            sizeBytes: 10,
            sha256: 'a'.repeat(64),
          },
          securityScanJob: { jobId: 'JOB01', status: 'QUEUED', stage: 'FILE_SECURITY_SCAN' },
        },
      })
    requestMock.put.mockResolvedValue({ data: {} })

    const progress: number[] = []
    const result = await uploadAsset(
      new File(['0123456789'], 'notes.txt', { type: 'text/plain' }),
      null,
      (value) => progress.push(value.percentage),
    )

    expect(requestMock.put).toHaveBeenCalledTimes(2)
    expect(requestMock.put.mock.calls[0]?.[0]).toBe('/api/v2/uploads/UPLOAD01/parts/1')
    expect((requestMock.put.mock.calls[0]?.[1] as Blob).size).toBe(6)
    expect((requestMock.put.mock.calls[1]?.[1] as Blob).size).toBe(4)
    expect(requestMock.post).toHaveBeenLastCalledWith('/api/v2/uploads/UPLOAD01/complete')
    expect(progress.at(-1)).toBe(100)
    expect(result.completion.asset.assetId).toBe('ASSET01')
  })

  it('keeps a completed upload successful when knowledge-base linking fails', async () => {
    requestMock.post
      .mockResolvedValueOnce({
        data: {
          uploadId: 'UPLOAD02', originalFilename: 'notes.txt', status: 'INITIATED',
          expectedSize: 5, uploadedBytes: 0, partSize: 8, expectedPartCount: 1,
          expiresAt: '2026-08-09T20:00:00Z',
        },
      })
      .mockResolvedValueOnce({
        data: {
          uploadId: 'UPLOAD02', status: 'COMPLETED',
          asset: { assetId: 'ASSET02', name: 'notes.txt', status: 'ACTIVE' },
          version: { versionId: 'VERSION02', versionNumber: 1, status: 'QUARANTINED', mimeType: 'text/plain', sizeBytes: 5, sha256: 'b'.repeat(64) },
          securityScanJob: { jobId: 'JOB02', status: 'QUEUED', stage: 'FILE_SECURITY_SCAN' },
        },
      })
    requestMock.put.mockImplementation((url: string) => {
      if (url.includes('/knowledge-bases/')) return Promise.reject(new Error('knowledge base removed'))
      return Promise.resolve({ data: {} })
    })

    const result = await uploadAsset(
      new File(['hello'], 'notes.txt', { type: 'text/plain' }),
      'KB01',
    )

    expect(result.completion.asset.assetId).toBe('ASSET02')
    expect(result.associationWarning).toContain('knowledge base removed')
    expect(requestMock.delete).not.toHaveBeenCalled()
  })
})
