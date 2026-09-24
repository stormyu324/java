import { useEffect, useRef } from 'react';
import { ColorType, createChart, LineSeries, type UTCTimestamp } from 'lightweight-charts';
import type { BacktestResult } from '../types';
import { chartColors } from './chartTheme';

export function EquityChart({ points }: { points: BacktestResult['equity'] }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ref.current) return;
    const c = chartColors();
    const chart = createChart(ref.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: c.bg }, textColor: c.text },
      grid: { vertLines: { color: c.grid }, horzLines: { color: c.grid } },
      rightPriceScale: { borderColor: c.grid },
      timeScale: { borderColor: c.grid },
    });
    const t = (iso: string) => Math.floor(new Date(iso).getTime() / 1000) as UTCTimestamp;
    chart.addSeries(LineSeries, { color: c.lines[0], lineWidth: 2, title: '策略' })
      .setData(points.map((p) => ({ time: t(p.time), value: p.equity })));
    chart.addSeries(LineSeries, { color: c.muted, lineWidth: 1, lineStyle: 2, title: '买入持有' })
      .setData(points.map((p) => ({ time: t(p.time), value: p.benchmark })));
    chart.timeScale().fitContent();
    return () => chart.remove();
  }, [points]);

  return <div className="chart" ref={ref} />;
}
