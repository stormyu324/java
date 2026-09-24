import { useEffect, useState } from 'react';
import { api } from '../api';
import { useLoad } from '../hooks';
import type { BacktestResult, Metrics, StrategyInfo, StrategyType } from '../types';
import { Card, ErrorBox, ParamFields, Stat } from '../components/ui';
import { EquityChart } from '../components/EquityChart';
import { fmtDate, fmtNum, fmtPct, fmtUsd, isoDate, signClass } from '../format';

export function BacktestPage() {
  const strategies = useLoad(() => api.get<StrategyInfo[]>('/api/strategies'));
  const [form, setForm] = useState({
    symbol: 'SPY',
    strategy: 'SMA_CROSS' as StrategyType,
    params: {} as Record<string, number>,
    from: isoDate(new Date(Date.now() - 5 * 365 * 86400_000)),
    to: isoDate(new Date()),
    initialCapital: 100000,
    commissionBps: 0,
    slippageBps: 5,
  });
  const [result, setResult] = useState<BacktestResult>();
  const [error, setError] = useState<string>();
  const [running, setRunning] = useState(false);

  const info = strategies.data?.find((s) => s.type === form.strategy);
  useEffect(() => setForm((f) => ({ ...f, params: {} })), [form.strategy]);

  const run = async (e: React.FormEvent) => {
    e.preventDefault();
    setRunning(true);
    setError(undefined);
    try {
      setResult(await api.post<BacktestResult>('/api/backtest', form));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="grid backtest">
      <Card title="策略回测">
        <form className="form" onSubmit={run}>
          <label>股票代码<input value={form.symbol} onChange={(e) => setForm({ ...form, symbol: e.target.value })} /></label>
          <label>
            策略
            <select value={form.strategy} onChange={(e) => setForm({ ...form, strategy: e.target.value as StrategyType })}>
              {strategies.data?.map((s) => <option key={s.type} value={s.type}>{s.label}</option>)}
            </select>
          </label>
          {info && <p className="muted small">{info.description}</p>}
          {info && <ParamFields defaults={info.defaults} values={form.params} onChange={(params) => setForm({ ...form, params })} />}
          <div className="row">
            <label>开始<input type="date" value={form.from} onChange={(e) => setForm({ ...form, from: e.target.value })} /></label>
            <label>结束<input type="date" value={form.to} onChange={(e) => setForm({ ...form, to: e.target.value })} /></label>
          </div>
          <label>初始资金 ($)<input type="number" value={form.initialCapital} onChange={(e) => setForm({ ...form, initialCapital: Number(e.target.value) })} /></label>
          <div className="row">
            <label>手续费 (bp)<input type="number" step="0.1" value={form.commissionBps} onChange={(e) => setForm({ ...form, commissionBps: Number(e.target.value) })} /></label>
            <label>滑点 (bp)<input type="number" step="0.1" value={form.slippageBps} onChange={(e) => setForm({ ...form, slippageBps: Number(e.target.value) })} /></label>
          </div>
          <button type="submit" disabled={running}>{running ? '回测中…' : '开始回测'}</button>
          <ErrorBox error={error ?? strategies.error} />
          <p className="muted small">
            日线回测，只做多。信号在当日收盘产生、次日开盘成交（避免未来函数），整股买入，1bp = 0.01%。
          </p>
        </form>
      </Card>

      {result && (
        <div className="stack">
          {result.dataSource === 'demo' && <div className="alert warn">本次回测使用的是随机演示数据，结果没有参考意义。</div>}
          <Card title={`${result.symbol} 回测结果`}>
            <div className="stats">
              <Stat label="总收益" value={fmtPct(result.metrics.totalReturnPct)} tone={signClass(result.metrics.totalReturnPct)} />
              <Stat label="年化收益" value={fmtPct(result.metrics.cagrPct)} tone={signClass(result.metrics.cagrPct)} />
              <Stat label="最大回撤" value={`-${fmtNum(result.metrics.maxDrawdownPct)}%`} tone="down" />
              <Stat label="夏普比率" value={fmtNum(result.metrics.sharpe)} />
              <Stat label="胜率" value={`${fmtNum(result.metrics.winRatePct, 1)}%`} />
              <Stat label="交易次数" value={result.metrics.trades} />
            </div>
            <EquityChart points={result.equity} />
            <MetricsTable strategy={result.metrics} benchmark={result.benchmark} />
          </Card>
          <Card title={`交易明细 (${result.trades.length})`}>
            <div className="scroll">
              <table className="table">
                <thead>
                  <tr><th>买入日期</th><th className="num">买入价</th><th>卖出日期</th><th className="num">卖出价</th><th className="num">股数</th><th className="num">盈亏</th><th className="num">收益率</th></tr>
                </thead>
                <tbody>
                  {result.trades.map((t, i) => (
                    <tr key={i}>
                      <td>{fmtDate(t.entryTime)}</td>
                      <td className="num">{fmtNum(t.entryPrice)}</td>
                      <td>{t.open ? <span className="badge">持仓中</span> : fmtDate(t.exitTime)}</td>
                      <td className="num">{fmtNum(t.exitPrice)}</td>
                      <td className="num">{t.shares}</td>
                      <td className={`num ${signClass(t.pnl)}`}>{fmtUsd(t.pnl)}</td>
                      <td className={`num ${signClass(t.returnPct)}`}>{fmtPct(t.returnPct)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}

function MetricsTable({ strategy, benchmark }: { strategy: Metrics; benchmark: Metrics }) {
  const rows: [string, (m: Metrics) => string][] = [
    ['期末资金', (m) => fmtUsd(m.finalEquity)],
    ['总收益', (m) => fmtPct(m.totalReturnPct)],
    ['年化收益 (CAGR)', (m) => fmtPct(m.cagrPct)],
    ['最大回撤', (m) => `-${fmtNum(m.maxDrawdownPct)}%`],
    ['年化波动率', (m) => `${fmtNum(m.volatilityPct)}%`],
    ['夏普比率', (m) => fmtNum(m.sharpe)],
    ['持仓时间占比', (m) => `${fmtNum(m.exposurePct, 1)}%`],
  ];
  return (
    <table className="table compact">
      <thead><tr><th>指标</th><th className="num">策略</th><th className="num">买入持有</th></tr></thead>
      <tbody>
        {rows.map(([label, f]) => (
          <tr key={label}><td>{label}</td><td className="num">{f(strategy)}</td><td className="num muted">{f(benchmark)}</td></tr>
        ))}
      </tbody>
    </table>
  );
}
