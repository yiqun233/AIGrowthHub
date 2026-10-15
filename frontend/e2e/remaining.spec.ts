import { test,expect } from '@playwright/test';
test('成果创建、编辑与归档',async({page})=>{
 await page.goto('/achievements');await page.getByRole('button',{name:'记录成果',exact:true}).click();await page.getByLabel('成果名称',{exact:true}).fill('浏览器可验证成果');await page.getByLabel('成果说明与验证方式').fill('通过真实 API 和浏览器测试');await page.getByRole('button',{name:'保存成果',exact:true}).click();await expect(page.getByRole('heading',{name:'浏览器可验证成果',exact:true})).toBeVisible();
 const article=page.locator('article').filter({has:page.getByRole('heading',{name:'浏览器可验证成果',exact:true})});await article.getByRole('button',{name:'编辑成果'}).click();await page.getByRole('button',{name:'归档成果',exact:true}).click();await expect(page.getByRole('heading',{name:'浏览器可验证成果',exact:true})).toHaveCount(0);
 await page.getByLabel('成果状态').click();await page.getByText('已归档',{exact:true}).last().click();await expect(page.getByRole('heading',{name:'浏览器可验证成果',exact:true})).toBeVisible();
});
test('资讯候选任务需要人工确认',async({page})=>{
 await page.goto('/feeds');await expect(page.getByRole('heading',{name:'技术资讯',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'转为候选任务',exact:true}).last().click();await page.getByLabel('候选任务名称',{exact:true}).fill('浏览器资讯候选');await page.getByRole('button',{name:'保存资讯候选',exact:true}).click();
 const proposal=page.locator('article.proposal').filter({hasText:'浏览器资讯候选'});await expect(proposal.getByText('待确认',{exact:true})).toBeVisible();await proposal.getByRole('button',{name:'确认资讯任务',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(proposal.getByText('已写入计划',{exact:true})).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
test('AI 周草稿生成、采用并保持人工确认',async({page})=>{
 const raw=await fetch('http://127.0.0.1:18080/api/v1/study-logs',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({studyDate:'2080-02-05',content:'浏览器的真实周复盘依据',durationMinutes:15})});expect(raw.ok).toBeTruthy();
 await page.goto('/weekly?week=2080-02-05');await page.getByRole('button',{name:'建立复盘草稿',exact:true}).click();await page.getByRole('checkbox',{name:'同意发送本周日志、周计划及关联任务生成草稿',exact:true}).check();await page.getByRole('button',{name:'生成 AI 周复盘',exact:true}).click();await expect(page.getByRole('button',{name:'采用为复盘草稿',exact:true})).toBeVisible({timeout:15000});await page.getByRole('button',{name:'采用为复盘草稿',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(page.getByLabel('我的周复盘')).toHaveValue(/基于真实来源/);await expect(page.getByRole('button',{name:'确认周复盘',exact:true})).toBeEnabled();await expect(page.getByText('已确认 · 只读',{exact:true})).toHaveCount(0);
});
