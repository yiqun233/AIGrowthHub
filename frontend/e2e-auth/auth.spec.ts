import { test,expect } from '@playwright/test';
test('真实登录、受保护写入和退出',async({page})=>{
 const content='登录保护下的真实浏览器记录 '+Date.now();
 await page.goto('/logs');await expect(page.getByRole('heading',{name:'登录你的工作空间',exact:true})).toBeVisible();
 await page.getByLabel('用户名',{exact:true}).fill(process.env.AUTH_USERNAME||'security-test');await page.getByLabel('密码',{exact:true}).fill(process.env.AUTH_PASSWORD!);await page.getByRole('button',{name:'登录',exact:true}).click();
 await expect(page.getByRole('heading',{name:'学习日志',exact:true})).toBeVisible();await page.getByRole('button',{name:'写学习记录',exact:true}).click();await page.getByLabel('学了什么，做了什么',{exact:true}).fill(content);await page.getByRole('button',{name:'保存记录',exact:true}).click();await expect(page.getByText(content,{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'退出登录',exact:true}).click();await expect(page.getByRole('heading',{name:'登录你的工作空间',exact:true})).toBeVisible();const response=await page.request.get('/api/v1/exports');expect(response.status()).toBe(401);
});
