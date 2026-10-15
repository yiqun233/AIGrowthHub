export type TaskStatus='TODO'|'IN_PROGRESS'|'DONE'|'CANCELLED';
export interface Goal {id:string;title:string;description:string;startDate:string|null;targetDate:string|null;priority:number;status:'ACTIVE'|'ARCHIVED';version:number}
export interface Task {id:string;goalId:string|null;title:string;detail:string;scheduledDate:string;estimateMinutes:number;priority:number;status:TaskStatus;acceptanceCriteria:string;completedAt:string|null;version:number}
export interface StudyLog {id:string;studyDate:string;content:string;durationMinutes:number;achievements:string;blockers:string;taskIds:string[];version:number}
export interface Page<T> {items:T[];total:number;page:number;size:number}
export interface Dashboard {date:string;timezone:string;tasks:Task[];recentLogs:StudyLog[];plannedCount:number;completedCount:number;weekDurationMinutes:number;overdueCount:number}
export async function api<T>(path:string,method='GET',body?:unknown):Promise<T>{
 const response=await fetch('/api/v1'+path,{method,headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 if(!response.ok){const error=await response.json().catch(()=>({message:'服务暂时不可用'}));const fields=error.fieldErrors?Object.values(error.fieldErrors).join('；'):'';throw new Error(error.message+(fields?'：'+fields:''));}
 return response.json() as Promise<T>;
}
export function today(){return new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());}
