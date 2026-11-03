import pluginVue from 'eslint-plugin-vue'
import { vueTsConfigs, withVueTs } from '@vue/eslint-config-typescript'

/**
 * T169: flat ESLint configuration.
 *
 * Vue essential rules plus the recommended TypeScript rules. Formatting is not checked here
 * (vue-tsc and the build own type errors); this configuration only finds real problems in
 * SFCs and TypeScript sources.
 */
export default withVueTs(
  [
    {
      ignores: [
        'node_modules/**',
        'dist/**',
        'coverage/**',
        'playwright-report/**',
        'test-results/**',
      ],
    },
  ],
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
)
