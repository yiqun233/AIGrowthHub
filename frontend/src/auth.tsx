import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Form, Input, Skeleton } from 'antd';
import { api } from './api';
interface Session {enabled:boolean;authenticated:boolean;username:string;csrfToken:string}
export function AuthGate({children}:{children:ReactNode}){
 const {message}=App.useApp();const q=useQuery({queryKey:['session'],queryFn:()=>api<Session>('/auth/session'),retry:1});
 useEffect(()=>{const listener=()=>void q.refetch();window.addEventListener('growthhub:unauthorized',listener);return()=>window.removeEventListener('growthhub:unauthorized',listener);},[q.refetch]);
 async function login(values:{username:string;password:string}){
  try{const state=await api<Session>('/auth/session');const response=await fetch('/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-CSRF-TOKEN':state.csrfToken},body:new URLSearchParams(values)});if(!response.ok){const error=await response.json();throw new Error(error.message);}window.location.reload();}catch(e){void message.error(e instanceof Error?e.message:'登录失败');}
 }
 if(q.isPending)return <div className="auth-shell"><Skeleton active/></div>;
 if(q.error)return <div className="auth-shell"><Alert type="error" title="无法读取会话" description={q.error.message} action={<Button onClick={()=>void q.refetch()}>重试</Button>}/></div>;
 if(q.data?.enabled&&!q.data.authenticated)return <main className="auth-shell"><div className="eyebrow">GrowthHub · 个人成长工作台</div><h1>登录你的工作空间</h1><p className="muted">使用服务端配置的个人账号。</p><Form layout="vertical" onFinish={v=>void login(v)}><Form.Item name="username" label="用户名" rules={[{required:true}]}><Input autoComplete="username" maxLength={100}/></Form.Item><Form.Item name="password" label="密码" rules={[{required:true}]}><Input.Password autoComplete="current-password" maxLength={200}/></Form.Item><Button type="primary" htmlType="submit">登录</Button></Form></main>;
 return <>{q.data?.enabled?<div className="session-bar"><span>{q.data.username}</span><Button size="small" onClick={()=>void api('/auth/logout','POST').then(()=>window.location.reload()).catch((e:Error)=>message.error(e.message))}>退出登录</Button></div>:null}{children}</>;
}
