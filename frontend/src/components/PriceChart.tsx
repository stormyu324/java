import { useEffect, useRef } from 'react';
import {
  CandlestickSeries,
  ColorType,
  createChart,
  HistogramSeries,
  LineSeries,
  type UTCTimestamp,
} from 'lightweight-charts';
import type { Bar } from '../types';
import { chartColors } from './chartTheme';

const toTime = (iso: string) => Math.floor(new Date(iso).getTime() / 1000) as UTCTimestamp;

function sma(bars: Bar[], period: number) {
  const out: { time: UTCTimestamp; value: number }[] = [];
  let sum = 0;
  bars.forEach((b, i) => {
    sum += b.close;
    if (i >= period) sum -= bars[i - period].close;
    if (i >= period - 1) out.push({ time: toTime(b.time), value: sum / period });
  });
  return out;
}

export function PriceChart({ bars, smaPeriods = [20, 50] }: { bars: Bar[]; smaPeriods?: number[] }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!ref.current) return;
    const c = chartColors();
    const chart = createChart(ref.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: c.bg }, textColor: c.text },
      grid: { vertLines: { color: c.grid }, horzLines: { color: c.grid } },
      rightPriceScale: { borderColor: c.grid },
      timeScale: { borderColor: c.grid, timeVisible: true },
    });

    const candles = chart.addSeries(CandlestickSeries, {
      upColor: c.up, downColor: c.down, borderVisible: false, wickUpColor: c.up, wickDownColor: c.down,
    });
    candles.setData(bars.map((b) => ({ time: toTime(b.time), open: b.open, high: b.high, low: b.low, close: b.close })));

    const volume = chart.addSeries(HistogramSeries, { priceFormat: { type: 'volume' }, priceScaleId: 'vol' });
    chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });
    volume.setData(bars.map((b) => ({
      time: toTime(b.time), value: b.volume, color: b.close >= b.open ? c.upSoft : c.downSoft,
    })));

    smaPeriods.forEach((p, i) => {
      const line = chart.addSeries(LineSeries, {
        color: c.lines[i % c.lines.length], lineWidth: 1, priceLineVisible: false, lastValueVisible: false,
        title: `MA${p}`,
      });
      line.setData(sma(bars, p));
    });

    chart.timeScale().fitContent();
    return () => chart.remove();
  }, [bars, smaPeriods]);

  return <div className="chart" ref={ref} />;
}
