import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import AppInput from '@/components/common/AppInput.vue'

function mountInput() {
  return mount(AppInput, {
    props: { mediaEnabled: true },
    global: {
      stubs: {
        ModelSwitch: true,
        AttachmentCard: true,
        ImageCaptureUploader: true,
        VoiceRecorder: true,
        ConfirmDialog: true,
      },
    },
  })
}

describe('AppInput', () => {
  it('clears immediately and restores the draft only when sending fails', async () => {
    const wrapper = mountInput()
    const textarea = wrapper.get('textarea')
    await textarea.setValue('立即清空这条消息')

    await wrapper.get('button[title="发送"]').trigger('click')

    expect((textarea.element as HTMLTextAreaElement).value).toBe('')
    const sendEvent = wrapper.emitted('send')?.[0]
    expect(sendEvent?.[0]).toBe('立即清空这条消息')

    const complete = sendEvent?.[2] as ((success?: boolean) => void) | undefined
    complete?.(false)
    await nextTick()
    expect((textarea.element as HTMLTextAreaElement).value).toBe('立即清空这条消息')

    wrapper.unmount()
  })
})
