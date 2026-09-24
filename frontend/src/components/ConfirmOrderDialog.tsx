import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { notifyApprovalsChanged } from '../approvals';
import type { OrderOutcome, PendingOrder } from '../types';
import { fmtNum, fmtTime, fmtUsd } from '../format';

export const ACTION_LABEL: Record<PendingOrder['action'], string> = { BUY: '买入', SELL: '卖出', CLOSE: '全部平仓' };

export function sourceLabel(source: string) {
  if (source === 'manual') return '手动下单';
  if (source.startsWith('bot:')) return `机器人 #${source.slice(4)}`;
  return source;
}

/**
 * The owner's confirmation step: shows the full order and requires the login password.
 * Nothing is sent to the broker until this succeeds.
 */
export function ConfirmOrderDialog({
  order, onClose,
}: {
  order: PendingOrder;
  onClose: (result?: OrderOutcome) => void;
}) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(Date.now());
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    input.current?.focus();
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  const left = Math.max(0, Math.floor((new Date(order.expiresAt).getTime() - now) / 1000));
  const live = order.mode === 'live';
  const est = order.qty != null ? (order.limitPrice ?? order.referencePrice ?? 0) * order.qty : undefined;

  const confirm = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      const result = await api.post<OrderOutcome>(`/api/trading/approvals/${order.id}/approve`, { password });
      notifyApprovalsChanged();
      onClose(result);
    } catch (err) {
      setError((err as Error).message);
      setPassword('');
      notifyApprovalsChanged();
    } finally {
      setBusy(false);
    }
  };

  const reject = async () => {
    setBusy(true);
    try {
      await api.post(`/api/trading/approvals/${order.id}/reject`);
      notifyApprovalsChanged();
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={() => !busy && onClose()}>
      <form className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title"
        onClick={(e) => e.stopPropagation()} onSubmit={confirm}>
        <h2 id="confirm-title">确认订单 #{order.id}</h2>
        {live && <div className="alert error">实盘账户：确认后将使用<strong>真实资金</strong>下单。</div>}
        <table className="table compact">
          <tbody>
            <tr><td className="muted">来源</td><td>{sourceLabel(order.source)}</td></tr>
            <tr><td className="muted">账户</td><td>{live ? '实盘' : '模拟盘'}</td></tr>
            <tr>
              <td className="muted">操作</td>
              <td className={order.action === 'BUY' ? 'up' : 'down'}><strong>{ACTION_LABEL[order.action]} {order.symbol}</strong></td>
            </tr>
            {order.action !== 'CLOSE' && (
              <>
                <tr><td className="muted">数量</td><td>{order.qty} 股</td></tr>
                <tr><td className="muted">类型</td><td>{order.type === 'LIMIT' ? `限价 ${fmtNum(order.limitPrice)}（${order.timeInForce === 'GTC' ? '撤销前有效' : '当日有效'}）` : '市价（当日有效）'}</td></tr>
                <tr><td className="muted">生成时价格</td><td>{fmtNum(order.referencePrice)}</td></tr>
                <tr><td className="muted">预估金额</td><td><strong>{fmtUsd(est)}</strong></td></tr>
              </>
            )}
            {order.action === 'CLOSE' && <tr><td className="muted">数量</td><td>该股票的全部持仓，市价卖出</td></tr>}
            <tr><td className="muted">生成时间</td><td>{fmtTime(order.createdAt)}</td></tr>
            <tr>
              <td className="muted">有效期</td>
              <td className={left < 120 ? 'down' : ''}>{left > 0 ? `剩余 ${Math.floor(left / 60)} 分 ${left % 60} 秒` : '已过期'}</td>
            </tr>
          </tbody>
        </table>
        <label>
          输入登录密码以确认下单
          <input ref={input} type="password" autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <div className="alert error">{error}</div>}
        <p className="muted small">确认时会重新检查风控（金额上限、持仓数、交易开关）。价格可能已经变化。</p>
        <div className="row end">
          <button type="button" className="ghost" onClick={() => onClose()} disabled={busy}>稍后处理</button>
          <button type="button" className="ghost danger" onClick={reject} disabled={busy}>拒绝</button>
          <button type="submit" className={live ? 'danger-fill' : ''} disabled={busy || left <= 0 || !password}>
            {busy ? '提交中…' : '确认下单'}
          </button>
        </div>
      </form>
    </div>
  );
}
