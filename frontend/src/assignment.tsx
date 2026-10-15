import { useState } from 'react';
import { useMutation,useQuery,useQueryClient } from '@tanstack/react-query';
import { App,Button,DatePicker,Form,Modal,Select } from 'antd';
import dayjs from 'dayjs';
import { api } from './api';import type { Task } from './api';
export function Assignment({task}:{task:Task}){
 const [open,setOpen]=useState(false);const [form]=Form.useForm();const client=useQueryClient();const {message}=App.useApp();
 const plans=useQuery({queryKey:['weekly-plan-options'],queryFn:()=>api<{id:string;weekStart:string;theme:string;status:string}[]>('/weekly-plans'),enabled:open});
 const save=useMutation({mutationFn:(v:{weeklyPlanId?:string;scheduledDate:dayjs.Dayjs})=>api('/tasks/'+task.id+'/weekly-plan','PATCH',{version:task.version,weeklyPlanId:v.weeklyPlanId??null,scheduledDate:v.scheduledDate.format('YYYY-MM-DD')}),onSuccess:()=>{void client.invalidateQueries();setOpen(false);void message.success('周归属已更新');},onError:(e:Error)=>void message.error(e.message)});
 return <><Button size="small" onClick={()=>setOpen(true)}>{task.weeklyPlanId?'调整周归属':'分配到周计划'}</Button>{open?<Modal open title="调整任务周归属" footer={null} onCancel={()=>setOpen(false)}><p className="muted">解除归属或转到另一周需要明确保存；跨周时同时选择新日期。</p><Form form={form} layout="vertical" initialValues={{weeklyPlanId:task.weeklyPlanId??undefined,scheduledDate:dayjs(task.scheduledDate)}} onFinish={v=>save.mutate(v)}><Form.Item name="weeklyPlanId" label="归属周计划"><Select allowClear placeholder="不显式归属" loading={plans.isPending} options={plans.data?.filter(p=>p.status==='ACTIVE'||p.id===task.weeklyPlanId).map(p=>({value:p.id,label:p.weekStart+' · '+p.theme}))}/></Form.Item><Form.Item name="scheduledDate" label="归属后的任务日期" rules={[{required:true}]}><DatePicker allowClear={false}/></Form.Item><Button type="primary" htmlType="submit" loading={save.isPending}>保存周归属</Button></Form></Modal>:null}</>;
}
