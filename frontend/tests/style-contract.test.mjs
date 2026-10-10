import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import test from 'node:test'

const css = readFileSync(new URL('../src/styles.css', import.meta.url), 'utf8')
const token = (name) => css.match(new RegExp(`--${name}:\\s*([^;]+);`))?.[1].trim()

test('颜色和形状遵守后台视觉规范', () => {
  assert.equal(token('accent'), '#2859b8')
  assert.equal(token('radius-field'), '0')
  assert.equal(token('radius-button'), '4px')
  assert.equal(token('radius-card'), '12px')
  assert.equal(token('control-height'), '36px')
  assert.doesNotMatch(css, /--green/)
  assert.equal(token('page'), '#e4e7ec')
  assert.equal(token('surface'), '#f0f2f5')
  assert.equal(token('field'), '#f7f8fa')
})

test('紧凑控件保留触屏尺寸和审核区下方间距', () => {
  assert.match(css, /\.copy-language select \{[^}]*width: 232px;[^}]*max-width: 100%/)
  assert.match(css, /\.generated-copy \.review-actions \{[^}]*margin: 20px 0 16px/)
  assert.match(css, /@media \(pointer: coarse\)[\s\S]*min-height: 44px/)
  assert.match(css, /@media \(max-width: 360px\)/)
  assert.match(css, /\.listing-config > \.review-actions \{ margin: 16px 0; \}/)
})

test('正文、次要文字和主按钮文字达到 AA 对比度', () => {
  const luminance = (hex) => {
    const rgb = hex.slice(1).match(/../g).map((part) => parseInt(part, 16) / 255)
    const linear = rgb.map((v) => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
  }
  for (const name of ['ink', 'muted', 'accent']) {
    for (const background of ['page', 'surface', 'field', 'soft']) {
      assert.ok((luminance(token(background)) + 0.05) / (luminance(token(name)) + 0.05) >= 4.5, `${name} on ${background}`)
    }
  }
  assert.ok(1.05 / (luminance(token('accent')) + 0.05) >= 4.5)
})
