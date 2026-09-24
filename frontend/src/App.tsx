import { useEffect } from 'react';
import { Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { api } from './api';
import { useLoad } from './hooks';
import { onApprovalsChanged } from './approvals';
import type { PendingOrder, Status } from './types';
import { MarketPage } from './pages/Market';
import { BacktestPage } from './pages/Backtest';
import { TradingPage } from './pages/Trading';
import { BotsPage } from './pages/Bots';
import { ApprovalsPage } from './pages/Approvals';

export function App() {
  const { data: status } = useLoad(() => api.get<Status>('/api/status'));
  const watchApprovals = !!status?.brokerConfigured;
  const approvals = useLoad(
    () => (watchApprovals ? api.get<PendingOrder[]>('/api/trading/approvals') : Promise.resolve([] as PendingOrder[])),
    [watchApprovals],
  );
  const waiting = approvals.data?.length ?? 0;

  useEffect(() => {
    if (!watchApprovals) return;
    const t = setInterval(() => void approvals.reload(), 20_000);
    const off = onApprovalsChanged(() => void approvals.reload());
    return () => { clearInterval(t); off(); };
  }, [watchApprovals, approvals.reload]);

  useEffect(() => {
    document.title = waiting > 0 ? `(${waiting}) 待确认 · 美股量化交易` : '美股量化交易';
  }, [waiting]);

  return (
    <div className="app">
      <nav className="topbar">
        <div className="brand">美股量化</div>
        <NavLink to="/market">行情</NavLink>
        <NavLink to="/backtest">回测</NavLink>
        <NavLink to="/trading">交易</NavLink>
        <NavLink to="/bots">自动交易</NavLink>
        <NavLink to="/approvals">
          待确认{waiting > 0 && <span className="count">{waiting}</span>}
        </NavLink>
        <div className="spacer" />
        {status && (
          <div className="badges">
            {status.dataSource === 'demo' && <span className="badge warn">演示数据</span>}
            {status.brokerConfigured ? (
              <span className={`badge ${status.accountMode === 'live' ? 'danger' : 'ok'}`}>
                {status.accountMode === 'live' ? '实盘账户' : '模拟盘'}
              </span>
            ) : (
              <span className="badge">未连接券商</span>
            )}
            {!status.tradingEnabled && <span className="badge danger">交易已关闭</span>}
          </div>
        )}
      </nav>

      {status?.dataSource === 'demo' && (
        <div className="alert warn banner">
          未配置 Alpaca API Key：行情和回测使用<strong>随机生成的演示数据</strong>，不是真实价格，交易功能不可用。
          设置环境变量 <code>ALPACA_KEY_ID</code> 和 <code>ALPACA_SECRET_KEY</code> 后重启即可接入真实数据和模拟盘。
        </div>
      )}
      {status?.accountMode === 'live' && status.brokerConfigured && (
        <div className="alert error banner">
          当前连接的是<strong>实盘账户</strong>，订单会使用真实资金。
          {status.liveTradingEnabled ? '实盘下单已开启，' : '实盘下单未开启（TRADING_LIVE_ENABLED=false），所有订单会被拒绝。'}
          {status.liveTradingEnabled && <>每一笔订单都必须由你在「待确认」页输入密码确认后才会发出。</>}
        </div>
      )}

      <main>
        <Routes>
          <Route path="/" element={<Navigate to="/market" replace />} />
          <Route path="/market" element={<MarketPage />} />
          <Route path="/backtest" element={<BacktestPage />} />
          <Route path="/trading" element={<TradingPage status={status} />} />
          <Route path="/bots" element={<BotsPage status={status} />} />
          <Route path="/approvals" element={<ApprovalsPage status={status} />} />
        </Routes>
      </main>
    </div>
  );
}
