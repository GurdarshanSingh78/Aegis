package com.aegis.dashboard;

/**
 * The dashboard is a single self-contained HTML page (no build step, no
 * external JS framework, no CDN dependency) so {@code mvn compile exec:java}
 * is the entire "install" story. It polls no REST endpoint for its numbers
 * -- it opens one {@code EventSource} against {@code /metrics/stream} and
 * repaints as events arrive.
 */
final class DashboardHtml {

    private DashboardHtml() {
    }

    static final String PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Aegis &mdash; Live Traffic Control</title>
            <style>
              :root {
                --bg: #0b0f14;
                --panel: #121821;
                --border: #1e2733;
                --text: #e6edf3;
                --text-muted: #7c8b9c;
                --green: #3ecf8e;
                --amber: #f5a623;
                --red: #e5484d;
                --violet: #a78bfa;
                --font-display: ui-monospace, "JetBrains Mono", "SFMono-Regular", Menlo, Consolas, monospace;
                --font-body: ui-sans-serif, -apple-system, "Segoe UI", Roboto, sans-serif;
              }

              * { box-sizing: border-box; }

              body {
                margin: 0;
                background: var(--bg);
                color: var(--text);
                font-family: var(--font-body);
                min-height: 100vh;
                padding: 32px 20px 60px;
              }

              .frame {
                max-width: 920px;
                margin: 0 auto;
              }

              header {
                display: flex;
                justify-content: space-between;
                align-items: flex-end;
                flex-wrap: wrap;
                gap: 12px;
                border-bottom: 1px solid var(--border);
                padding-bottom: 20px;
                margin-bottom: 28px;
              }

              .wordmark {
                font-family: var(--font-display);
                font-size: 34px;
                letter-spacing: 0.14em;
                font-weight: 700;
                margin: 0;
              }

              .subtitle {
                color: var(--text-muted);
                font-size: 13px;
                margin-top: 6px;
                letter-spacing: 0.02em;
              }

              .throughput {
                font-family: var(--font-display);
                font-size: 13px;
                color: var(--text-muted);
                text-align: right;
              }

              .throughput strong {
                color: var(--text);
                font-size: 20px;
                display: block;
              }

              section.panel {
                background: var(--panel);
                border: 1px solid var(--border);
                border-radius: 10px;
                padding: 20px;
                margin-bottom: 20px;
              }

              .panel-label {
                font-size: 11px;
                letter-spacing: 0.12em;
                text-transform: uppercase;
                color: var(--text-muted);
                margin: 0 0 14px 0;
              }

              .readouts {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                gap: 16px;
              }

              .readout .value {
                font-family: var(--font-display);
                font-size: 30px;
                font-variant-numeric: tabular-nums;
              }

              .readout .label {
                font-size: 11px;
                letter-spacing: 0.08em;
                text-transform: uppercase;
                color: var(--text-muted);
                margin-top: 4px;
              }

              .readout.allowed .value { color: var(--text); }
              .readout.rejected .value { color: var(--amber); }
              .readout.success .value { color: var(--green); }
              .readout.failure .value { color: var(--red); }
              .readout.shortcircuited .value { color: var(--violet); }

              .lamps {
                display: flex;
                gap: 28px;
              }

              .lamp {
                display: flex;
                flex-direction: column;
                align-items: center;
                gap: 8px;
              }

              .lamp .dot {
                width: 22px;
                height: 22px;
                border-radius: 50%;
                border: 2px solid var(--border);
                background: transparent;
                transition: background-color 120ms ease, box-shadow 120ms ease;
              }

              .lamp .name {
                font-size: 11px;
                letter-spacing: 0.08em;
                color: var(--text-muted);
              }

              .lamp.on.closed .dot { background: var(--green); border-color: var(--green); box-shadow: 0 0 12px var(--green); }
              .lamp.on.half_open .dot { background: var(--amber); border-color: var(--amber); box-shadow: 0 0 12px var(--amber); }
              .lamp.on.open .dot {
                background: var(--red);
                border-color: var(--red);
                box-shadow: 0 0 14px var(--red);
                animation: pulse 1s ease-in-out infinite;
              }
              .lamp.on .name { color: var(--text); }

              @keyframes pulse {
                0%, 100% { opacity: 1; }
                50% { opacity: 0.55; }
              }

              @media (prefers-reduced-motion: reduce) {
                .lamp.on.open .dot { animation: none; }
              }

              .strip {
                display: flex;
                align-items: center;
                gap: 3px;
                height: 40px;
                overflow: hidden;
              }

              .tick {
                flex: 0 0 auto;
                width: 6px;
                height: 26px;
                border-radius: 2px;
                background: var(--border);
              }

              .tick.success { background: var(--green); }
              .tick.rejected { background: var(--amber); }
              .tick.failure { background: var(--red); }
              .tick.shortcircuited { background: var(--violet); }

              .controls {
                display: flex;
                flex-wrap: wrap;
                gap: 12px;
              }

              button {
                font-family: var(--font-body);
                font-size: 12px;
                letter-spacing: 0.06em;
                text-transform: uppercase;
                color: var(--text);
                background: transparent;
                border: 1px solid var(--border);
                border-radius: 6px;
                padding: 12px 16px;
                cursor: pointer;
                transition: border-color 120ms ease, background-color 120ms ease;
              }

