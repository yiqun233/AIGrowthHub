import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
test('主流提供商选择和个人数据下载', async ({ page }) => {
 await page.goto('/settings');
 await page.getByLabel('默认提供商').click();
 for(const label of ['Claude / Anthropic','Google Gemini','通义千问 / 百炼','智谱 GLM','Kimi / Moonshot','豆包 / 火山方舟','OpenRouter','自定义 OpenAI 兼容接口']){
  await expect(page.getByRole('option').filter({hasText:label})).toBeAttached();
 }
 await page.getByRole('option').filter({hasText:'Google Gemini'}).click();
 await expect(page.getByLabel('默认模型名称')).toHaveValue('test-gemini');
 const downloading=page.waitForEvent('download');
 await page.getByRole('button',{name:'导出全部个人数据'}).click();
 const download=await downloading;expect(download.suggestedFilename()).toMatch(/^GrowthHub-export-.*\.json$/);
 const path=await download.path();expect(path).toBeTruthy();
 const raw=await fs.readFile(path!,'utf8');const data=JSON.parse(raw);
 expect(data.schemaVersion).toBe('growthhub-export-v3');expect(raw).not.toContain('test-only-');
 expect(data.counts.study_log).toBe(data.data.study_log.length);
 await page.route('**/api/v1/exports',route=>route.fulfill({status:500,json:{message:'导出暂时不可用，请重试'}}));
 await page.getByRole('button',{name:'导出全部个人数据'}).click();
 await expect(page.getByText('导出暂时不可用，请重试',{exact:true})).toBeVisible();
 await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
