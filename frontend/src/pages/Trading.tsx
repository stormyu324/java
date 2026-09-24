import { useState } from 'react';
import { api } from '../api';
import { useLoad } from '../hooks';
import type { Account, Clock, Order, OrderOutcome, PendingOrder, Position, Status, TradeLog } from '../types';
import { notifyApprovalsChanged } from '../approvals';
import { ConfirmOrderDialog } from '../components/ConfirmOrderDialog';
import { Card, ErrorBox, Stat } from '../components/ui';
import { fmtNum, fmtPct, fmtTime, fmtUsd, signClass } from '../format';

export function TradingPage({ status }: { status?: Status }) {
  const account = useLoad(() => api.get<Account>('/api/trading/account'));
  const clock = useLoad(() => api.get<Clock>('/api/trading/clock'));
  const positions = useLoad(() => api.get<Position[]>('/api/trading/positions'));
  const orders = useLoad(() => api.get<Order[]>('/api/trading/orders?status=all&limit=50'));
  const logs = useLoad(() => api.get<TradeLog[]>('/api/trading/logs'));
  const [toConfirm, setToConfirm] = useState<PendingOrder>();
  const [notice, setNotice] = useState<string>();

  /** Orders that need the owner's confirmation open the password dialog straight away. */
  const handleOutcome = (o: OrderOutcome) => {
    if (o.status === 'PENDING_APPROVAL' && o.pending) {
      notifyApprovalsChanged();
      setToConfirm(o.pending);
    }
    refreshAll();
  };

  const refreshAll = () => {
    void account.reload();
    void positions.reload();
    void orders.reload();
    void logs.reload();
  };

  if (status && !status.brokerConfigured) {
    return (
      <Card title="交易">
        <p>还没有连接券商。到 <a href="https://alpaca.markets" target="_blank" rel="noreferrer">alpaca.markets</a> 注册，
          在 Paper Trading 页面生成 API Key，然后设置环境变量后重启后端：</p>
        <pre>ALPACA_KEY_ID=你的KeyID{'\n'}ALPACA_SECRET_KEY=你的Secret{'\n'}ALPACA_PAPER=true</pre>
      </Card>
    );
  }

  const closeConfirm = (result?: OrderOutcome) => {
    setToConfirm(undefined);
    setNotice(result?.order
      ? `已提交到券商：${result.order.side} ${result.order.symbol}，状态 ${result.order.status}`
      : '订单未确认，可稍后在「待确认」页处理（过期前有效）。');
    refreshAll();
  };

  const a = account.data;
  const dayPl = a ? Number(a.equity) - Number(a.last_equity) : undefined;

  return (
    <div className="stack">
      <Card
        title={<>账户 <span className={`badge ${status?.accountMode === 'live' ? 'danger' : 'ok'}`}>{status?.accountMode === 'live' ? '实盘' : '模拟盘'}</span></>}
        actions={
          <>
            {clock.data && <span className={`badge ${clock.data.is_open ? 'ok' : ''}`}>{clock.data.is_open ? '开盘中' : `休市 · 下次开盘 ${fmtTime(clock.data.next_open)}`}</span>}
            <button className="ghost" onClick={refreshAll}>刷新</button>
          </>
        }
      >
        <ErrorBox error={account.error} />
        {a && (
          <div className="stats">
            <Stat label="总资产" value={fmtUsd(a.equity)} />
            <Stat label="今日盈亏" value={fmtUsd(dayPl)} tone={signClass(dayPl)} />
            <Stat label="现金" value={fmtUsd(a.cash)} />
            <Stat label="购买力" value={fmtUsd(a.buying_power)} />
            <Stat label="账户状态" value={a.trading_blocked ? '禁止交易' : a.status} />
          </div>
        )}
      </Card>

      {notice && <div className="alert">{notice}</div>}
      {toConfirm && <ConfirmOrderDialog order={toConfirm} onClose={closeConfirm} />}

      <div className="grid trading">
        <OrderForm status={status} onPlaced={handleOutcome} />
        <Card title={`持仓 (${positions.data?.length ?? 0})`}>
          <ErrorBox error={positions.error} />
          <div className="scroll">
            <table className="table">
              <thead>
                <tr><th>代码</th><th className="num">数量</th><th className="num">成本</th><th className="num">现价</th><th className="num">市值</th><th className="num">浮动盈亏</th><th /></tr>
              </thead>
              <tbody>
                {positions.data?.map((p) => (
                  <tr key={p.symbol}>
                    <td className="sym">{p.symbol}</td>
                    <td className="num">{fmtNum(p.qty, 0)}</td>
                    <td className="num">{fmtNum(p.avg_entry_price)}</td>
                    <td className="num">{fmtNum(p.current_price)}</td>
                    <td className="num">{fmtUsd(p.market_value)}</td>
                    <td className={`num ${signClass(p.unrealized_pl)}`}>
                      {fmtUsd(p.unrealized_pl)} <span className="small">({fmtPct(Number(p.unrealized_plpc) * 100)})</span>
                    </td>
                    <td>
                      <ConfirmButton
                        label="平仓"
                        confirm={status?.approvalRequired ? `为 ${p.symbol} 生成平仓订单？下一步需要输入密码确认。` : `确定以市价卖出全部 ${p.symbol}？`}
                        onConfirm={() => api.del<OrderOutcome>(`/api/trading/positions/${p.symbol}`).then(handleOutcome)}
                      />
                    </td>
                  </tr>
                ))}
                {positions.data?.length === 0 && <tr><td colSpan={7} className="muted">暂无持仓</td></tr>}
              </tbody>
            </table>
          </div>
        </Card>
      </div>

      <Card title="订单">
        <ErrorBox error={orders.error} />
        <div className="scroll">
          <table className="table">
            <thead>
              <tr><th>提交时间</th><th>代码</th><th>方向</th><th>类型</th><th className="num">数量</th><th className="num">限价</th><th className="num">成交</th><th>状态</th><th /></tr>
            </thead>
            <tbody>
              {orders.data?.map((o) => (
                <tr key={o.id}>
                  <td>{fmtTime(o.submitted_at)}</td>
                  <td className="sym">{o.symbol}</td>
                  <td className={o.side === 'buy' ? 'up' : 'down'}>{o.side === 'buy' ? '买入' : '卖出'}</td>
                  <td>{o.type}</td>
                  <td className="num">{o.qty ?? (o.notional ? fmtUsd(o.notional) : '—')}</td>
                  <td className="num">{fmtNum(o.limit_price)}</td>
                  <td className="num">{o.filled_qty ? `${o.filled_qty} @ ${fmtNum(o.filled_avg_price)}` : '—'}</td>
                  <td>{o.status}</td>
                  <td>
                    {['new', 'accepted', 'partially_filled', 'pending_new'].includes(o.status) && (
                      <ConfirmButton label="撤单" confirm="确定撤销该订单？"
                        onConfirm={() => api.del(`/api/trading/orders/${o.id}`).then(refreshAll)} />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="下单记录（含被风控拒绝的）">
        <ErrorBox error={logs.error} />
        <div className="scroll short">
          <table className="table compact">
            <thead><tr><th>时间</th><th>来源</th><th>账户</th><th>代码</th><th>方向</th><th className="num">数量</th><th>结果</th><th>说明</th></tr></thead>
            <tbody>
              {logs.data?.map((l) => (
                <tr key={l.id}>
                  <td>{fmtTime(l.time)}</td><td>{l.source}</td><td>{l.mode}</td><td className="sym">{l.symbol}</td>
                  <td>{l.side}</td><td className="num">{l.qty ?? '全部'}</td>
                  <td><span className={`badge ${l.status === 'SUBMITTED' ? 'ok' : l.status === 'PENDING_APPROVAL' ? 'warn' : 'danger'}`}>{l.status}</span></td>
                  <td className="small">{l.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

function OrderForm({ status, onPlaced }: { status?: Status; onPlaced: (o: OrderOutcome) => void }) {
  const [form, setForm] = useState({ symbol: '', side: 'BUY', qty: 1, type: 'MARKET', limitPrice: '', timeInForce: 'DAY' });
  const [error, setError] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const desc = `${form.side === 'BUY' ? '买入' : '卖出'} ${form.qty} 股 ${form.symbol.toUpperCase()}（${form.type === 'MARKET' ? '市价' : `限价 ${form.limitPrice}`}）`;
    // With confirmation required, the password dialog that follows is the confirmation step.
    if (!status?.approvalRequired && !window.confirm(`确认${desc}？`)) return;
    setBusy(true);
    setError(undefined);
    setMessage(undefined);
    try {
      const o = await api.post<OrderOutcome>('/api/trading/orders', {
        ...form,
        limitPrice: form.type === 'LIMIT' ? Number(form.limitPrice) : null,
      });
      setMessage(o.status === 'PENDING_APPROVAL'
        ? `已生成待确认订单 #${o.pending?.id}：${desc}`
        : `已提交：${desc}，状态 ${o.order?.status}`);
      onPlaced(o);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="下单">
      <form className="form" onSubmit={submit}>
        <label>股票代码<input required value={form.symbol} onChange={(e) => setForm({ ...form, symbol: e.target.value })} placeholder="AAPL" /></label>
        <div className="segmented full">
          <button type="button" className={form.side === 'BUY' ? 'active buy' : ''} onClick={() => setForm({ ...form, side: 'BUY' })}>买入</button>
          <button type="button" className={form.side === 'SELL' ? 'active sell' : ''} onClick={() => setForm({ ...form, side: 'SELL' })}>卖出</button>
        </div>
        <div className="row">
          <label>数量（股）<input type="number" min="0" step="any" required value={form.qty} onChange={(e) => setForm({ ...form, qty: Number(e.target.value) })} /></label>
          <label>
            类型
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="MARKET">市价</option>
              <option value="LIMIT">限价</option>
            </select>
          </label>
        </div>
        {form.type === 'LIMIT' && (
          <div className="row">
            <label>限价<input type="number" step="0.01" required value={form.limitPrice} onChange={(e) => setForm({ ...form, limitPrice: e.target.value })} /></label>
            <label>
              有效期
              <select value={form.timeInForce} onChange={(e) => setForm({ ...form, timeInForce: e.target.value })}>
                <option value="DAY">当日有效</option>
                <option value="GTC">撤销前有效</option>
              </select>
            </label>
          </div>
        )}
        <button type="submit" disabled={busy || !status?.tradingEnabled}>
          {busy ? '提交中…' : status?.approvalRequired ? '下一步：确认订单' : '提交订单'}
        </button>
        <ErrorBox error={error} />
        {message && <div className="alert ok">{message}</div>}
        {status && (
          <p className="muted small">
            风控：单笔上限 {fmtUsd(status.maxOrderNotional)}，最多同时持有 {status.maxOpenPositions} 只股票。
            {status.approvalRequired && ' 每笔订单都需要输入密码确认后才会发送到券商。'}
          </p>
        )}
      </form>
    </Card>
  );
}

function ConfirmButton({ label, confirm, onConfirm }: { label: string; confirm: string; onConfirm: () => Promise<unknown> }) {
  const [busy, setBusy] = useState(false);
  return (
    <button
      className="ghost small"
      disabled={busy}
      onClick={async () => {
        if (!window.confirm(confirm)) return;
        setBusy(true);
        try {
          await onConfirm();
        } catch (e) {
          window.alert((e as Error).message);
        } finally {
          setBusy(false);
        }
      }}
    >{label}</button>
  );
}
