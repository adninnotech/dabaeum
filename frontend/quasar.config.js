// Configuration for your app
// https://v2.quasar.dev/quasar-cli-vite/quasar-config-file

import { defineConfig } from '#q-app';

export default defineConfig((/* ctx */) => {
  return {
    // https://v2.quasar.dev/quasar-cli-vite/prefetch-feature
    // preFetch: true,

    // app boot file (/src/boot)
    // --> boot files are part of "main.js"
    // https://v2.quasar.dev/quasar-cli-vite/boot-files
    boot: ['api', 'datetime'],

    // https://v2.quasar.dev/quasar-cli-vite/quasar-config-file#css
    css: ['app.scss'],

    // https://github.com/quasarframework/quasar/tree/dev/extras
    extras: [
      // 'ionicons-v4',
      // 'mdi-v7',
      // 'fontawesome-v7',
      // 'eva-icons',
      // 'themify',
      // 'line-awesome',
      // 'roboto-font-latin-ext', // this or either 'roboto-font', NEVER both!

      'material-icons',
    ],

    // https://v2.quasar.dev/quasar-cli-vite/quasar-config-file#build
    build: {
      target: {
        // browser: 'baseline-widely-available',
        // node: 'node22'
      },

      // Options API 사용 필수. Quasar app-vite 기본값은 false라 data/methods가 인스턴스에 안 붙음.
      vueOptionsAPI: true,

      // https://v2.quasar.dev/quasar-cli-vite/page-routing-with-vue-router#filename-based-routing
      // filenameBasedRouting: true,

      vueRouterMode: 'history', // available values: 'hash', 'history'
      // vueRouterBase,

      // publicPath: '/',
      define: {
        // boolean으로 명시 (Vite define / plugin-vue features와 일치)
        __VUE_OPTIONS_API__: true,
      },
      // defineEnv: {}
      // ignorePublicFolder: true,
      // minify: false,
      // distDir

      // Vite prebundle(vue deps)에도 플래그를 구워넣어야 Options API가 실제로 동작함
      extendViteConf(viteConf) {
        viteConf.define = {
          ...viteConf.define,
          __VUE_OPTIONS_API__: true,
        };
        viteConf.optimizeDeps = viteConf.optimizeDeps || {};
        // Vite(Rolldown) 기준. 구버전 호환용 esbuildOptions도 함께 유지
        viteConf.optimizeDeps.rolldownOptions = viteConf.optimizeDeps.rolldownOptions || {};
        viteConf.optimizeDeps.rolldownOptions.define = {
          ...(viteConf.optimizeDeps.rolldownOptions.define || {}),
          __VUE_OPTIONS_API__: 'true',
        };
        viteConf.optimizeDeps.esbuildOptions = viteConf.optimizeDeps.esbuildOptions || {};
        viteConf.optimizeDeps.esbuildOptions.define = {
          ...(viteConf.optimizeDeps.esbuildOptions.define || {}),
          __VUE_OPTIONS_API__: 'true',
        };
      },

      viteVuePluginOptions: {
        features: {
          optionsAPI: true,
        },
      },

      vitePlugins: [
        [
          'vite-plugin-checker',
          {
            eslint: {
              lintCommand: 'eslint -c ./eslint.config.js "./src*/**/*.{js,mjs,cjs,vue}"',
              useFlatConfig: true,
            },
          },
          { server: false },
        ],
      ],
    },

    // https://v2.quasar.dev/quasar-cli-vite/quasar-config-file#devserver
    devServer: {
      // vueDevtools: true,
      // https: true,
      open: false,
      proxy: {
        '/api': {
          target: process.env.DEV_API_PROXY_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
        },
      },
    },

    // https://v2.quasar.dev/quasar-cli-vite/quasar-config-file#framework
    framework: {
      config: {
        brand: {
          primary: '#1a479f',
          secondary: '#2f78d6',
          accent: '#0f9d8a',
          dark: '#102a56',
          positive: '#2e9b4f',
          negative: '#d32f2f',
          info: '#2f78d6',
          warning: '#f0a202',
        },
      },

      lang: 'ko-KR',

      // Quasar plugins
      plugins: ['Notify', 'Dialog', 'LocalStorage'],
    },

    // animations: 'all', // --- includes all animations
    // https://v2.quasar.dev/options/animations
    animations: ['fadeIn', 'fadeOut'],

    // https://v2.quasar.dev/quasar-cli-vite/quasar-config-file#sourcefiles
    // sourceFiles: {
    //   rootComponent: 'src/App.vue',
    //   router: 'src/router/index',
    //   store: 'src/store/index',
    //   pwaRegisterServiceWorker: 'src-pwa/register-sw',
    //   pwaServiceWorker: 'src-pwa/sw/custom-sw',
    //   pwaManifestFile: 'src-pwa/manifest.json',
    //   electronMain: 'src-electron/electron-main',
    //   electronPreload: 'src-electron/electron-preload'
    //   bexManifestFile: 'src-bex/manifest.json
    // },

    // https://v2.quasar.dev/quasar-cli-vite/developing-ssr/configuring-ssr
    ssr: {
      /**
       * The default port that the production server should use
       * (gets superseded if process.env.PORT is specified at runtime)
       */
      prodPort: 3000,
      middlewares: [
        'render', // keep this as last one
      ],

      // pwa: true,
      // pwaOfflineHtmlFilename: 'offline.html', // do NOT use index.html as name!
    },

    // https://v2.quasar.dev/quasar-cli-vite/developing-ssg/configuring-ssg
    ssg: {},

    // https://v2.quasar.dev/quasar-cli-vite/developing-pwa/configuring-pwa
    pwa: {
      workboxMode: 'GenerateSW', // 'GenerateSW' or 'InjectManifest'
      // swFilename: 'sw.js',
      // manifestFilename: 'manifest.json',
      // extendPWAManifestJson (json) {},
      // useCredentialsForManifestTag: true,
      // injectPWAMetaTags: false,
      // extendPWACustomSWConf (rolldownConf) {},
      // extendPWAGenerateSWOptions (cfg) {},
      // extendPWAInjectManifestOptions (cfg) {},
    },

    // https://v2.quasar.dev/quasar-cli-vite/developing-cordova-apps/configuring-cordova
    cordova: {},

    // https://v2.quasar.dev/quasar-cli-vite/developing-capacitor-apps/configuring-capacitor
    capacitor: {
      hideSplashscreen: true,
    },

    // https://v2.quasar.dev/quasar-cli-vite/developing-electron-apps/configuring-electron
    electron: {
      // Electron preload scripts (if any) from /src-electron, WITHOUT file extension
      preloadScripts: ['electron-preload'],

      // specify the debugging port to use for the Electron app when running in development mode
      inspectPort: 5858,

      bundler: 'packager', // 'packager' or 'builder'

      packager: {
        // https://github.com/electron-userland/electron-packager/blob/master/docs/api.md#options
      },

      builder: {
        // https://www.electron.build/configuration
        appId: 'dabaeum-frontend',
      },
    },

    // https://v2.quasar.dev/quasar-cli-vite/developing-browser-extensions/configuring-bex
    bex: {
      /**
       * The list of extra scripts (js/ts) not in your bex manifest that you want to
       * compile and use in your browser extension.
       *
       * @example [ 'my-script.js', 'sub-folder/my-other-script.js' ]
       */
      extraScripts: [],
    },
  };
});
