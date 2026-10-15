import { test, expect } from '@playwright/test';
test('通过界面创建目标、完成任务、保存和编辑日志',async({page})=>{
 const suffix=String(Date.now());const title='浏览器实践 '+suffix;
 await page.goto('/goals');await page.getByRole('button',{name:'新建目标',exact:true}).click();
 await page.getByLabel('目标名称').fill('工程能力 '+suffix);await page.getByRole('button',{name:'保存目标'}).click();
 await expect(page.getByRole('heading',{name:'工程能力 '+suffix})).toBeVisible();
 await page.getByRole('link',{name:'学习计划',exact:true}).click();await page.getByRole('button',{name:'新建任务',exact:true}).click();
 await page.getByLabel('任务名称').fill(title);await page.getByLabel('完成时拿出什么成果').fill('完成一次端到端验证');
 await page.getByRole('button',{name:'保存任务'}).click();await expect(page.getByText(title,{exact:true})).toBeVisible();
 await page.getByRole('checkbox',{name:'完成任务：'+title,exact:true}).click();await expect(page.getByRole('checkbox',{name:'完成任务：'+title,exact:true})).toBeChecked();
 await page.getByRole('link',{name:'学习日志',exact:true}).click();await page.getByRole('button',{name:'写学习记录',exact:true}).click();
 await page.getByLabel('学了什么，做了什么').fill('完成了 GrowthHub 的真实浏览器流程 '+suffix);
 await page.getByLabel('实际成果').fill('目标、任务和日志都已持久化');await page.getByRole('button',{name:'保存记录'}).click();
 await expect(page.getByText('完成了 GrowthHub 的真实浏览器流程 '+suffix,{exact:true})).toBeVisible();
 const article=page.locator('article').filter({hasText:suffix});await article.getByRole('button',{name:'编辑'}).click();
 await page.getByLabel('还卡在哪里').fill('下一步接入 AI');await page.getByRole('button',{name:'保存记录'}).click();
 await expect(page.getByText('下一步接入 AI',{exact:true})).toBeVisible();await page.reload();await expect(page.getByText('下一步接入 AI',{exact:true})).toBeVisible();
 await page.goto('/today');await expect(page.getByRole('heading',{name:'今日任务',exact:true})).toBeVisible();await page.screenshot({path:'test-results/today-desktop.png',fullPage:true});
});
test('手机宽度和错误重试状态',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/today');await expect(page.getByRole('heading',{name:'今天，向前一步。'})).toBeVisible();
 await expect(page.getByRole('heading',{name:'今日任务',exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
 await page.screenshot({path:'test-results/today-mobile.png',fullPage:true});
 await page.route('**/api/v1/dashboard',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'测试服务不可用'})}));
 await page.reload();await expect(page.getByText('暂时无法读取数据',{exact:true})).toBeVisible();await page.unroute('**/api/v1/dashboard');
 await page.getByRole('button',{name:'重试',exact:true}).click();await expect(page.getByRole('heading',{name:'今日任务',exact:true})).toBeVisible();
});
