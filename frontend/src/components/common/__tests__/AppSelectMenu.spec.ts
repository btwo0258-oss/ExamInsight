import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import AppSelectMenu from '@/components/common/AppSelectMenu.vue'

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  document.body.innerHTML = ''
})

describe('AppSelectMenu', () => {
  it('renders the current value and emits the selected option', async () => {
    wrapper = mount(AppSelectMenu, {
      attachTo: document.body,
      props: {
        modelValue: 'student',
        options: [
          { value: 'student', label: '学生' },
          { value: 'teacher', label: '教师' },
        ],
        ariaLabel: '选择演示受众',
      },
    })

    expect(wrapper.get('[role="combobox"]').text()).toContain('学生')
    await wrapper.get('[role="combobox"]').trigger('click')

    const options = Array.from(document.body.querySelectorAll<HTMLButtonElement>('[role="option"]'))
    expect(options.map((option) => option.textContent)).toEqual(expect.arrayContaining(['学生', '教师']))
    options.find((option) => option.textContent?.includes('教师'))?.click()
    await nextTick()

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['teacher'])
    expect(document.body.querySelector('[role="listbox"]')).toBeNull()
  })

  it('emits create from the optional footer action', async () => {
    wrapper = mount(AppSelectMenu, {
      attachTo: document.body,
      props: {
        modelValue: null,
        options: [{ value: null, label: '无', icon: 'close' }],
        createLabel: '新建知识库',
      },
    })

    await wrapper.get('[role="combobox"]').trigger('click')
    const createButton = Array.from(document.body.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('新建知识库'))
    expect(createButton).toBeTruthy()
    createButton?.click()

    expect(wrapper.emitted('create')).toHaveLength(1)
  })
})
