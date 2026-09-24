export type Timeframe = 'MIN_5' | 'MIN_15' | 'HOUR_1' | 'DAY_1' | 'WEEK_1';
export type StrategyType = 'SMA_CROSS' | 'RSI_MEAN_REVERSION' | 'BREAKOUT' | 'BUY_AND_HOLD';

export interface Bar {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface BarsResponse {
  symbol: string;
  timeframe: Timeframe;
  source: string;
  bars: Bar[];
}

export interface Quote {
  symbol: string;
  price: number;
  previousClose: number;
  change: number;
  changePercent: number;
  time: string;
}

export interface StrategyInfo {
  type: StrategyType;
  label: string;
  description: string;
  defaults: Record<string, number>;
}

export interface Metrics {
  finalEquity: number;
  totalReturnPct: number;
  cagrPct: number;
  maxDrawdownPct: number;
  sharpe: number;
  volatilityPct: number;
  trades: number;
  winRatePct: number;
  exposurePct: number;
}

export interface BacktestResult {
  symbol: string;
  dataSource: string;
  metrics: Metrics;
  benchmark: Metrics;
  equity: { time: string; equity: number; benchmark: number }[];
  trades: {
    entryTime: string;
    entryPrice: number;
    exitTime: string | null;
    exitPrice: number | null;
    shares: number;
    pnl: number;
    returnPct: number;
    open: boolean;
  }[];
}

export interface Status {
  dataSource: string;
  brokerConfigured: boolean;
  accountMode: 'paper' | 'live';
  tradingEnabled: boolean;
  liveTradingEnabled: boolean;
  maxOrderNotional: number;
  maxOpenPositions: number;
  botSchedulerEnabled: boolean;
  botCron: string;
  botZone: string;
}

// Alpaca objects are passed through in snake_case; numbers arrive as JSON numbers.
export interface Account {
  account_number: string;
  status: string;
  currency: string;
  cash: number;
  equity: number;
  last_equity: number;
  buying_power: number;
  portfolio_value: number;
  trading_blocked: boolean;
  pattern_day_trader: boolean;
}

export interface Position {
  symbol: string;
  qty: number;
  side: string;
  avg_entry_price: number;
  current_price: number;
  market_value: number;
  cost_basis: number;
  unrealized_pl: number;
  unrealized_plpc: number;
  change_today: number;
}

export interface Order {
  id: string;
  client_order_id: string;
  symbol: string;
  qty: number | null;
  notional: number | null;
  filled_qty: number | null;
  filled_avg_price: number | null;
  side: string;
  type: string;
  time_in_force: string;
  limit_price: number | null;
  status: string;
  submitted_at: string;
  filled_at: string | null;
}

export interface Clock {
  timestamp: string;
  is_open: boolean;
  next_open: string;
  next_close: string;
}

export interface TradeLog {
  id: number;
  time: string;
  source: string;
  mode: string;
  symbol: string;
  side: string;
  qty: number | null;
  status: string;
  brokerOrderId: string | null;
  message: string | null;
}

export interface Bot {
  id: number;
  name: string;
  symbol: string;
  strategy: StrategyType;
  params: Record<string, number>;
  notional: number;
  enabled: boolean;
  lastRunAt: string | null;
  lastSignal: string | null;
  lastAction: string | null;
  lastMessage: string | null;
}

export interface BotRunResult {
  botId: number;
  symbol: string;
  signal: string;
  action: string;
  message: string;
  dryRun: boolean;
}
