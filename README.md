# 美股量化交易网站

个人使用的美股量化交易平台：行情看板、策略回测、Alpaca 模拟盘/实盘交易、按策略自动下单的机器人。

- 后端：Java 21 + Spring Boot 3.5（`backend/`）
- 前端：React 19 + TypeScript + Vite + TradingView lightweight-charts（`frontend/`）
- 券商与行情：[Alpaca](https://alpaca.markets)
- 数据库：H2 文件库（`backend/data/`），存放机器人配置和下单记录

## 功能

| 页面 | 内容 |
| --- | --- |
| 行情 | 自选股报价、K 线（15 分钟 / 1 小时 / 日线 / 周线）、MA20/MA50、成交量 |
| 回测 | 均线交叉、RSI 均值回归、通道突破、买入持有；输出收益曲线、年化、最大回撤、夏普、胜率、交易明细，并与买入持有对比 |
| 交易 | 账户资金、持仓、市价/限价下单、撤单、一键平仓、下单审计日志（包括被风控拒绝的） |
| 自动交易 | 为某只股票配置策略机器人，每个交易日收盘前自动检查信号并下单；支持「试运行」只看信号不下单 |
| 待确认 | 实盘账户的所有订单都在这里等你输入密码确认，确认后才发送到券商 |

回测规则：日线、只做多、整股；信号在当天收盘产生，**第二天开盘成交**（避免未来函数）；可设置手续费和滑点。

## 快速开始

### 1. 不配置 Key，先用演示数据跑起来

```bash
# 构建前端（输出到 backend/src/main/resources/static）
cd frontend && npm install && npm run build && cd ..

# 启动后端
cd backend && APP_PASSWORD=change-me mvn spring-boot:run
```

浏览器打开 http://localhost:8080 ，用 `admin` / `change-me` 登录。

没有 Alpaca Key 时，行情和回测用的是**随机生成的演示数据**（页面顶部会有黄色提示），交易功能不可用。

### 2. 接入 Alpaca 模拟盘

1. 在 https://alpaca.markets 注册（免费），进入 **Paper Trading** 账户，生成 API Key。
2. 启动时设置环境变量：

```bash
export APP_PASSWORD=change-me
export ALPACA_KEY_ID=你的KeyID
export ALPACA_SECRET_KEY=你的Secret
export ALPACA_PAPER=true
cd backend && mvn spring-boot:run
```

所有配置项见 [`.env.example`](.env.example)。

### 3. 开发模式（前端热更新）

```bash
cd backend && APP_PASSWORD=change-me mvn spring-boot:run   # :8080
cd frontend && npm run dev                                  # :5173，/api 代理到 :8080
```

### 4. 打包部署

```bash
cd frontend && npm run build
cd ../backend && mvn package
java -jar target/quant-trader-0.1.0.jar
```

一个 JAR 同时提供网页和 API。部署到公网时请放在 HTTPS 反向代理（如 Nginx、Caddy）后面，因为登录使用 HTTP Basic 认证。

## 实盘订单必须本人确认

连接实盘账户（`ALPACA_PAPER=false`）时，**任何订单都不会自动发出**，这条规则无法关闭：

- 手动下单、手动平仓、机器人的买入/卖出，都只会生成一条「待确认订单」。
- 你在「待确认」页（导航栏会显示待确认数量）查看订单详情，**输入登录密码**后才会发送到 Alpaca。浏览器保持登录状态不等于确认，必须重新输入密码。
- 确认时会重新检查风控：交易开关、单笔金额上限（按最新价格）、持仓数量，任何一项不通过则作废。
- 待确认订单默认 30 分钟后过期（`TRADING_APPROVAL_TTL_MINUTES`），避免过时的信号被执行。
- 在实盘生成的订单，程序切换到模拟盘后无法确认，反之亦然。
- 同一个机器人对同一只股票不会重复生成待确认订单。
- 撤单不需要确认（撤单只会降低风险）。
- 所有确认、拒绝、过期都记录在「确认记录」和「下单记录」里。

想先熟悉流程，可以在模拟盘上设置 `TRADING_APPROVAL_IN_PAPER=true`。

> 机器人默认在美东 15:50 运行，离收盘只有 10 分钟。如果你需要更多时间确认，可以把 `BOTS_CRON` 调早，例如 `0 0 15 * * MON-FRI`（15:00）。收盘后才确认的市价单会在下一个交易日开盘成交。

## 安全与风控

- **默认只连模拟盘**。要用实盘，必须同时设置 `ALPACA_PAPER=false`（使用实盘 Key）**和** `TRADING_LIVE_ENABLED=true`，缺一个都会拒绝下单。
- `TRADING_ENABLED=false` 是总开关，会拦截所有手动和自动订单。
- 单笔订单金额上限 `TRADING_MAX_ORDER_NOTIONAL`，最多持仓数 `TRADING_MAX_OPEN_POSITIONS`。
- 每只股票只能有一个机器人，避免互相抢仓位。机器人卖出时会平掉该股票的**全部**持仓（包括手动买入的部分）。
- 定时任务运行前会检查 Alpaca 市场时钟，节假日休市时不会下单。
- 每次下单（成功、失败、被拒）都会记录在「交易」页的下单记录里。
- 需要登录才能访问；所有修改类请求必须带 `X-Requested-With` 头（防 CSRF）。

> ⚠️ 回测结果不代表未来收益。请先在模拟盘运行足够长时间，确认策略和系统行为符合预期后，再考虑用小资金实盘。

## 添加新策略

1. 在 `backend/src/main/java/com/quant/strategy/` 实现 `Strategy` 接口：`signals(bars)` 返回每根 K 线的 `BUY` / `SELL` / `HOLD`，第 `i` 个信号只能使用第 `0..i` 根 K 线的数据。
2. 在 `StrategyType` 里注册名称、说明和默认参数。前端表单会自动出现。

## 测试

```bash
cd backend && mvn test        # 指标、策略、回测引擎、风控、订单确认、机器人、登录与 CSRF
cd frontend && npm run typecheck
```

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/status` | 数据源、账户类型、风控配置 |
| GET | `/api/market/bars/{symbol}?timeframe=DAY_1&from=&to=` | K 线 |
| GET | `/api/market/quotes?symbols=AAPL,MSFT` | 最新报价 |
| GET | `/api/strategies` | 策略列表和默认参数 |
| POST | `/api/backtest` | 运行回测 |
| GET | `/api/trading/account` · `/positions` · `/orders` · `/clock` · `/logs` | 账户信息 |
| POST | `/api/trading/orders` | 下单 |
| DELETE | `/api/trading/orders/{id}` · `/api/trading/positions/{symbol}` | 撤单 / 平仓 |
| GET | `/api/trading/approvals` · `?all=true` | 待确认订单 / 确认记录 |
| POST | `/api/trading/approvals/{id}/approve` `{"password": "..."}` | 输入密码确认并发送 |
| POST | `/api/trading/approvals/{id}/reject` | 拒绝 |
| GET/POST/PUT/DELETE | `/api/bots` | 机器人增删改查 |
| POST | `/api/bots/{id}/run?dryRun=true` | 立即运行（试运行不下单） |
