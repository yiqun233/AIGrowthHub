import { StrictMode, useState } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App as AntApp, Button, Checkbox, ConfigProvider, DatePicker, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Skeleton, Space, Tag } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ArrowRightOutlined, BookOutlined, CheckOutlined, EditOutlined, FlagOutlined, HomeOutlined, PlusOutlined, SettingOutlined, UnorderedListOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { api, today } from './api';
import type { Dashboard, Goal, Page, StudyLog, Task, TaskStatus } from './api';
import 'antd/dist/reset.css';
import './style.css';
const client=new QueryClient({defaultOptions:{queries:{retry:1,refetchOnWindowFocus:false}}});
const labels:Record<TaskStatus,string>={TODO:'待开始',IN_PROGRESS:'进行中',DONE:'已完成',CANCELLED:'已取消'};
const statusOptions=Object.entries(labels).map(([value,label])=>({value,label}));
function useAction<T>(fn:(data:T)=>Promise<unknown>,done?:()=>void){
 const q=useQueryClient();const {message}=AntApp.useApp();
 return useMutation({mutationFn:fn,onSuccess:()=>{void q.invalidateQueries();void message.success('已保存');done?.();},onError:(e:Error)=>{void message.error(e.message);}});
}
function State({loading,error,children}:{loading:boolean;error:Error|null;children:ReactNode}){
 if(loading)return <Skeleton active paragraph={{rows:5}}/>;
 if(error)return <Alert type="error" showIcon title="暂时无法读取数据" description={error.message} action={<Button onClick={()=>void client.invalidateQueries()}>重试</Button>}/>;
 return children;
}
function Header({title,description,action}:{title:string;description:string;action?:ReactNode}){return <header className="page-header"><div><div className="eyebrow">个人成长工作台</div><h1>{title}</h1><p>{description}</p></div>{action}</header>;}
function TaskEditor({task,onClose}:{task:Task|null|undefined;onClose:()=>void}){
 const [form]=Form.useForm();const goals=useQuery({queryKey:['goals'],queryFn:()=>api<Goal[]>('/goals')});
 interface Values {title:string;detail?:string;scheduledDate:Dayjs;estimateMinutes:number;priority:number;goalId?:string;acceptanceCriteria?:string}
 const save=useAction<Values>(v=>api(task?'/tasks/'+task.id:'/tasks',task?'PATCH':'POST',{...v,scheduledDate:v.scheduledDate.format('YYYY-MM-DD'),...(task?{version:task.version,clearGoal:!v.goalId}:{})}),onClose);
 return <Modal open={task!==undefined} title={task?'编辑任务':'新建学习任务'} onCancel={onClose} footer={null} destroyOnHidden><Form layout="vertical" form={form} initialValues={task?{...task,scheduledDate:dayjs(task.scheduledDate)}:{scheduledDate:dayjs(today()),estimateMinutes:30,priority:2}} onFinish={v=>save.mutate(v)}>
 <Form.Item name="title" label="任务名称" rules={[{required:true,whitespace:true,message:'请输入任务名称'}]}><Input maxLength={200} placeholder="例如：实现一个带校验的 REST 接口" autoFocus/></Form.Item>
 <div className="form-grid"><Form.Item name="scheduledDate" label="计划日期" rules={[{required:true}]}><DatePicker allowClear={false}/></Form.Item><Form.Item name="estimateMinutes" label="预计分钟" rules={[{required:true}]}><InputNumber min={1} max={1440}/></Form.Item></div>
 <div className="form-grid"><Form.Item name="priority" label="优先级"><Select options={[{value:1,label:'优先'},{value:2,label:'常规'},{value:3,label:'有空再做'}]}/></Form.Item><Form.Item name="goalId" label="关联目标"><Select allowClear loading={goals.isPending} options={goals.data?.filter(g=>g.status==='ACTIVE'||g.id===task?.goalId).map(g=>({value:g.id,label:g.title}))}/></Form.Item></div>
 {goals.error?<Alert type="warning" title="目标列表读取失败，可稍后重试"/>:null}
 <Form.Item name="acceptanceCriteria" label="完成时拿出什么成果"><Input.TextArea rows={2} maxLength={5000} placeholder="例如：接口通过参数校验和持久化测试"/></Form.Item>
 <Form.Item name="detail" label="补充说明"><Input.TextArea rows={3} maxLength={20000}/></Form.Item>
 <div className="modal-actions"><Button onClick={onClose}>取消</Button><Button type="primary" htmlType="submit" loading={save.isPending}>保存任务</Button></div>
 </Form></Modal>;
}
function TaskList({tasks,onEdit}:{tasks:Task[];onEdit:(t:Task)=>void}){
 const update=useAction<{task:Task;status:TaskStatus}>(({task,status})=>api('/tasks/'+task.id,'PATCH',{version:task.version,status}));
 if(!tasks.length)return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有任务，先安排一件能完成的小事"/>;
 return <div className="task-list">{tasks.map(task=><div className={'task-row '+(task.status==='DONE'?'is-done':'')} key={task.id}>
 <Checkbox aria-label={'完成任务：'+task.title} checked={task.status==='DONE'} disabled={update.isPending||task.status==='CANCELLED'} onChange={e=>update.mutate({task,status:e.target.checked?'DONE':'TODO'})}/>
 <div className="task-body"><strong>{task.title}</strong><div className="task-meta"><span>{task.scheduledDate}</span><span>{task.estimateMinutes} 分钟</span>{task.priority===1?<Tag color="gold">优先</Tag>:null}</div>{task.acceptanceCriteria?<p>{task.acceptanceCriteria}</p>:null}</div>
 <Select aria-label={'任务状态：'+task.title} size="small" className="status-select" value={task.status} options={statusOptions} disabled={update.isPending} onChange={status=>update.mutate({task,status})}/>
 <Button type="text" aria-label={'编辑任务：'+task.title} icon={<EditOutlined aria-hidden="true"/>} onClick={()=>onEdit(task)}/>
 </div>)}</div>;
}
function Today(){
 const q=useQuery({queryKey:['dashboard'],queryFn:()=>api<Dashboard>('/dashboard')});
 const [edit,setEdit]=useState<Task|null|undefined>();
 return <><Header title="今天，向前一步。" description="用一件具体的小事，把学习变成自己的能力。" action={<Button type="primary" icon={<PlusOutlined aria-hidden="true"/>} onClick={()=>setEdit(null)}>新建任务</Button>}/>
 <State loading={q.isPending} error={q.error}>{q.data?<>
 <div className="metrics"><div><span>今日完成</span><strong>{q.data.completedCount}<small> / {q.data.plannedCount}</small></strong></div><div><span>本周学习</span><strong>{q.data.weekDurationMinutes}<small> 分钟</small></strong></div><div><span>待重新安排</span><strong>{q.data.overdueCount}<small> 项</small></strong></div><div className="date-metric"><span>{q.data.timezone}</span><strong>{dayjs(q.data.date).format('MM.DD')}<small> {['周日','周一','周二','周三','周四','周五','周六'][dayjs(q.data.date).day()]}</small></strong></div></div>
 <div className="today-grid"><section className="panel"><div className="section-heading"><h2>今日任务</h2><NavLink to="/plan">查看全部 <ArrowRightOutlined aria-hidden="true"/></NavLink></div><TaskList tasks={q.data.tasks} onEdit={setEdit}/></section>
 <aside className="note-panel"><span className="note-label">记录比打卡更有价值</span><h2>今天收获了什么？</h2><p>记下做过的实践、拿得出的成果，或者暂时卡住的地方。休息也是正常的安排。</p><NavLink to="/logs"><Button icon={<BookOutlined aria-hidden="true"/>}>写学习记录</Button></NavLink><div className="coach-note">AI 学习建议将在下一里程碑接入，当前数据均由你自己创建和管理。</div></aside></div>
 <section className="panel recent"><div className="section-heading"><h2>最近记录</h2><NavLink to="/logs">全部记录 <ArrowRightOutlined aria-hidden="true"/></NavLink></div>{q.data.recentLogs.length?q.data.recentLogs.map(l=><article key={l.id} className="log-preview"><span>{l.studyDate} · {l.durationMinutes} 分钟</span><p>{l.content}</p>{l.achievements?<Tag icon={<CheckOutlined aria-hidden="true"/>}>{l.achievements.slice(0,80)}</Tag>:null}</article>):<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="记录会从你的第一次实践开始积累"/>}</section>
 </>:null}</State><TaskEditor key={edit?.id??String(edit)} task={edit} onClose={()=>setEdit(undefined)}/></>;
}
function Plan(){
 const [date,setDate]=useState<string>();const [status,setStatus]=useState<TaskStatus>();const [page,setPage]=useState(0);const [edit,setEdit]=useState<Task|null|undefined>();
 const params=new URLSearchParams({page:String(page),size:'20'});if(date)params.set('date',date);if(status)params.set('status',status);
 const q=useQuery({queryKey:['tasks',date,status,page],queryFn:()=>api<Page<Task>>('/tasks?'+params)});
 return <><Header title="学习计划" description="一次安排几件小事，给变化留出空间。" action={<Button type="primary" icon={<PlusOutlined aria-hidden="true"/>} onClick={()=>setEdit(null)}>新建任务</Button>}/>
 <section className="panel"><div className="filters"><DatePicker placeholder="所有日期" value={date?dayjs(date):null} onChange={d=>{setDate(d?.format('YYYY-MM-DD'));setPage(0);}}/><Select aria-label="筛选任务状态" allowClear placeholder="所有状态" value={status} options={statusOptions} style={{width:140}} onChange={s=>{setStatus(s);setPage(0);}}/></div>
 <State loading={q.isPending} error={q.error}><TaskList tasks={q.data?.items??[]} onEdit={setEdit}/><Pagination page={page} total={q.data?.total??0} size={20} change={setPage}/></State></section>
 <TaskEditor key={edit?.id??String(edit)} task={edit} onClose={()=>setEdit(undefined)}/></>;
}
function Pagination({page,total,size,change}:{page:number;total:number;size:number;change:(n:number)=>void}){
 return total>size?<div className="pagination"><span>共 {total} 条 · 第 {page+1} 页</span><Button disabled={!page} onClick={()=>change(page-1)}>上一页</Button><Button disabled={(page+1)*size>=total} onClick={()=>change(page+1)}>下一页</Button></div>:null;
}
function Goals(){
 const q=useQuery({queryKey:['goals'],queryFn:()=>api<Goal[]>('/goals')});const [edit,setEdit]=useState<Goal|null|undefined>();
 const archive=useAction<Goal>(g=>api('/goals/'+g.id,'PATCH',{...g,status:g.status==='ACTIVE'?'ARCHIVED':'ACTIVE'}));
 return <><Header title="长期目标" description="用目标保持方向，用实际成果衡量进步。" action={<Button type="primary" icon={<PlusOutlined aria-hidden="true"/>} onClick={()=>setEdit(null)}>新建目标</Button>}/>
 <State loading={q.isPending} error={q.error}><section className="panel">{q.data?.length?q.data.map(g=><article className="goal-row" key={g.id}><div className="goal-symbol"><FlagOutlined aria-hidden="true"/></div><div className="goal-content"><Space><h2>{g.title}</h2>{g.status==='ARCHIVED'?<Tag>已归档</Tag>:null}</Space><p>{g.description||'还没有补充目标说明'}</p><span>{g.startDate||'未设开始日期'} 至 {g.targetDate||'未设目标日期'}</span></div><Space><Button onClick={()=>setEdit(g)}>编辑</Button><Popconfirm title={g.status==='ACTIVE'?'归档目标？历史记录会保留。':'重新启用目标？'} onConfirm={()=>archive.mutate(g)}><Button loading={archive.isPending}>{g.status==='ACTIVE'?'归档':'启用'}</Button></Popconfirm></Space></article>):<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先写下一个未来几个月想达成的目标"/>}</section></State>
 {edit!==undefined?<GoalEditor key={edit?.id??'new'} goal={edit} close={()=>setEdit(undefined)}/>:null}</>;
}
function GoalEditor({goal,close}:{goal:Goal|null;close:()=>void}){
 interface Values {title:string;description?:string;startDate?:Dayjs;targetDate?:Dayjs;priority:number}
 const save=useAction<Values>(v=>api(goal?'/goals/'+goal.id:'/goals',goal?'PATCH':'POST',{...v,startDate:v.startDate?.format('YYYY-MM-DD')??null,targetDate:v.targetDate?.format('YYYY-MM-DD')??null,...(goal?{version:goal.version,status:goal.status}:{})}),close);
 return <Modal open title={goal?'编辑目标':'新建长期目标'} onCancel={close} footer={null}><Form layout="vertical" initialValues={goal?{...goal,startDate:goal.startDate?dayjs(goal.startDate):undefined,targetDate:goal.targetDate?dayjs(goal.targetDate):undefined}:{priority:2}} onFinish={v=>save.mutate(v)}>
 <Form.Item name="title" label="目标名称" rules={[{required:true,whitespace:true}]}><Input maxLength={200} placeholder="例如：能够独立交付一个可靠的 AI 应用"/></Form.Item><Form.Item name="description" label="希望达成什么"><Input.TextArea rows={4} maxLength={20000}/></Form.Item>
 <div className="form-grid"><Form.Item name="startDate" label="开始日期"><DatePicker/></Form.Item><Form.Item name="targetDate" label="目标日期"><DatePicker/></Form.Item></div>
 <Form.Item name="priority" label="优先级"><Select options={[{value:1,label:'优先'},{value:2,label:'常规'},{value:3,label:'有空再做'}]}/></Form.Item><div className="modal-actions"><Button onClick={close}>取消</Button><Button type="primary" htmlType="submit" loading={save.isPending}>保存目标</Button></div></Form></Modal>;
}
function LogEditor({log,close}:{log:StudyLog|null;close:()=>void}){
 const tasks=useQuery({queryKey:['tasks','log-options'],queryFn:()=>api<Page<Task>>('/tasks?size=100')});
 interface Values {studyDate:Dayjs;content:string;durationMinutes:number;achievements?:string;blockers?:string;taskIds?:string[]}
 const save=useAction<Values>(v=>{const data={...v,studyDate:v.studyDate.format('YYYY-MM-DD'),taskIds:v.taskIds??[]};return api(log?'/study-logs/'+log.id:'/study-logs',log?'PATCH':'POST',log?{version:log.version,data}:data);},close);
 return <Modal open title={log?'编辑学习记录':'写学习记录'} onCancel={close} footer={null} width={660}><Form layout="vertical" initialValues={log?{...log,studyDate:dayjs(log.studyDate)}:{studyDate:dayjs(today()),durationMinutes:30,taskIds:[]}} onFinish={v=>save.mutate(v)}>
 <div className="form-grid"><Form.Item name="studyDate" label="记录日期" rules={[{required:true}]}><DatePicker allowClear={false}/></Form.Item><Form.Item name="durationMinutes" label="实际分钟" rules={[{required:true}]}><InputNumber min={0} max={1440}/></Form.Item></div>
 <Form.Item name="content" label="学了什么，做了什么" rules={[{required:true,whitespace:true,message:'请写下学习记录'}]}><Input.TextArea rows={5} maxLength={50000} placeholder="也可以记录今天为什么没学，或下次想从哪里继续。"/></Form.Item>
 <Form.Item name="achievements" label="实际成果"><Input.TextArea rows={2} maxLength={20000} placeholder="做出的功能、通过的测试、理解清楚的问题……"/></Form.Item><Form.Item name="blockers" label="还卡在哪里"><Input.TextArea rows={2} maxLength={20000}/></Form.Item>
 <Form.Item name="taskIds" label="关联任务"><Select mode="multiple" optionFilterProp="label" loading={tasks.isPending} options={tasks.data?.items.map(t=>({value:t.id,label:t.title}))}/></Form.Item>
 {tasks.data&&tasks.data.total>100?<p className="muted">当前显示最近 100 项任务；已关联的历史任务会保留。</p>:null}
 <div className="modal-actions"><Button onClick={close}>取消</Button><Button type="primary" htmlType="submit" loading={save.isPending}>保存记录</Button></div></Form></Modal>;
}
function Logs(){
 const [date,setDate]=useState<string>();const [page,setPage]=useState(0);const [edit,setEdit]=useState<StudyLog|null|undefined>();
 const params=new URLSearchParams({page:String(page),size:'20'});if(date){params.set('from',date);params.set('to',date);}
 const q=useQuery({queryKey:['logs',date,page],queryFn:()=>api<Page<StudyLog>>('/study-logs?'+params)});
 return <><Header title="学习日志" description="保留自己的原话，让每一次实践都有迹可循。" action={<Button type="primary" icon={<PlusOutlined aria-hidden="true"/>} onClick={()=>setEdit(null)}>写学习记录</Button>}/><div className="filters"><DatePicker placeholder="所有日期" value={date?dayjs(date):null} onChange={d=>{setDate(d?.format('YYYY-MM-DD'));setPage(0);}}/></div>
 <State loading={q.isPending} error={q.error}>{q.data?.items.length?q.data.items.map(l=><article className="panel log-article" key={l.id}><div className="section-heading"><Space><Tag>{l.studyDate}</Tag><span className="muted">{l.durationMinutes} 分钟 · 关联 {l.taskIds.length} 项任务</span></Space><Button icon={<EditOutlined aria-hidden="true"/>} onClick={()=>setEdit(l)}>编辑</Button></div><p className="log-content">{l.content}</p>{l.achievements?<div className="log-detail"><strong>实际成果</strong><p>{l.achievements}</p></div>:null}{l.blockers?<div className="log-detail blocker"><strong>还卡在哪里</strong><p>{l.blockers}</p></div>:null}</article>):<section className="panel"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有记录，第一次保存后就会出现在这里"/></section>}<Pagination page={page} total={q.data?.total??0} size={20} change={setPage}/></State>
 {edit!==undefined?<LogEditor key={edit?.id??'new'} log={edit} close={()=>setEdit(undefined)}/>:null}</>;
}
function Settings(){return <><Header title="设置与数据" description="先把学习记录保存好，再逐步加入智能辅助。"/><section className="panel settings-panel"><h2>当前运行方式</h2><dl><dt>业务时区</dt><dd>Asia/Shanghai</dd><dt>使用范围</dt><dd>本机个人使用，尚未启用服务器登录</dd><dt>AI 提供商</dt><dd>尚未接入，下一里程碑支持 OpenAI / DeepSeek</dd><dt>数据备份</dt><dd>在 WSL 项目目录执行 <code>bash scripts/backup.sh</code></dd></dl><Alert type="info" showIcon title="原始记录优先" description="AI 未配置或不可用时，你仍可以管理目标、任务和学习记录。密钥将只保存在服务端，不通过网页填写。"/></section></>;}
const nav=[{to:'/today',label:'今日',icon:<HomeOutlined aria-hidden="true"/>},{to:'/plan',label:'学习计划',icon:<UnorderedListOutlined aria-hidden="true"/>},{to:'/goals',label:'长期目标',icon:<FlagOutlined aria-hidden="true"/>},{to:'/logs',label:'学习日志',icon:<BookOutlined aria-hidden="true"/>},{to:'/settings',label:'设置与数据',icon:<SettingOutlined aria-hidden="true"/>}];
function Workspace(){const loc=useLocation();return <div className="workspace"><aside className="sidebar"><NavLink to="/today" className="brand"><span className="brand-mark">G</span><span>GrowthHub<small>把实践变成成长</small></span></NavLink><nav aria-label="主导航">{nav.map(n=><NavLink key={n.to} to={n.to}>{n.icon}<span>{n.label}</span></NavLink>)}</nav><div className="sidebar-bottom"><span className="local-dot"/>本地个人工作空间<p>按自己的节奏，持续向前。</p></div></aside><main key={loc.pathname}><Routes><Route path="/today" element={<Today/>}/><Route path="/plan" element={<Plan/>}/><Route path="/goals" element={<Goals/>}/><Route path="/logs" element={<Logs/>}/><Route path="/settings" element={<Settings/>}/><Route path="*" element={<Navigate to="/today" replace/>}/></Routes><footer>记录真实的实践，而不只是完成打卡。</footer></main></div>;}
createRoot(document.getElementById('root')!).render(<StrictMode><ConfigProvider button={{autoInsertSpace:false}} locale={zhCN} theme={{token:{colorPrimary:'#147d64',colorInfo:'#147d64',borderRadius:8,fontFamily:'"Segoe UI", "Microsoft YaHei", sans-serif',controlHeight:38}}}><AntApp><QueryClientProvider client={client}><BrowserRouter><Workspace/></BrowserRouter></QueryClientProvider></AntApp></ConfigProvider></StrictMode>);
