import { useState } from 'react';
import { NavLink, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, DatePicker, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Skeleton, Space, Tag } from 'antd';
import dayjs from 'dayjs';
import { api, ApiError, today } from './api';
import { WorkPanel } from './work';
import type { LearningStage, StudyLog, Task } from './api';
interface WeeklyPlan {id:string;weekStart:string;theme:string;budgetMinutes:number;notes:string;status:'ACTIVE'|'ARCHIVED';version:number;learningStageId:string|null}
interface Facts {weekStart:string;weekEnd:string;timezone:string;plan:WeeklyPlan|null;summary:{logCount:number;actualMinutes:number;scheduledTaskCount:number;scheduledMinutes:number;scheduledDoneCount:number;completedDuringWeekCount:number;learningMinutes:number;developmentMinutes:number};logs:StudyLog[];scheduledTasks:Task[];completedDuringWeek:Task[];linkedTasks:Task[]}
interface Review {id:string;weekStart:string;finalContent:string;status:'DRAFT'|'CONFIRMED';confirmedAt:string|null;version:number;stale:boolean;snapshot:Facts}
interface Overview {facts:Facts;review:Review|null}
function useWrite<T>(fn:(v:T)=>Promise<unknown>,done?:()=>void){
 const q=useQueryClient();const {message}=App.useApp();
 return useMutation({mutationFn:fn,onSuccess:()=>{void q.invalidateQueries();void message.success('已保存');done?.();},onError:(e:Error)=>{void message.error(e.message);if(e instanceof ApiError&&e.status===409)void q.invalidateQueries();}});
}
export function Weekly(){
 const [params,setParams]=useSearchParams();const raw=params.get('week')??today();const date=dayjs(raw).isValid()?dayjs(raw):dayjs(today());const start=date.subtract((date.day()+6)%7,'day').format('YYYY-MM-DD');
 const q=useQuery({queryKey:['week',start],queryFn:()=>api<Overview>('/weeks?date='+start)});const client=useQueryClient();
 const [editing,setEditing]=useState(false);const create=useWrite(()=>api('/reviews/weekly','POST',{weekStart:start}));
 const setWeek=(value:string)=>{setEditing(false);setParams({week:value});};
 const f=q.data?.facts;const review=q.data?.review;
 return <><header className="page-header"><div><div className="eyebrow">周期成长</div><h1>周计划与复盘</h1><p>从真实日期、任务和原文中，整理这一周的收获与下一步。</p></div><Button onClick={()=>void client.invalidateQueries({queryKey:['week',start]})}>刷新周数据</Button></header>
 <div className="filters"><Button onClick={()=>setWeek(dayjs(start).subtract(7,'day').format('YYYY-MM-DD'))}>上一周</Button><DatePicker aria-label="查看哪一周" value={date} allowClear={false} onChange={d=>{if(d)setWeek(d.format('YYYY-MM-DD'));}}/><Button onClick={()=>setWeek(dayjs(start).add(7,'day').format('YYYY-MM-DD'))}>下一周</Button><Button onClick={()=>setWeek(today())}>本周</Button></div>
 {q.isPending?<Skeleton active/>:q.error?<Alert showIcon type="error" title="暂时无法读取这一周" description={q.error.message} action={<Button onClick={()=>void q.refetch()}>重试</Button>}/>:f?<>
 <p className="muted">{f.weekStart} 至 {f.weekEnd} · 周一开始 · {f.timezone}</p>
 <div className="metrics"><div><span>日志实际时长</span><strong>{f.summary.actualMinutes}<small> 分钟</small></strong></div><div><span>排期预计时长</span><strong>{f.summary.scheduledMinutes}<small> 分钟</small></strong></div><div><span>本周排期当前已完成</span><strong>{f.summary.scheduledDoneCount}<small> / {f.summary.scheduledTaskCount}</small></strong></div><div><span>完成时间落在本周</span><strong>{f.summary.completedDuringWeekCount}<small> 项</small></strong></div></div>
 <p className="muted">学习 {f.summary.learningMinutes} 分钟 · 开发实践 {f.summary.developmentMinutes} 分钟；两类分别记录，总时长只累计一次。</p>
 <section className="panel weekly-section"><div className="section-heading"><h2>这一周想推进什么</h2><Button onClick={()=>setEditing(true)}>{f.plan?'编辑周计划':'建立周计划'}</Button></div>{f.plan?<><Space wrap><Tag>{f.plan.status==='ACTIVE'?'执行中':'已归档'}</Tag><strong>{f.plan.theme}</strong><Tag>周预算 {f.plan.budgetMinutes} 分钟</Tag></Space><p className="weekly-text">{f.plan.notes||'暂未填写补充说明。'}</p>{f.summary.scheduledMinutes>f.plan.budgetMinutes?<Alert showIcon type="warning" title="当前排期预计时长超过周预算" description="可以到学习计划调整日期、时长或取消任务；这里不会自动改动已有任务。"/>:null}</>:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="先确定一个主题和可用时间；休息周也可以设置为 0 分钟。"/>}<p className="muted">按任务的计划日期归入这一周，取消任务不计预计时长。实际时长只累计日志，不与任务预计时间相加。</p></section>
 <section className="panel weekly-section"><div className="section-heading"><h2>本周复盘</h2>{review?<Tag>{review.status==='CONFIRMED'?'已确认 · 只读':'草稿'}</Tag>:null}</div>{review?<ReviewEditor key={review.id} review={review}/>:<><p className="weekly-text">冻结当前周计划、日志和相关任务作为依据，再写下自己的收获、阻塞与下周调整。此步骤不调用模型。</p><Button type="primary" loading={create.isPending} onClick={()=>create.mutate(undefined)}>建立复盘草稿</Button></>}</section>
 <section className="panel weekly-section"><div className="section-heading"><h2>本周实时依据</h2><Space><NavLink to="/plan">安排任务</NavLink><NavLink to="/logs">写日志</NavLink></Space></div><Evidence facts={f}/></section>
 {editing?<PlanEditor key={start+':'+(f.plan?.version??'new')} weekStart={start} plan={f.plan} close={()=>setEditing(false)}/>:null}
 </>:null}</>;
}
function PlanEditor({weekStart,plan,close}:{weekStart:string;plan:WeeklyPlan|null;close:()=>void}){
 const stages=useQuery({queryKey:['stages'],queryFn:()=>api<LearningStage[]>('/stages')});
 interface Values {theme:string;budgetMinutes:number;notes:string;learningStageId?:string}
 const save=useWrite<Values>(v=>api(plan?'/weekly-plans/'+plan.id:'/weekly-plans',plan?'PATCH':'POST',plan?{...v,version:plan.version,status:plan.status}:{...v,weekStart}),close);
 const archive=useWrite(()=>api('/weekly-plans/'+plan!.id,'PATCH',{...plan,status:plan!.status==='ACTIVE'?'ARCHIVED':'ACTIVE'}),close);
 return <Modal open title={plan?'编辑周计划':'建立周计划'} onCancel={close} footer={null}><Form layout="vertical" initialValues={plan??{theme:'',budgetMinutes:300,notes:'',learningStageId:stages.data?.find(s=>s.status==='ACTIVE')?.id}} onFinish={v=>save.mutate(v)}>
 <p className="muted">周一 {weekStart} 开始。日期固定，防止历史周计划被移到其他周。</p><Form.Item name="theme" label="本周主题" rules={[{required:true,whitespace:true,message:'请输入本周主题'}]}><Input maxLength={200}/></Form.Item><Form.Item name="learningStageId" label="关联学习主题"><Select allowClear loading={stages.isPending} options={stages.data?.filter(s=>s.status!=='CLOSED'||s.id===plan?.learningStageId).map(s=>({value:s.id,label:s.title}))}/></Form.Item><Form.Item name="budgetMinutes" label="整周可用分钟" rules={[{required:true}]}><InputNumber min={0} max={10080}/></Form.Item><Form.Item name="notes" label="想完成的成果与安排"><Input.TextArea rows={4} maxLength={10000}/></Form.Item>
 <div className="modal-actions">{plan?<Button loading={archive.isPending} onClick={()=>archive.mutate(undefined)}>{plan.status==='ACTIVE'?'归档周计划':'恢复周计划'}</Button>:null}<Button onClick={close}>取消</Button><Button type="primary" htmlType="submit" loading={save.isPending}>保存周计划</Button></div></Form></Modal>;
}
function ReviewEditor({review:r}:{review:Review}){
 const [draft,setDraft]=useState<string|null>(null);const content=draft??r.finalContent;const dirty=content!==r.finalContent;
 const save=useWrite(()=>api('/reviews/weekly/'+r.id,'PATCH',{version:r.version,finalContent:content}));
 const refresh=useWrite(()=>api('/reviews/weekly/'+r.id+'/refresh','POST',{version:r.version}));
 const confirm=useWrite(()=>api('/reviews/weekly/'+r.id+'/confirm','POST',{version:r.version}));
 return <>{r.stale?<Alert showIcon type="warning" title={r.status==='DRAFT'?'来源已变化，请重新核对':'后续记录已变化，历史复盘仍保留确认时的快照'} description={r.status==='DRAFT'?'先保存复盘文字，再刷新来源。刷新保留已保存的文字；核对后才能确认。':undefined}/>:null}
 {r.status==='DRAFT'?<><label className="weekly-label" htmlFor="weekly-final-content">我的周复盘</label><Input.TextArea id="weekly-final-content" value={content} onChange={e=>setDraft(e.target.value)} rows={7} maxLength={30000} placeholder="这一周拿出了什么成果？依据是哪条记录？哪里卡住了？下周具体调整什么？"/>
 <Space wrap className="weekly-actions"><Button loading={save.isPending} onClick={()=>save.mutate(undefined)}>保存复盘内容</Button><Button loading={refresh.isPending} disabled={dirty} onClick={()=>refresh.mutate(undefined)}>刷新来源快照</Button><Popconfirm title="确认这份周复盘？" description="确认后保留当前内容和来源快照，复盘变为只读，不会生成或修改任务。" onConfirm={()=>confirm.mutateAsync(undefined)}><Button type="primary" loading={confirm.isPending} disabled={dirty||r.stale||!content.trim()}>确认周复盘</Button></Popconfirm></Space>{dirty?<p className="muted">内容尚未保存，请先保存再刷新来源或确认。<Button type="link" onClick={()=>setDraft(null)}>载入已保存内容</Button></p>:null}</>:<><p className="weekly-text">{r.finalContent}</p><p className="muted">确认于 {new Date(r.confirmedAt!).toLocaleString('zh-CN',{timeZone:r.snapshot.timezone,hour12:false})}</p></>}
 <WorkPanel kind="WEEKLY_REVIEW" targetId={r.id} version={r.version} readOnly={r.status==='CONFIRMED'} dirty={dirty} onApplied={setDraft}/>
 <details className="snapshot"><summary>查看复盘保存的来源快照</summary><p className="muted">{r.snapshot.weekStart} 至 {r.snapshot.weekEnd}；以下原文不随后续编辑改变。</p><Evidence facts={r.snapshot}/></details></>;
}
function Evidence({facts:f}:{facts:Facts}){
 const statuses={TODO:'待开始',IN_PROGRESS:'进行中',DONE:'已完成',CANCELLED:'已取消'};
 function taskList(tasks:Task[],empty:string){return tasks.length?<ul className="weekly-tasks">{tasks.map(t=><li key={t.id}><strong>{t.title}</strong><span>{t.scheduledDate} · {t.estimateMinutes} 分钟 · {statuses[t.status]}</span>{t.completedAt?<small>完成时间：{new Date(t.completedAt).toLocaleString('zh-CN',{timeZone:f.timezone,hour12:false})}</small>:null}<small>任务 ID：{t.id}</small>{t.acceptanceCriteria?<p>验收产出：{t.acceptanceCriteria}</p>:null}</li>)}</ul>:<p className="muted">{empty}</p>;}
 return <><h3 className="weekly-subheading">学习日志（{f.logs.length} 条）</h3>{f.logs.length?f.logs.map(l=><article className="snapshot-log" key={l.id}><strong>{l.studyDate} · {l.durationMinutes} 分钟</strong><small className="weekly-id">记录 ID：{l.id}</small><p>{l.content}</p>{l.achievements?<p>成果：{l.achievements}</p>:null}{l.blockers?<p>阻塞：{l.blockers}</p>:null}</article>):<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="这一周还没有日志；不会把没有记录推断成没有学习。"/>}
 <h3 className="weekly-subheading">安排在本周的任务</h3>{taskList(f.scheduledTasks,'这一周没有排期任务。')}<details className="snapshot"><summary>查看本周完成与日志关联任务</summary><p className="muted">完成时间与排期日期可能不在同一周。以下可能与上方重复，不重复累计实际学习时长。</p><h3 className="weekly-subheading">完成时间落在本周</h3>{taskList(f.completedDuringWeek,'本周没有可追溯完成时间的任务。')}<h3 className="weekly-subheading">日志关联任务</h3>{taskList(f.linkedTasks,'本周日志没有关联任务。')}</details></>;
}
