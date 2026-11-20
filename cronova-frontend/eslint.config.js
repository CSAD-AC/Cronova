// ESLint 10 flat config（create-vue 标准写法）
// 与 prettier 分工：prettier 管格式，eslint 只管代码问题，格式类规则全部关掉
//
// 关于存量规则等级：首轮接入时全仓有 164 个问题（any 117、未使用变量 33、
// 单文件组件名单词 12、空对象类型 2）。三条噪音最大的规则先降成 warn，
// 保证 CI 能绿、不阻塞开发；后续逐个修完再把等级改回 error（见 lint:strict）。
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.{ts,mts,tsx,vue}'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/dist-ssr/**', '**/coverage/**', '**/node_modules/**'],
  },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  {
    name: 'app/legacy-tolerance',
    rules: {
      // 存量多为接口返回体的 any，类型化要连着改调用方，留作后续
      '@typescript-eslint/no-explicit-any': 'warn',
      // catch (error) {} 这类故意不用的参数不该报，其余未使用变量仍是 warn
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', caughtErrors: 'none', varsIgnorePattern: '^_' },
      ],
      // views/Tasks/List.vue 这类按目录分组命名的路由页，不需要多词
      'vue/multi-word-component-names': 'off',
    },
  },
  skipFormatting,
)
