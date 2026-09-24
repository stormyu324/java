import { useEffect, useState } from 'react';
import { api } from '../api';
import { onApprovalsChanged } from '../approvals';
import { useLoad } from '../hooks';
import type { OrderOutcome, PendingOrder, Status } from '../types';
import { Card, ErrorBox } from '../components/ui';
import { ACTION_LABEL, ConfirmOrderDialog, sourceLabel } from '../components/ConfirmOrderDialog';
import { fmtNum, fmtTime } from '../format';

const STATUS_LABEL: Record<PendingOrder['status'], [string, string]> = {
  PENDING: ['待确认', 'warn'],
  APPROVED: ['已确认下单', 'ok'],
  REJECTED: ['已拒绝', ''],
  EXPIRED: ['已过期', ''],
  FAILED: ['确认时被拒', 'danger'],
};

export function ApprovalsPage({ status }: { status?: Status }) {
  const pending = useLoad(() => api.get<PendingOrder[]>('/api/trading/approvals'));
  const history = useLoad(() => api.get<PendingOrder[]>('/api/trading/approvals?all=true'));
  const [open, setOpen] = useState<PendingOrder>();
  const [done, setDone] = useState<string>();

  useEffect(() => onApprovalsChanged(() => {
    void pending.reload();
    void history.reload();
  }), [pending.reload, history.reload]);

  useEffect(() => {
    const t = setInterval(() => void pending.reload(), 20_000);
    return () => clearInterval(t);
  }, [pending.reload]);

  const closed = (result?: OrderOutcome) => {
    setOpen(undefined);
    if (result?.order) setDone(`已提交到券商：${result.order.side} ${result.order.symbol}，状态 ${result.order.status}`);
  };

  return (
    <div className="stack">
      <Card title="待确认订单">
        {status && (
          <p className="muted small">
            {status.approvalRequired
              ? `当前${status.accountMode === 'live' ? '实盘' : '模拟盘'}账户的所有订单（手动、机器人、平仓）都要在这里输入密码确认后才会发送到券商。未确认的订单 ${status.approvalTtlMinutes} 分钟后自动过期。`
              : '当前是模拟盘，订单直接发送，不需要确认。连接实盘账户后，所有订单都必须在这里确认。'}
          </p>
        )}
        {done && <div className="alert ok">{done}</div>}
        <ErrorBox error={pending.error} />
        <div className="scroll">
          <table className="table">
            <thead><tr><th>#</th><th>生成时间</th><th>来源</th><th>操作</th><th className="num">数量</th><th className="num">参考价</th><th>过期时间</th><th /></tr></thead>
            <tbody>
              {pending.data?.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td>
                  <td>{fmtTime(p.createdAt)}</td>
                  <td>{sourceLabel(p.source)}</td>
                  <td className={p.action === 'BUY' ? 'up' : 'down'}><strong>{ACTION_LABEL[p.action]} {p.symbol}</strong></td>
                  <td className="num">{p.qty ?? '全部'}</td>
                  <td className="num">{p.type === 'LIMIT' ? `限 ${fmtNum(p.limitPrice)}` : fmtNum(p.referencePrice)}</td>
                  <td>{fmtTime(p.expiresAt)}</td>
                  <td><button onClick={() => { setDone(undefined); setOpen(p); }}>确认 / 拒绝</button></td>
                </tr>
              ))}
              {pending.data?.length === 0 && <tr><td colSpan={8} className="muted">没有待确认的订单</td></tr>}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="确认记录">
        <ErrorBox error={history.error} />
        <div className="scroll short">
          <table className="table compact">
            <thead><tr><th>#</th><th>生成时间</th><th>来源</th><th>账户</th><th>操作</th><th className="num">数量</th><th>结果</th><th>处理时间</th><th>说明</th></tr></thead>
            <tbody>
              {history.data?.map((p) => (
                <tr key={p.id}>
                  <td>{p.id}</td>
                  <td>{fmtTime(p.createdAt)}</td>
                  <td>{sourceLabel(p.source)}</td>
                  <td>{p.mode === 'live' ? '实盘' : '模拟盘'}</td>
                  <td>{ACTION_LABEL[p.action]} {p.symbol}</td>
                  <td className="num">{p.qty ?? '全部'}</td>
                  <td><span className={`badge ${STATUS_LABEL[p.status][1]}`}>{STATUS_LABEL[p.status][0]}</span></td>
                  <td>{fmtTime(p.decidedAt)}</td>
                  <td className="small">{p.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {open && <ConfirmOrderDialog order={open} onClose={closed} />}
    </div>
  );
}
