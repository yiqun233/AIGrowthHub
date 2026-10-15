import { useEffect, useRef, useState } from 'react';
import { NavLink, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Checkbox, DatePicker, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Skeleton, Space, Tag } from 'antd';
import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { api, ApiError, today } from './api';
import type { AiProvider, AiRequest, AiSettings as Settings, Analysis, AnalysisSummary, Page, ProposedTask, StudyLog, Suggestion } from './api';
const analysisLabels={PENDING:'等待分析',RUNNING:'分析中',SUCCEEDED:'分析完成',FAILED:'分析失败'};
const suggestionLabels={DRAFT:'待确认',CONFIRMED:'已写入任务',REJECTED:'已拒绝',SUPERSEDED:'已被新建议替代'};
function useWrite<T,R>(fn:(value:T)=>Promise<R>,done?:(value:R)=>void){
 const q=useQueryClient();const {message}=App.useApp();
 return useMutation({mutationFn:fn,onSuccess:value=>{void q.invalidateQueries();done?.(value);},onError:(e:Error)=>{void message.error(e.message);if(e instanceof ApiError&&e.status===409)void q.invalidateQueries();}});
}
function LoadingError({loading,error}:{loading:boolean;error:Error|null}){
 const q=useQueryClient();return loading?<Skeleton active paragraph={{rows:3}}/>:error?<Alert type="error" showIcon title="读取失败" description={error.message} action={<Button onClick={()=>void q.invalidateQueries()}>重试</Button>}/>:null;
}
export function AiSettings(){
 const q=useQuery({queryKey:['settings'],queryFn:()=>api<Settings>('/settings')});const {message}=App.useApp();const [form]=Form.useForm();const [exporting,setExporting]=useState(false);
 async function exportData(){setExporting(true);try{const res=await fetch('/api/v1/exports');if(!res.ok){const error=await res.json().catch(()=>({message:'导出失败，请稍后重试'}));throw new Error(error.message);}const url=URL.createObjectURL(await res.blob());const link=document.createElement('a');link.href=url;link.download='GrowthHub-export-'+today()+'.json';document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(error){void message.error(error instanceof Error?error.message:'导出失败');}finally{setExporting(false);}}
 const save=useWrite<Omit<Settings,'providers'|'dailyLimit'|'timeoutSeconds'>,Settings>(i=>api('/settings','PATCH',i),()=>void message.success('设置已保存'));
 return <><header className="page-header"><div><div className="eyebrow">个人成长工作台</div><h1>设置与数据</h1><p>按自己的可用时间安排学习，模型配置只作用于下一次分析。</p></div></header>
 <LoadingError loading={q.isPending} error={q.error}/>{q.data?<>
 <section className="panel settings-panel"><div className="section-heading"><h2>学习与 AI 偏好</h2><Tag>服务端保存</Tag></div>
 <Form form={form} key={q.data.version} layout="vertical" onValuesChange={change=>{if(change.provider)form.setFieldValue('model',change.provider===q.data!.provider?q.data!.model:q.data!.providers.find(p=>p.id===change.provider)?.defaultModel??'');}} initialValues={{...q.data}} onFinish={values=>save.mutate({...values,version:q.data!.version})}>
 <div className="form-grid"><Form.Item name="provider" label="默认提供商" rules={[{required:true}]}><Select virtual={false} options={q.data.providers.map(p=>({value:p.id,label:p.label+(p.configured?'（已配置）':'（未配置）')}))}/></Form.Item><Form.Item name="model" label="默认模型名称" rules={[{pattern:/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}$/,message:'请填写有效模型标识'}]}><Input maxLength={100} placeholder="填写所选提供商账号可用的模型名称"/></Form.Item></div>
 <Form.Item name="timezone" label="业务时区" rules={[{required:true}]}><Input maxLength={100} placeholder="Asia/Shanghai"/></Form.Item>
 <Form.Item name="dailyMinutes" label="每日可用分钟" rules={[{required:true}]}><InputNumber min={0} max={1440}/></Form.Item>
 <Form.Item name="preferences" label="学习偏好"><Input.TextArea rows={3} maxLength={2000} placeholder="例如：优先 Java 和 AI 工程实践；每次安排一个可验证成果。"/></Form.Item>
 <Button type="primary" htmlType="submit" loading={save.isPending}>保存设置</Button></Form></section>
 <section className="panel settings-panel"><h2>连接状态与数据</h2><dl><dt>业务时区</dt><dd>{q.data.timezone}</dd><dt>分析限制</dt><dd>每天最多 {q.data.dailyLimit} 次分析，每次最多两次提供商请求，整体超时 {q.data.timeoutSeconds} 秒</dd><dt>提供商状态</dt><dd><Space wrap>{q.data.providers.map(p=><Tag key={p.id} color={p.configured?'success':'default'}>{p.label}：{p.configured?'已配置密钥':'未配置'}</Tag>)}</Space></dd><dt>数据导出</dt><dd><Button loading={exporting} onClick={()=>void exportData()}>导出全部个人数据</Button><p className="muted">JSON 文件包含目标、任务、日志和 AI 分析快照，不包含服务端密钥。完整数据库恢复请使用备份脚本。</p></dd><dt>数据备份</dt><dd>在 WSL 项目目录执行 <code>bash scripts/backup.sh</code></dd></dl>
 <Alert showIcon type="info" title="密钥只保存在服务端" description="在项目 .env 设置对应提供商的 API_KEY、MODEL 后重启后端。支持 OpenAI、DeepSeek、Claude、Gemini、通义千问、智谱、Kimi、豆包、OpenRouter 及自定义 OpenAI 兼容接口；自定义接口还需设置 CUSTOM_BASE_URL。模型名称填写账号实际可用的模型 ID，豆包也可填写推理接入点 ID。网页不会接收或返回密钥，具体配置见 docs/model-providers.md。"/>
 </section></>:null}</>;
}
interface GenerateValues {sourceLogId:string;targetDate:Dayjs;budgetMinutes:number;provider:AiProvider;model:string;feedback?:string;consent:boolean}
export function Coach(){
 const [params,setParams]=useSearchParams();const id=params.get('analysis');const logId=params.get('logId');
 const [form]=Form.useForm<GenerateValues>();const q=useQueryClient();
 const settings=useQuery({queryKey:['settings'],queryFn:()=>api<Settings>('/settings')});
 const logs=useQuery({queryKey:['logs','coach-options'],queryFn:()=>api<Page<StudyLog>>('/study-logs?size=100')});
 const source=useQuery({queryKey:['log',logId],queryFn:()=>api<StudyLog>('/study-logs/'+logId),enabled:!!logId});
 const history=useQuery({queryKey:['analyses'],queryFn:()=>api<Page<AnalysisSummary>>('/ai/analyses?size=20'),refetchInterval:query=>query.state.data?.items.some(a=>a.status==='PENDING'||a.status==='RUNNING')?1500:false});
 const [historyPage,setHistoryPage]=useState(0);
 const older=useQuery({queryKey:['analyses',historyPage],queryFn:()=>api<Page<AnalysisSummary>>('/ai/analyses?size=20&page='+historyPage),enabled:historyPage>0});
 const displayed=historyPage>0?older:history;
 const keyRef=useRef<{body:string;key:string}|null>(null);
 const create=useWrite<{body:AiRequest;key:string},Analysis>(({body,key})=>api('/ai/analyses','POST',body,{'Idempotency-Key':key}),a=>{keyRef.current=null;form.setFieldValue('consent',false);setParams({analysis:a.id});});
 useEffect(()=>{if(settings.data)form.setFieldsValue({provider:settings.data.provider,model:settings.data.model,budgetMinutes:settings.data.dailyMinutes,targetDate:dayjs(today()).add(1,'day'),feedback:'',consent:false});},[settings.data?.version,form]);
 useEffect(()=>{if(logId)form.setFieldValue('sourceLogId',logId);},[logId,form]);
 const selectedProvider=Form.useWatch('provider',form);const configured=settings.data?.providers.find(p=>p.id===selectedProvider)?.configured;
 const logOptions=new Map((logs.data?.items??[]).map(l=>[l.id,l]));if(source.data)logOptions.set(source.data.id,source.data);
 function generate(v:GenerateValues){
  const body:AiRequest={sourceLogId:v.sourceLogId,targetDate:v.targetDate.format('YYYY-MM-DD'),budgetMinutes:v.budgetMinutes,provider:v.provider,model:v.model,feedback:v.feedback??''};
  const serialized=JSON.stringify(body);if(keyRef.current?.body!==serialized)keyRef.current={body:serialized,key:crypto.randomUUID()};
  create.mutate({body,key:keyRef.current!.key});
 }
 return <><header className="page-header"><div><div className="eyebrow">AI 学习教练</div><h1>让下一步更具体。</h1><p>结合真实记录形成建议，编辑并确认后才会写入学习计划。</p></div><Button icon={<ReloadOutlined aria-hidden="true"/>} onClick={()=>void q.invalidateQueries()}>刷新状态</Button></header>
 <LoadingError loading={settings.isPending||logs.isPending} error={settings.error||logs.error||source.error}/>
 {settings.data?<section className="panel coach-generate"><div className="section-heading"><h2>从一条记录开始</h2><NavLink to="/logs">先写学习记录</NavLink></div>
 {!settings.data.providers.some(p=>p.configured)?<Alert type="warning" showIcon title="尚未配置模型密钥" description={<>你仍可管理目标、任务和日志。请到 <NavLink to="/settings">设置与数据</NavLink> 查看服务端配置方式，配置后再生成分析。</>}/>:null}
 <Form form={form} layout="vertical" onFinish={generate} onValuesChange={changed=>{if(changed.provider){const p=settings.data!.providers.find(p=>p.id===changed.provider);form.setFieldValue('model',changed.provider===settings.data!.provider?settings.data!.model:p?.defaultModel??'');}}}>
 <Form.Item name="sourceLogId" label="作为分析起点的学习记录" rules={[{required:true,message:'请选择学习记录'}]}><Select showSearch optionFilterProp="label" loading={logs.isPending} options={[...logOptions.values()].map(l=>({value:l.id,label:l.studyDate+' · '+l.content.slice(0,50)}))} placeholder="选择你已经保存的学习记录"/></Form.Item>
 <div className="form-grid"><Form.Item name="provider" label="本次提供商" rules={[{required:true}]}><Select virtual={false} options={settings.data.providers.map(p=>({value:p.id,label:p.label+(p.configured?'':'（未配置）')}))}/></Form.Item><Form.Item name="model" label="本次模型名称" rules={[{required:true,message:'请填写模型名称'},{pattern:/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,99}$/,message:'模型名称格式不正确'}]}><Input placeholder="填写账号可用的模型名称" maxLength={100}/></Form.Item></div>
 <div className="form-grid"><Form.Item name="targetDate" label="建议安排到哪一天" rules={[{required:true}]}><DatePicker allowClear={false}/></Form.Item><Form.Item name="budgetMinutes" label="该日总预算（分钟）" rules={[{required:true}]}><InputNumber min={0} max={1440}/></Form.Item></div>
 <Form.Item name="feedback" label="这次还有什么想告诉教练"><Input.TextArea rows={2} maxLength={2000} placeholder="例如：明天只有 30 分钟；希望继续完成接口测试。"/></Form.Item>
 <Form.Item name="consent" valuePropName="checked" rules={[{validator:(_,v)=>v?Promise.resolve():Promise.reject(new Error('请先确认本次记录发送范围'))}]}><Checkbox>同意将所选记录、近期记录及相关目标和任务发送给所选提供商分析</Checkbox></Form.Item>
 <Button type="primary" htmlType="submit" disabled={!configured||!logOptions.size} loading={create.isPending}>生成分析与次日建议</Button>
 </Form></section>:null}
 {id?<AnalysisPanel id={id} retry={a=>{form.setFieldsValue({sourceLogId:a.sourceLogId,provider:a.provider,model:a.model,budgetMinutes:a.budgetMinutes,targetDate:dayjs(a.targetDate),feedback:a.feedback,consent:false});setParams({logId:a.sourceLogId});window.scrollTo({top:0,behavior:'smooth'});}}/>:null}
 <section className="panel coach-history"><div className="section-heading"><h2>分析历史</h2><span className="muted">来源快照与确认状态都会保留</span></div><LoadingError loading={displayed.isPending} error={displayed.error}/>
 {displayed.data?.items.length?displayed.data.items.map(a=><button className={'history-row '+(a.id===id?'selected':'')} key={a.id} onClick={()=>setParams({analysis:a.id})}><div><strong>{a.targetDate} 的学习建议</strong><span>{a.provider} · {a.model} · {dayjs(a.createdAt).format('MM-DD HH:mm')}</span></div><Tag color={a.status==='FAILED'?'error':a.status==='SUCCEEDED'?'success':'processing'}>{a.suggestionStatus?suggestionLabels[a.suggestionStatus]:analysisLabels[a.status]}</Tag></button>):displayed.data?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有分析，先选择一条学习记录"/>:null}
 {(displayed.data?.total??0)>20?<div className="pagination"><Button disabled={!historyPage} onClick={()=>setHistoryPage(p=>p-1)}>上一页</Button><span>第 {historyPage+1} 页</span><Button disabled={(historyPage+1)*20>=(displayed.data?.total??0)} onClick={()=>setHistoryPage(p=>p+1)}>下一页</Button></div>:null}
 </section></>;
}
function AnalysisPanel({id,retry}:{id:string;retry:(a:Analysis)=>void}){
 const client=useQueryClient();const q=useQuery({queryKey:['analysis',id],queryFn:()=>api<Analysis>('/ai/analyses/'+id),refetchInterval:query=>query.state.data?.status==='PENDING'||query.state.data?.status==='RUNNING'?1000:false});
 const status=q.data?.status;useEffect(()=>{if(status==='SUCCEEDED'||status==='FAILED')void client.invalidateQueries({queryKey:['analyses']});},[status,client]);
 return <section className="panel coach-result"><LoadingError loading={q.isPending} error={q.error}/>{q.data?<>
 <div className="section-heading"><h2>本次分析</h2><Tag>{analysisLabels[q.data.status]}</Tag></div>
 <p className="muted">{q.data.provider} · {q.data.model} · 已尝试 {q.data.attemptCount} 次{q.data.durationMs!==null?' · '+(q.data.durationMs/1000).toFixed(1)+' 秒':''}{q.data.inputTokens!==null?' · 输入 '+q.data.inputTokens+' tokens':''}{q.data.outputTokens!==null?' · 输出 '+q.data.outputTokens+' tokens':''}</p>
 {q.data.status==='PENDING'||q.data.status==='RUNNING'?<Alert type="info" showIcon title="分析正在进行" description="可以离开页面稍后从分析历史继续查看；原始日志已保存，分析不会直接修改任务。"/>:null}
 {q.data.status==='FAILED'?<Alert type="error" showIcon title={q.data.errorMessage??'分析未完成'} description={q.data.errorCode} action={<Button onClick={()=>retry(q.data!)}>重新填写并重试</Button>}/>:null}
 {q.data.result?<><p className="analysis-summary">{q.data.result.summary}</p><Space wrap>{q.data.result.learnedTopics.map((t,i)=><Tag key={i}>{t}</Tag>)}</Space>
 <div className="log-detail"><strong>掌握程度建议，仅为模型推断</strong><p>{q.data.result.masteryAssessment}</p></div>
 {q.data.result.openQuestions.length?<div className="coach-questions"><h3>待解决问题</h3><ul>{q.data.result.openQuestions.map((t,i)=><li key={i}>{t}</li>)}</ul></div>:null}
 <div className="coach-evidence"><h3>分析依据</h3>{q.data.result.evidence.map((e,i)=><blockquote key={i}><p>{e.quote}</p><small>学习记录 {e.logId}</small></blockquote>)}</div>
 <p className="analysis-reason">{q.data.result.planAdjustmentReason}</p>
 {q.data.suggestionId?<SuggestionPanel key={q.data.suggestionId} id={q.data.suggestionId}/>:null}</>:null}
 <details className="snapshot"><summary>查看本次输入来源快照</summary><p className="muted">这些是提交时的记录，不会随之后编辑改变。省略的较长近期记录：{q.data.snapshot.omittedLogs} 条。</p>
 {[q.data.snapshot.source,...q.data.snapshot.recentLogs].map(l=><div className="snapshot-log" key={l.id}><strong>{l.studyDate} · {l.durationMinutes} 分钟</strong><p>{l.content}</p>{l.achievements?<p>成果：{l.achievements}</p>:null}{l.blockers?<p>阻塞：{l.blockers}</p>:null}</div>)}
 <p>关联目标：{q.data.snapshot.goals.map(g=>g.title).join('、')||'暂无'}</p><p>目标日已有任务：{q.data.snapshot.targetTasks.map(t=>t.title).join('、')||'暂无'}</p><p>本次反馈：{q.data.feedback||'无'}</p></details>
 </>:null}</section>;
}
function SuggestionPanel({id}:{id:string}){
 const q=useQuery({queryKey:['suggestion',id],queryFn:()=>api<Suggestion>('/plan-suggestions/'+id)});const [editing,setEditing]=useState(false);const {message}=App.useApp();
 const confirm=useWrite<number,{taskIds:string[]}>(version=>api('/plan-suggestions/'+id+'/confirm','POST',{version}),result=>void message.success(result.taskIds.length?'已确认并写入 '+result.taskIds.length+' 项任务':'已确认休息安排，没有创建任务'));
 const reject=useWrite<number,Suggestion>(version=>api('/plan-suggestions/'+id+'/reject','POST',{version}),()=>void message.success('已拒绝建议，正式任务没有改变'));
 const s=q.data;
 return <div className="suggestion"><LoadingError loading={q.isPending} error={q.error}/>{s?<><div className="section-heading"><h2>{s.targetDate} 的候选任务</h2><Tag>{suggestionLabels[s.status]}</Tag></div>
 <p className="muted">该日预算 {s.analysis.budgetMinutes} 分钟；确认时会计入该日已有任务。修改仅作用于这份草稿。</p>
 {s.stale?<Alert type="warning" showIcon title="来源已变化，不能直接确认" description={s.staleReason}/>:null}
 {s.tasks.length?s.tasks.map(t=><article className="proposal" key={t.itemId}><div className="section-heading"><h3>{t.title}</h3><Tag>{t.estimatedMinutes} 分钟</Tag></div><p>{t.reason}</p><div className="proposal-acceptance"><strong>验收产出</strong><p>{t.acceptanceCriteria}</p></div>{t.existingTaskId?<Tag color="processing">确认后调整已有任务，沿用原任务 ID</Tag>:null}</article>):<Alert showIcon type="info" title="没有新增任务" description="这份建议可以用于休息安排。确认后会保留记录，但不会创建任务。"/>}
 {s.status==='DRAFT'?<Space wrap className="suggestion-actions"><Button disabled={s.stale} onClick={()=>setEditing(true)}>编辑候选任务</Button><Popconfirm title="确认把这份建议写入学习计划？" description="新任务会创建，标记为已有任务的条目会调整原任务。" onConfirm={()=>confirm.mutateAsync(s.version)}><Button type="primary" loading={confirm.isPending} disabled={s.stale}>确认写入计划</Button></Popconfirm><Button danger loading={reject.isPending} onClick={()=>reject.mutate(s.version)}>拒绝建议</Button></Space>:null}
 {s.status==='CONFIRMED'?<Alert type="success" showIcon title={'已确认，共关联 '+s.taskIds.length+' 项正式任务'} description={<NavLink to="/plan">前往学习计划查看</NavLink>}/>:null}
 {editing?<SuggestionEditor suggestion={s} close={()=>setEditing(false)}/>:null}
 </>:null}</div>;
}
function SuggestionEditor({suggestion:s,close}:{suggestion:Suggestion;close:()=>void}){
 interface Values {targetDate:Dayjs;tasks?:ProposedTask[]}
 const save=useWrite<Values,Suggestion>(values=>api('/plan-suggestions/'+s.id,'PATCH',{version:s.version,targetDate:values.targetDate.format('YYYY-MM-DD'),tasks:(values.tasks??[]).map(t=>({...t,goalId:t.goalId||null,existingTaskId:t.existingTaskId||null}))}),close);
 return <Modal open width={720} title="编辑候选任务" onCancel={close} footer={null}><Form layout="vertical" initialValues={{targetDate:dayjs(s.targetDate),tasks:s.tasks}} onFinish={values=>save.mutate(values)}>
 <Form.Item name="targetDate" label="目标日期" rules={[{required:true}]}><DatePicker allowClear={false}/></Form.Item>
 <Form.List name="tasks">{(fields,{add,remove})=><>{fields.map(field=><div className="proposal-editor" key={field.key}>
 <Form.Item name={[field.name,'itemId']} hidden><Input/></Form.Item><Form.Item name={[field.name,'existingTaskId']} hidden><Input/></Form.Item>
 <div className="section-heading"><strong>候选任务 {field.name+1}</strong><Button type="text" danger aria-label={'移除候选任务 '+(field.name+1)} icon={<DeleteOutlined aria-hidden="true"/>} onClick={()=>remove(field.name)}/></div>
 <Form.Item name={[field.name,'title']} label="任务名称" rules={[{required:true,whitespace:true}]}><Input maxLength={200}/></Form.Item>
 <div className="form-grid"><Form.Item name={[field.name,'estimatedMinutes']} label="预计分钟" rules={[{required:true}]}><InputNumber min={1} max={1440}/></Form.Item><Form.Item name={[field.name,'goalId']} label="关联目标"><Select allowClear options={s.analysis.snapshot.goals.map(g=>({value:g.id,label:g.title}))}/></Form.Item></div>
 <Form.Item name={[field.name,'reason']} label="安排理由" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={2} maxLength={2000}/></Form.Item>
 <Form.Item name={[field.name,'acceptanceCriteria']} label="验收产出" rules={[{required:true,whitespace:true}]}><Input.TextArea rows={2} maxLength={5000}/></Form.Item>
 </div>)}<Button icon={<PlusOutlined aria-hidden="true"/>} disabled={fields.length>=3} onClick={()=>add({itemId:crypto.randomUUID(),title:'',reason:'',estimatedMinutes:30,acceptanceCriteria:'',goalId:null,existingTaskId:null})}>添加候选任务</Button></>}</Form.List>
 <div className="modal-actions"><Button onClick={close}>取消</Button><Button type="primary" htmlType="submit" loading={save.isPending}>保存草稿</Button></div></Form></Modal>;
}
