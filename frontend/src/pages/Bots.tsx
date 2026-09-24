import { useState } from 'react';
import { api } from '../api';
import { useLoad } from '../hooks';
import type { Bot, BotRunResult, Status, StrategyInfo, StrategyType } from '../types';
import { Card, ErrorBox, ParamFields } from '../components/ui';
import { fmtTime, fmtUsd } from '../format';

interface BotForm {
  id?: number;
  name: string;
  symbol: string;
  strategy: StrategyType;
  params: Record<string, number>;
  notional: number;
  enabled: boolean;
}

const EMPTY: BotForm = { name: '', symbol: '', strategy: 'SMA_CROSS', params: {}, notional: 1000, enabled: false };

export function BotsPage({ status }: { status?: Status }) {
  const bots = useLoad(() => api.get<Bot[]>('/api/bots'));
  const strategies = useLoad(() => api.get<StrategyInfo[]>('/api/strategies'));
  const [form, setForm] = useState<BotForm>(EMPTY);
  const [error, setError] = useState<string>();
  const [result, setResult] = useState<BotRunResult>();

  const info = strategies.data?.find((s) => s.type === form.strategy);
  const label = (t: StrategyType) => strategies.data?.find((s) => s.type === t)?.label ?? t;

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(undefined);
    try {
      const body = { ...form, id: undefined };
      if (form.id) await api.put(`/api/bots/${form.id}`, body);
      else await api.post('/api/bots', body);
      setForm(EMPTY);
      void bots.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const run = async (bot: Bot, dryRun: boolean) => {
    if (!dryRun && !window.confirm(`立即运行「${bot.name}」，可能会真实下单（${status?.accountMode === 'live' ? '实盘' : '模拟盘'}）。继续？`)) return;
    setError(undefined);
    try {
      setResult(await api.post<BotRunResult>(`/api/bots/${bot.id}/run?dryRun=${dryRun}`));
      void bots.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const toggle = async (bot: Bot) => {
    try {
      await api.put(`/api/bots/${bot.id}`, { ...bot, enabled: !bot.enabled });
      void bots.reload();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const remove = async (bot: Bot) => {
    if (!window.confirm(`删除「${bot.name}」？已有持仓不会被卖出。`)) return;
    await api.del(`/api/bots/${bot.id}`);
    void bots.reload();
  };

  return (
    <div className="grid bots">
      <Card title={form.id ? '编辑策略机器人' : '新建策略机器人'}>
        <form className="form" onSubmit={save}>
          <label>名称<input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="SPY 均线趋势" /></label>
          <label>股票代码<input required value={form.symbol} onChange={(e) => setForm({ ...form, symbol: e.target.value })} placeholder="SPY" /></label>
          <label>
            策略
            <select value={form.strategy} onChange={(e) => setForm({ ...form, strategy: e.target.value as StrategyType, params: {} })}>
              {strategies.data?.filter((s) => s.type !== 'BUY_AND_HOLD').map((s) => <option key={s.type} value={s.type}>{s.label}</option>)}
            </select>
          </label>
          {info && <p className="muted small">{info.description}</p>}
          {info && <ParamFields defaults={info.defaults} values={form.params} onChange={(params) => setForm({ ...form, params })} />}
          <label>每次买入金额 ($)<input type="number" min="1" required value={form.notional} onChange={(e) => setForm({ ...form, notional: Number(e.target.value) })} /></label>
          <label className="check"><input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />启用定时自动运行</label>
          <div className="row">
            <button type="submit">{form.id ? '保存' : '创建'}</button>
            {form.id && <button type="button" className="ghost" onClick={() => setForm(EMPTY)}>取消</button>}
          </div>
          <ErrorBox error={error} />
        </form>
        <div className="muted small explain">
          <p>机器人按日线运行：每个交易日 {status?.botCron ? <code>{status.botCron}</code> : ''}（{status?.botZone}）检查一次信号。</p>
          <p>信号为「买入」且没有持仓 → 按金额市价买入整股；信号为「卖出」且有持仓 → 全部卖出；其它情况不动。</p>
          <p>每只股票只能有一个机器人，避免互相抢仓位。建议先用「试运行」看信号，在模拟盘跑一段时间再考虑实盘。</p>
          {status && !status.botSchedulerEnabled && <p className="down">定时调度已关闭（BOTS_SCHEDULER_ENABLED=false）。</p>}
        </div>
      </Card>

      <div className="stack">
        {result && (
          <div className={`alert ${result.action === 'NONE' ? '' : 'ok'}`}>
            {result.dryRun ? '试运行' : '已运行'} {result.symbol}：信号 <strong>{result.signal}</strong>，动作 <strong>{result.action}</strong>。{result.message}
          </div>
        )}
        <Card title={`机器人 (${bots.data?.length ?? 0})`}>
          <ErrorBox error={bots.error} />
          <div className="scroll">
            <table className="table">
              <thead><tr><th>名称</th><th>代码</th><th>策略</th><th className="num">金额</th><th>状态</th><th>上次运行</th><th /></tr></thead>
              <tbody>
                {bots.data?.map((b) => (
                  <tr key={b.id}>
                    <td>{b.name}</td>
                    <td className="sym">{b.symbol}</td>
                    <td>{label(b.strategy)} <span className="muted small">{Object.entries(b.params).map(([k, v]) => `${k}=${v}`).join(' ')}</span></td>
                    <td className="num">{fmtUsd(b.notional)}</td>
                    <td><button className={`badge ${b.enabled ? 'ok' : ''}`} onClick={() => toggle(b)}>{b.enabled ? '运行中' : '已暂停'}</button></td>
                    <td className="small">
                      {b.lastRunAt ? <>{fmtTime(b.lastRunAt)}<br />{b.lastSignal} → {b.lastAction}<br /><span className="muted">{b.lastMessage}</span></> : '—'}
                    </td>
                    <td className="actions">
                      <button className="ghost small" onClick={() => run(b, true)}>试运行</button>
                      <button className="ghost small" onClick={() => run(b, false)} disabled={!status?.brokerConfigured}>立即运行</button>
                      <button className="ghost small" onClick={() => setForm({ ...b })}>编辑</button>
                      <button className="ghost small" onClick={() => remove(b)}>删除</button>
                    </td>
                  </tr>
                ))}
                {bots.data?.length === 0 && <tr><td colSpan={7} className="muted">还没有机器人。先在「回测」页找到表现不错的参数，再在左边创建。</td></tr>}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </div>
  );
}
