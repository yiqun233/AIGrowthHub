export type TaskStatus='TODO'|'IN_PROGRESS'|'DONE'|'CANCELLED';
export interface Goal {id:string;title:string;description:string;startDate:string|null;targetDate:string|null;priority:number;status:'ACTIVE'|'ARCHIVED';version:number}
export interface LearningStage {id:string;title:string;desiredOutcome:string;acceptanceCriteria:string;startDate:string;targetDate:string|null;status:'ACTIVE'|'PAUSED'|'CLOSED';outcome:'ACHIEVED'|'PARTIAL'|'RESCOPED'|'ABANDONED'|null;version:number}
export interface Task {id:string;goalId:string|null;title:string;detail:string;scheduledDate:string;estimateMinutes:number;priority:number;status:TaskStatus;acceptanceCriteria:string;completedAt:string|null;version:number;weeklyPlanId?:string|null;learningStageId?:string|null}
export interface StudyLog {id:string;studyDate:string;content:string;durationMinutes:number;activityType?:string;achievements:string;blockers:string;taskIds:string[];version:number;learningStageId?:string|null}
export interface Page<T> {items:T[];total:number;page:number;size:number}
export interface Dashboard {date:string;timezone:string;tasks:Task[];recentLogs:StudyLog[];plannedCount:number;completedCount:number;weekDurationMinutes:number;overdueCount:number}
export class ApiError extends Error { constructor(message:string,public code:string,public status:number){super(message);} }
export async function api<T>(path:string,method='GET',body?:unknown,headers:Record<string,string>={}):Promise<T>{
 const securityHeaders:Record<string,string>={};if(!['GET','HEAD','OPTIONS'].includes(method)){const session=await fetch('/api/v1/auth/session',{cache:'no-store'});if(!session.ok)throw new ApiError('无法校验会话，请刷新页面重试','SESSION_FAILED',session.status);const state=await session.json();if(state.csrfToken)securityHeaders['X-CSRF-TOKEN']=state.csrfToken;}
 const response=await fetch('/api/v1'+path,{method,headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...securityHeaders,...headers},body:body===undefined?undefined:JSON.stringify(body)});
 if(response.status===401)window.dispatchEvent(new Event('growthhub:unauthorized'));
 if(!response.ok){const error=await response.json().catch(()=>({message:'服务暂时不可用'}));const fields=error.fieldErrors?Object.values(error.fieldErrors).join('；'):'';throw new ApiError((error.message||'请求失败，请稍后重试')+(fields?'：'+fields:''),error.code||'REQUEST_FAILED',response.status);}
 const value=await response.json();if((path==='/settings'||path==='/auth/session')&&typeof value.timezone==='string')businessTimezone=value.timezone;return value as T;
}
let businessTimezone='Asia/Shanghai';
export function today(){return new Intl.DateTimeFormat('sv-SE',{timeZone:businessTimezone,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());}

export type AiProvider='OPENAI'|'DEEPSEEK'|'ANTHROPIC'|'GEMINI'|'QWEN'|'ZHIPU'|'MOONSHOT'|'DOUBAO'|'OPENROUTER'|'CUSTOM';
export type AnalysisStatus='PENDING'|'RUNNING'|'SUCCEEDED'|'FAILED';
export type SuggestionStatus='DRAFT'|'CONFIRMED'|'REJECTED'|'SUPERSEDED';
export interface AiSettings {version:number;provider:AiProvider;model:string;dailyMinutes:number;preferences:string;timezone:string;dailyLimit:number;timeoutSeconds:number;providers:{id:AiProvider;label:string;configured:boolean;defaultModel:string}[]}
export interface AiRequest {sourceLogId:string;targetDate:string;budgetMinutes:number;provider:AiProvider;model:string;feedback:string}
export interface ProposedTask {itemId:string;title:string;reason:string;estimatedMinutes:number;acceptanceCriteria:string;goalId:string|null;existingTaskId:string|null}
export interface AiResult {summary:string;learnedTopics:string[];evidence:{logId:string;quote:string}[];openQuestions:string[];masteryAssessment:string;tomorrowTasks:ProposedTask[];planAdjustmentReason:string}
export interface Snapshot {source:StudyLog;recentLogs:StudyLog[];goals:Goal[];openTasks:Task[];targetTasks:Task[];targetDate:string;budgetMinutes:number;feedback:string;preferences:string;omittedLogs:number}
export interface AnalysisSummary {id:string;sourceLogId:string;targetDate:string;provider:AiProvider;model:string;status:AnalysisStatus;suggestionId:string|null;suggestionStatus:SuggestionStatus|null;createdAt:string}
export interface Analysis extends AnalysisSummary {budgetMinutes:number;feedback:string;result:AiResult|null;snapshot:Snapshot;attemptCount:number;durationMs:number|null;inputTokens:number|null;outputTokens:number|null;errorCode:string|null;errorMessage:string|null}
export interface Suggestion {id:string;analysisId:string;targetDate:string;status:SuggestionStatus;version:number;tasks:ProposedTask[];taskIds:string[];confirmedAt:string|null;stale:boolean;staleReason:string|null;analysis:Analysis}