              button:hover { border-color: var(--text-muted); }
              button:focus-visible { outline: 2px solid var(--green); outline-offset: 2px; }
              button.stop { color: var(--red); border-color: var(--red); }
            </style>
            </head>
            <body>
            <div class="frame">
              <header>
                <div>
                  <p class="wordmark">AEGIS</p>
                  <p class="subtitle">rate limiter + circuit breaker &mdash; live telemetry</p>
                </div>
                <div class="throughput">
                  allowed / sec
                  <strong id="rps">0.0</strong>
                </div>
              </header>

              <section class="panel">
                <p class="panel-label">Traffic counters</p>
                <div class="readouts">
                  <div class="readout allowed">
                    <div class="value" id="stat-allowed">0</div>
                    <div class="label">Allowed</div>
                  </div>
                  <div class="readout rejected">
                    <div class="value" id="stat-rejected">0</div>
                    <div class="label">Rejected (rate limit)</div>
                  </div>
                  <div class="readout success">
                    <div class="value" id="stat-success">0</div>
                    <div class="label">Success</div>
                  </div>
                  <div class="readout failure">
                    <div class="value" id="stat-failure">0</div>
                    <div class="label">Failure</div>
                  </div>
                  <div class="readout shortcircuited">
                    <div class="value" id="stat-shortcircuited">0</div>
                    <div class="label">Short-circuited (breaker open)</div>
                  </div>
                </div>
              </section>

              <section class="panel">
                <p class="panel-label">Circuit state</p>
                <div class="lamps">
                  <div class="lamp" id="lamp-closed"><div class="dot"></div><div class="name">CLOSED</div></div>
                  <div class="lamp" id="lamp-half_open"><div class="dot"></div><div class="name">HALF-OPEN</div></div>
                  <div class="lamp" id="lamp-open"><div class="dot"></div><div class="name">OPEN</div></div>
                </div>
              </section>

              <section class="panel">
                <p class="panel-label">Live traffic (most recent outcomes, left to right)</p>
                <div class="strip" id="strip"></div>
              </section>

              <section class="panel">
                <p class="panel-label">Generate traffic</p>
                <div class="controls">
                  <button id="btn-one">Send one request</button>
                  <button id="btn-burst">Start burst (~20 req/sec)</button>
                  <button id="btn-stop" class="stop">Stop burst</button>
                </div>
              </section>
            </div>

            <script>
              const els = {
                allowed: document.getElementById('stat-allowed'),
                rejected: document.getElementById('stat-rejected'),
                success: document.getElementById('stat-success'),
                failure: document.getElementById('stat-failure'),
                shortcircuited: document.getElementById('stat-shortcircuited'),
                rps: document.getElementById('rps'),
                strip: document.getElementById('strip')
              };

              const lamps = {
                CLOSED: document.getElementById('lamp-closed'),
                HALF_OPEN: document.getElementById('lamp-half_open'),
                OPEN: document.getElementById('lamp-open')
              };

              const MAX_TICKS = 90;
              let previous = null;
              let lastTimestampMs = performance.now();

              function addTicks(className, count) {
                for (let i = 0; i < count; i++) {
                  const tick = document.createElement('div');
                  tick.className = 'tick ' + className;
                  els.strip.appendChild(tick);
                  if (els.strip.children.length > MAX_TICKS) {
                    els.strip.removeChild(els.strip.firstChild);
                  }
                }
              }

              function setLamp(state) {
                for (const key of Object.keys(lamps)) {
                  lamps[key].classList.remove('on', 'closed', 'half_open', 'open');
                }
                const active = lamps[state];
                if (active) {
                  active.classList.add('on', state.toLowerCase());
                }
              }

              function update(data) {
                els.allowed.textContent = data.allowed;
                els.rejected.textContent = data.rejected;
                els.success.textContent = data.success;
                els.failure.textContent = data.failure;
                els.shortcircuited.textContent = data.shortCircuited;
                setLamp(data.circuitState);

                const now = performance.now();
                const elapsedSeconds = (now - lastTimestampMs) / 1000;
                lastTimestampMs = now;

                if (previous) {
                  addTicks('success', data.success - previous.success);
                  addTicks('rejected', data.rejected - previous.rejected);
                  addTicks('failure', data.failure - previous.failure);
                  addTicks('shortcircuited', data.shortCircuited - previous.shortCircuited);

                  const allowedDelta = data.allowed - previous.allowed;
                  if (elapsedSeconds > 0) {
                    els.rps.textContent = (allowedDelta / elapsedSeconds).toFixed(1);
                  }
                }
                previous = data;
              }

              const source = new EventSource('/metrics/stream');
              source.onmessage = (event) => update(JSON.parse(event.data));

              let burstTimer = null;

              document.getElementById('btn-one').addEventListener('click', () => {
                fetch('/api/resource').catch(() => {});
              });

              document.getElementById('btn-burst').addEventListener('click', () => {
                if (burstTimer) return;
                burstTimer = setInterval(() => {
                  fetch('/api/resource').catch(() => {});
                }, 50);
              });

              document.getElementById('btn-stop').addEventListener('click', () => {
                if (burstTimer) {
                  clearInterval(burstTimer);
                  burstTimer = null;
                }
              });
            </script>
            </body>
            </html>
            """;
}
