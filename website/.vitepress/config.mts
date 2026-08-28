import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'OryxOS',
  titleTemplate: ':title — OryxOS',
  description: 'Java 原生的、企业私有可审计的 Agent 统一底座 — 私有部署 · 完全可审计 · 数据不出企业',
  base: '/OryxOS-one/',
  cleanUrls: true,
  appearance: 'force-dark',

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/OryxOS-one/favicon.svg' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    ['link', { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap' }],
    ['meta', { name: 'author', content: 'OryxOS' }],
    ['meta', { name: 'keywords', content: 'OryxOS, Agent OS, AI Agent, Java Agent, enterprise agent, agent runtime, ReAct, MCP, Spring AI, agent audit, private deployment' }],
    ['meta', { name: 'robots', content: 'index, follow' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'OryxOS' }],
    ['meta', { property: 'og:title', content: 'OryxOS — Java 原生的企业级 Agent 运行时底座' }],
    ['meta', { property: 'og:description', content: '私有部署、完全可审计、数据不出企业的 Agent 统一底座。Java 21 + Spring Boot 3.x，一个 fat JAR 就跑起来。' }],
    ['meta', { property: 'og:url', content: 'https://oryxos.dev' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:title', content: 'OryxOS — Java 原生的企业级 Agent 运行时底座' }],
    ['meta', { name: 'twitter:description', content: '私有部署、完全可审计、数据不出企业的 Agent 统一底座。' }],
    ['link', { rel: 'canonical', href: 'https://oryxos.dev' }],
  ],

  locales: {
    root: {
      label: 'English',
      lang: 'en-US',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/' },
          { text: 'Docs', link: '/docs/what' },
          { text: 'GitHub', link: 'https://github.com/your-org/oryxos' },
        ],
        sidebar: {
          '/docs/': [
            {
              text: 'Getting Started',
              items: [
                { text: 'What is OryxOS', link: '/docs/what' },
                { text: 'Quick Start', link: '/docs/quick-start' },
              ],
            },
            {
              text: 'Deep Dives',
              items: [
                { text: 'Architecture', link: '/docs/architecture' },
                { text: 'Features', link: '/docs/features' },
                { text: 'Scenarios', link: '/docs/scenarios' },
              ],
            },
            {
              text: 'Reference',
              items: [
                { text: 'Roadmap', link: '/docs/roadmap' },
                { text: 'FAQ', link: '/docs/faq' },
                { text: 'Website Development', link: '/docs/development' },
              ],
            },
          ],
        },
      },
    },
    zh: {
      label: '中文',
      lang: 'zh-CN',
      link: '/zh/',
      themeConfig: {
        nav: [
          { text: '首页', link: '/zh/' },
          { text: '文档', link: '/zh/docs/what' },
          { text: 'GitHub', link: 'https://github.com/your-org/oryxos' },
        ],
        sidebar: {
          '/zh/docs/': [
            {
              text: '快速入门',
              items: [
                { text: 'OryxOS 是什么', link: '/zh/docs/what' },
                { text: '快速开始', link: '/zh/docs/quick-start' },
              ],
            },
            {
              text: '深入了解',
              items: [
                { text: '系统架构', link: '/zh/docs/architecture' },
                { text: '功能特性', link: '/zh/docs/features' },
                { text: '使用场景', link: '/zh/docs/scenarios' },
              ],
            },
            {
              text: '参考',
              items: [
                { text: '路线图', link: '/zh/docs/roadmap' },
                { text: '常见问题', link: '/zh/docs/faq' },
                { text: '网站开发与部署', link: '/zh/docs/development' },
              ],
            },
          ],
        },
      },
    },
  },

  themeConfig: {
    siteTitle: false,
    logo: '/logo-dark.svg',
    socialLinks: [
      { icon: 'github', link: 'https://github.com/your-org/oryxos' },
    ],
  },

  sitemap: {
    hostname: 'https://oryxos.dev',
  },
})
