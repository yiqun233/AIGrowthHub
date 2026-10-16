import { NavLink } from 'react-router-dom';
import { Button } from 'antd';

export function Guide(){
 return <>
  <header className="page-header"><div><div className="eyebrow">快速上手</div><h1>使用说明</h1><p>围绕一个主题持续实践，记录按需进行，复盘帮助你决定下一步。</p></div><NavLink to="/today"><Button type="primary">回到今日</Button></NavLink></header>
  <section className="panel guide-section" aria-labelledby="guide-loop-title">
   <h2 id="guide-loop-title">一个主题的完整闭环</h2>
   <ol className="guide-steps">
    <li><strong>学习主题</strong><span>写清这轮想掌握什么，以及怎样算有成果。一个主题可以跨越多个星期；目标日期只是参考。</span></li>
    <li><strong>周计划与任务安排</strong><span>每周按实际时间安排任务和预算。任务是可执行的小步骤，不要求每天都安排。</span></li>
    <li><strong>记录真实实践</strong><span>有学习、开发实践、休息或阻塞时都可以记一笔。漏记或休息不算失败；日志可以关联主题和任务。</span></li>
    <li><strong>每周回看</strong><span>查看当周日志、排期和成果，写下收获、阻塞与调整。周复盘可手写，也可选择生成 AI 草稿。</span></li>
    <li><strong>阶段复盘并选择下一轮</strong><span>对照验收条件和阶段证据，确认达成、部分达成、调整范围或放弃，再由你决定下一主题或调整长期方向。</span></li>
   </ol>
   <p className="guide-note">无需每日打卡。阶段和周复盘只汇总明确关联的资料，不会把未关联的历史记录自动算进去。</p>
  </section>
  <section className="panel guide-section" aria-labelledby="guide-terms-title">
   <h2 id="guide-terms-title">这些页面分别做什么</h2>
   <dl className="guide-terms">
    <div><dt><NavLink to="/goals">长期目标</NavLink></dt><dd>保持几个月或更长时间的方向，例如“能独立交付 AI 应用”。不负责拆解每天做什么。长期目标与学习主题目前没有直接绑定；可在任务上同时关联两者。</dd></div>
    <div><dt><NavLink to="/stages">学习主题</NavLink></dt><dd>当前一轮要推进的具体主题，例如“掌握检索增强生成并交付一个可验证原型”。可跨多个周计划。</dd></div>
    <div><dt><NavLink to="/weekly">周计划与复盘</NavLink></dt><dd>这一周想推进的重点和可用时间；周末可回看实际记录并复盘。</dd></div>
    <div><dt><NavLink to="/plan">任务安排</NavLink></dt><dd>带日期、预计时长和验收产出的具体行动。改期或取消由你决定。</dd></div>
    <div><dt><NavLink to="/logs">学习日志</NavLink></dt><dd>保存实际发生的实践、成果和阻塞，AI 建议和复盘以这些原始记录为依据。</dd></div>
   </dl>
  </section>
  <section className="panel guide-section" aria-labelledby="guide-ai-title">
   <h2 id="guide-ai-title">AI 会做什么，以及不会做什么</h2>
   <ul className="guide-list">
    <li>每日建议、周复盘和阶段总结都由你主动发起；没有配置 AI 时，手工记录和复盘仍可使用。</li>
    <li>提交 AI 前会显示本次发送范围并要求同意。模型的判断是建议，引用证据应回到原记录核对。</li>
    <li>采用 AI 内容只会填入可编辑草稿。确认任务、确认复盘和结束阶段都需要你操作。</li>
    <li>来源有变化时，先保存自己的文字，再刷新快照并重新核对；确认后的复盘保留当时的历史依据。</li>
    <li>目前阶段预期结果和验收条件会用于阶段总结；每日 AI 与周 AI 尚未自动把这些阶段信息带入分析上下文。每日和周建议需结合主题验收要求自行核对。</li>
   </ul>
   <div className="guide-links"><NavLink to="/coach">每日 AI 教练</NavLink><NavLink to="/weekly">周复盘</NavLink><NavLink to="/stages">阶段总结</NavLink></div>
  </section>
  <section className="panel guide-section" aria-labelledby="guide-keyboard-title">
   <h2 id="guide-keyboard-title">键盘操作</h2>
   <ul className="guide-list">
    <li>按 Tab 前进、Shift+Tab 后退；焦点框会标出当前控件。</li>
    <li>按 Enter 或空格激活按钮、链接和选项；输入框可直接键入，日期和下拉选项可用键盘选择。</li>
    <li>打开弹窗后按 Esc 关闭；关闭后焦点会回到打开弹窗的控件。</li>
    <li>在任意页面按 Tab 后，可先用“跳到主要内容”跳过侧栏导航。</li>
   </ul>
  </section>
 </>;
}
