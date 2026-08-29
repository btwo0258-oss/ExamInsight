import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, effectScope, reactive } from 'vue'
import { useLearningDrafts } from '../useLearningDrafts'
import { learningErrorMessage } from '@/utils/learningErrors'

const scopes: ReturnType<typeof effectScope>[] = []
beforeEach(() => { sessionStorage.clear(); vi.useFakeTimers() })
afterEach(() => { scopes.forEach(scope => scope.stop()); scopes.length = 0; vi.useRealTimers() })
function setup(save = vi.fn().mockResolvedValue({}), version = '1') {
  const scope = effectScope(); scopes.push(scope)
  return scope.run(() => {
    const form = reactive({ name: '', manual: '' })
    const drafts = useLearningDrafts({ storageKey: 'draft-user-project', values: computed(() => ({ target: { name: form.name }, sources: { manualScope: form.manual } })), version: () => version, save })
    drafts.initialize(() => {}, (section, value) => { if (section === 'target') form.name = String(value.name); else form.manual = String(value.manualScope) })
    return { form, drafts, save }
  })!
}
describe('learning draft persistence', () => {
  it('does not erase cached drafts if the initial project request failed', () => {
    sessionStorage.setItem('unloaded-project', '{"target":{"value":{"name":"离线草稿"},"version":"1"}}')
    const scope = effectScope(); scopes.push(scope)
    scope.run(() => {
      const drafts = useLearningDrafts({ storageKey: 'unloaded-project', values: computed(() => ({ target: {} })), version: () => '1', save: vi.fn() })
      drafts.dispose()
    })
    expect(sessionStorage.getItem('unloaded-project')).toContain('离线草稿')
  })
  it('coalesces typing and flushes all edited sections before leaving', async () => {
    const { form, drafts, save } = setup()
    form.name = '初稿'; form.name = '最新目标'; form.manual = '函数与数组'
    expect(save).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('draft-user-project')).toContain('最新目标')
    expect(await drafts.flush()).toBe(true)
    expect(save.mock.calls).toEqual([['target', { name: '最新目标' }], ['sources', { manualScope: '函数与数组' }]])
    expect(sessionStorage.getItem('draft-user-project')).toBeNull()
  })
  it('serializes writes and never overwrites typing with an old response', async () => {
    let finish!: () => void
    const save = vi.fn().mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve })).mockResolvedValue({})
    const { form, drafts } = setup(save)
    form.name = '旧输入'
    const flight = drafts.flush()
    form.name = '新输入'
    expect(save).toHaveBeenCalledTimes(1)
    finish(); await flight
    expect(save.mock.calls.map(args => args[1])).toEqual([{ name: '旧输入' }, { name: '新输入' }])
    expect(form.name).toBe('新输入')
  })
  it('restores unacknowledged drafts after failure/refresh and retries', async () => {
    const first = setup(vi.fn().mockRejectedValue({ response: { status: 500 } }))
    first.form.manual = '离开前刚输入的范围'
    expect(await first.drafts.flush()).toBe(false)
    first.drafts.dispose()
    const next = setup()
    expect(next.form.manual).toBe('离开前刚输入的范围')
    expect(await next.drafts.flush()).toBe(true)
    expect(next.save).toHaveBeenCalledWith('sources', { manualScope: '离开前刚输入的范围' })
  })
  it('does not replay an obsolete draft after upstream versions change', () => {
    const first = setup(); first.form.manual = '旧目标的范围'; first.drafts.dispose()
    const next = setup(undefined, '2')
    expect(next.form.manual).toBe('')
  })
  it('does not autosave server hydration or lose explicit cleared values', async () => {
    const { form, drafts, save } = setup()
    drafts.replace(() => { form.name = '服务端内容' })
    await vi.advanceTimersByTimeAsync(600)
    expect(save).not.toHaveBeenCalled()
    form.name = ''
    await drafts.flush()
    expect(save).toHaveBeenCalledWith('target', { name: '' })
  })
})
describe('learning error messages', () => {
  it('extracts useful Chinese validation text from both error envelopes', () => {
    expect(learningErrorMessage({ message: 'Request failed with status code 400', response: { status: 400, data: { message: '截止日期不能早于今天。' } } })).toBe('截止日期不能早于今天。')
    expect(learningErrorMessage({ response: { data: { error: { message: '资料暂时不可用。' } } } })).toBe('资料暂时不可用。')
  })
  it('does not expose raw diagnostics', () => {
    expect(learningErrorMessage(new Error('Request failed with status code 400'))).not.toContain('Request failed')
    expect(learningErrorMessage({ response: { status: 500, data: { message: 'SQL错误 password=secret' } } })).not.toContain('secret')
  })
})
