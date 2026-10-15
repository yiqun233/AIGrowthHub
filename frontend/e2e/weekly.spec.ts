import { test, expect } from '@playwright/test';
test('周计划、复盘保存确认和历史来源',async({page})=>{
 await page.goto('/weekly?week=2054-03-02');
 await expect(page.getByRole('heading',{name:'周计划与复盘'})).toBeVisible();
 await page.getByRole('button',{name:'建立周计划',exact:true}).click();
 await page.getByLabel('本周主题',{exact:true}).fill('本周交付一个可运行接口');
 await page.getByLabel('整周可用分钟').fill('90');await page.getByLabel('想完成的成果与安排').fill('保留测试与真实记录');
 await page.getByRole('button',{name:'保存周计划',exact:true}).click();await expect(page.getByText('本周交付一个可运行接口',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'建立复盘草稿',exact:true}).click();
 await page.getByLabel('我的周复盘').fill('这一周整理了真实记录，下周验证异常场景。');
 await expect(page.getByRole('button',{name:'确认周复盘',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'保存复盘内容',exact:true}).click();await expect(page.getByRole('button',{name:'确认周复盘',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'确认周复盘',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();
 await expect(page.getByText('已确认 · 只读',{exact:true})).toBeVisible();await page.reload();await expect(page.getByText('这一周整理了真实记录，下周验证异常场景。',{exact:true})).toBeVisible();
 await page.getByText('查看复盘保存的来源快照',{exact:true}).click();await expect(page.getByText('以下原文不随后续编辑改变。',{exact:false})).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
test('周数据错误可重试，跨年周按周一开始',async({page})=>{
 await page.route('**/api/v1/weeks?*',route=>route.fulfill({status:503,json:{message:'周数据暂时不可用'}}));
 await page.goto('/weekly?week=2027-01-03');await expect(page.getByText('周数据暂时不可用',{exact:true})).toBeVisible();
 await page.unroute('**/api/v1/weeks?*');await page.getByRole('button',{name:'重试',exact:true}).click();
 await expect(page.getByText('2026-12-28 至 2027-01-03 · 周一开始 · Asia/Shanghai',{exact:true})).toBeVisible();
});

test('版本冲突保留正在编辑的复盘文字',async({page})=>{
 const response=await fetch('http://127.0.0.1:18080/api/v1/reviews/weekly',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({weekStart:'2054-03-09'})});
 expect(response.status).toBe(201);const review=await response.json();
 await page.goto('/weekly?week=2054-03-09');await page.getByLabel('我的周复盘').fill('尚未保存的个人编辑，不能被刷新覆盖。');
 const external=await fetch('http://127.0.0.1:18080/api/v1/reviews/weekly/'+review.id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({version:review.version,finalContent:'另一处已经保存的复盘内容'})});expect(external.ok).toBeTruthy();
 await page.getByRole('button',{name:'保存复盘内容',exact:true}).click();
 await expect(page.getByText('记录已更新，请刷新后重试',{exact:true})).toBeVisible();
 await expect(page.getByLabel('我的周复盘')).toHaveValue('尚未保存的个人编辑，不能被刷新覆盖。');
 await page.getByRole('button',{name:'载入已保存内容',exact:true}).click();
 await expect(page.getByLabel('我的周复盘')).toHaveValue('另一处已经保存的复盘内容');
});
