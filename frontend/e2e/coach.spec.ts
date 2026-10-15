import { test, expect } from '@playwright/test';
async function createLog(content:string){
 const response=await fetch('http://127.0.0.1:18080/api/v1/study-logs',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({studyDate:'2042-01-01',content,durationMinutes:30})});
 if(!response.ok)throw new Error('Cannot create browser fixture');return response.json() as Promise<{id:string}>;
}
test('AI 分析、编辑草稿、确认后生成正式任务',async({page})=>{
 const log=await createLog('浏览器 AI 闭环 '+Date.now());const title='经过人工编辑的 AI 建议 '+Date.now();
 await page.goto('/coach?logId='+log.id);await expect(page.getByLabel('本次模型名称')).toHaveValue('test-openai');
 await page.getByLabel('该日总预算（分钟）').fill('90');
 await page.getByRole('button',{name:'生成分析与次日建议',exact:true}).click();await expect(page.getByText('请先确认本次记录发送范围',{exact:true})).toBeVisible();
 await page.getByRole('checkbox',{name:'同意将所选记录、近期记录及相关目标和任务发送给所选提供商分析',exact:true}).check();
 await page.getByRole('button',{name:'生成分析与次日建议',exact:true}).click();
 await expect(page.getByRole('button',{name:'编辑候选任务',exact:true})).toBeVisible({timeout:15000});
 await page.getByRole('button',{name:'编辑候选任务',exact:true}).click();await page.getByLabel('任务名称',{exact:true}).fill(title);
 await page.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(page.getByRole('heading',{name:title,exact:true})).toBeVisible();
 await page.getByRole('button',{name:'确认写入计划',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();
 await expect(page.getByText('已确认，共关联 1 项正式任务',{exact:true})).toBeVisible();await page.reload();await expect(page.getByText('已确认，共关联 1 项正式任务',{exact:true})).toBeVisible();
 await page.getByRole('link',{name:'前往学习计划查看',exact:true}).click();await expect(page.getByText(title,{exact:true})).toBeVisible();
});
test('分析失败保留重试入口，缺少密钥时禁用提交',async({page})=>{
 const log=await createLog('浏览器失败恢复 '+Date.now());await page.goto('/coach?logId='+log.id);
 await page.getByLabel('这次还有什么想告诉教练').fill('[test:auth]');
 await page.getByRole('checkbox',{name:'同意将所选记录、近期记录及相关目标和任务发送给所选提供商分析',exact:true}).check();await page.getByRole('button',{name:'生成分析与次日建议',exact:true}).click();
 await expect(page.getByRole('button',{name:'重新填写并重试',exact:true})).toBeVisible({timeout:15000});
 await page.getByRole('button',{name:'重新填写并重试',exact:true}).click();await expect(page.getByLabel('这次还有什么想告诉教练')).toHaveValue('[test:auth]');
 await page.route('**/api/v1/settings',async route=>{const response=await route.fetch();const body=await response.json();body.providers=body.providers.map((p:object)=>({...p,configured:false}));await route.fulfill({response,json:body});});
 await page.reload();await expect(page.getByText('尚未配置模型密钥',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'生成分析与次日建议',exact:true})).toBeDisabled();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
