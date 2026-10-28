/**
 * elastic.js
 * -----------------------------------------------------------------------
 * Elasticsearch Feature Page — Four Workloads:
 *   W1: Aggregations Dashboard (GROUP BY analytics)
 *   W2: Free-Text Search (Medical Records + Feedback)
 *   W3: Doctor Attribute Lookup (denormalized, no JOINs)
 *   W4: Geo-Proximity Hospital Finder
 * Plus: Cluster Status, Benchmark Runner, DSL/SQL Side-by-Side Viewer
 */

(function () {
  'use strict';

  HMD.pages = HMD.pages || {};

  /* ─── State ──────────────────────────────────────────────────────────── */
  let aggCharts = {};          // Chart.js instances
  let clusterStatus = null;    // last /status response
  let benchmarkData = [];      // last /benchmark response
  let aggData = {};            // last /aggregations response

  /* ─── Page Registration ──────────────────────────────────────────────── */
  HMD.pages['elastic'] = {
    load() { initElasticPage(); },
    search(term) { /* no global search here */ },
  };

  /* ─── Entry Point ────────────────────────────────────────────────────── */
  function initElasticPage() {
    loadClusterStatus();
    loadAggregations();
    setupEventListeners();
  }

  /* =====================================================================
   * CLUSTER STATUS
   * ===================================================================== */
  function loadClusterStatus() {
    const badge = document.getElementById('esClusterBadge');
    const details = document.getElementById('esClusterDetails');
    if (!badge) return;
    badge.textContent = 'Checking…';
    badge.className = 'es-badge es-badge--checking';

    HMD_API.get('/api/elastic/status')
      .then(data => {
        clusterStatus = data;
        const live = data.connectedToCluster;
        badge.textContent = live ? '🟢 Live Elasticsearch' : '🟡 Standalone Engine';
        badge.className = 'es-badge ' + (live ? 'es-badge--live' : 'es-badge--standalone');
        badge.title = data.clusterUri;

        const counts = data.indexDocCounts || {};
        const rows = Object.entries(counts).map(([idx, cnt]) =>
          `<tr><td>${idx}</td><td class="num">${cnt.toLocaleString()}</td></tr>`).join('');
        details.innerHTML = `
          <table class="es-mini-table">
            <thead><tr><th>Index</th><th>Docs</th></tr></thead>
            <tbody>${rows}</tbody>
          </table>
          <p class="es-mode-note">${live
            ? 'Connected to <strong>' + (data.clusterName || 'elasticsearch') + '</strong> v' + (data.clusterVersion || '?')
            : 'No Elasticsearch cluster detected at <code>' + data.clusterUri + '</code>.<br>Running on the built-in Elastic Engine — all features fully functional.'
          }</p>`;
      })
      .catch(() => {
        badge.textContent = '⚪ Offline';
        badge.className = 'es-badge es-badge--offline';
      });
  }

  /* =====================================================================
   * WORKLOAD 1 — AGGREGATIONS
   * ===================================================================== */
  function loadAggregations() {
    const grid = document.getElementById('esAggGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="es-loading"><span class="spinner"></span> Running aggregation pipeline…</div>';

    HMD_API.get('/api/elastic/aggregations')
      .then(data => {
        aggData = data;
        grid.innerHTML = '';
        const aggs = data.aggregations || {};

        renderAggChart('esAggConsultStatus', 'chartEsConsultStatus',
          aggs.consultations_by_status, 'Consultations by Status', 'doughnut', 'key', 'doc_count');
        renderAggChart('esAggPatientsCity', 'chartEsPatientsCity',
          aggs.patients_by_city, 'Patients by City', 'bar', 'key', 'doc_count');
        renderAggChart('esAggDoctorsSpec', 'chartEsDoctorsSpec',
          aggs.doctors_by_specialization, 'Doctors by Specialization', 'bar', 'key', 'doc_count');
        renderAggChart('esAggRevenue', 'chartEsRevenue',
          aggs.revenue_by_payment_method, 'Revenue by Payment Method', 'bar', 'key', 'total_revenue');
        renderAggChart('esAggRating', 'chartEsRating',
          aggs.avg_rating_per_specialization, 'Avg Rating per Specialization', 'bar', 'key', 'avg_rating');

        renderDslPanel('esAggDsl', data);
      })
      .catch(err => {
        grid.innerHTML = `<div class="es-error">Aggregation failed: ${err.message}</div>`;
      });
  }

  function renderAggChart(containerId, canvasId, aggResult, title, type, labelField, valueField) {
    const grid = document.getElementById('esAggGrid');
    const buckets = (aggResult && aggResult.buckets) ? aggResult.buckets : [];
    const labels = buckets.map(b => humanLabel(b[labelField]));
    const values = buckets.map(b => typeof b[valueField] === 'number' ? parseFloat(b[valueField].toFixed(2)) : 0);

    const panel = document.createElement('div');
    panel.className = 'es-agg-panel';
    panel.innerHTML = `
      <div class="es-panel-head"><h3>${title}</h3></div>
      <div class="es-chart-wrap"><canvas id="${canvasId}"></canvas></div>
    `;
    grid.appendChild(panel);

    const ctx = document.getElementById(canvasId).getContext('2d');
    const COLORS = ['#6366f1','#10b981','#f59e0b','#ef4444','#3b82f6','#8b5cf6','#ec4899'];

    if (aggCharts[canvasId]) aggCharts[canvasId].destroy();
    aggCharts[canvasId] = new Chart(ctx, {
      type,
      data: {
        labels,
        datasets: [{
          label: valueField,
          data: values,
          backgroundColor: type === 'doughnut' ? COLORS : COLORS[0],
          borderColor: type === 'doughnut' ? '#fff' : COLORS[0],
          borderWidth: type === 'doughnut' ? 2 : 0,
          borderRadius: type === 'bar' ? 4 : 0,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: type === 'doughnut', position: 'bottom' },
          tooltip: { callbacks: { label: ctx => `${ctx.dataset.label}: ${ctx.parsed.y ?? ctx.parsed}` } }
        },
        scales: type === 'bar' ? {
          y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,.05)' } },
          x: { ticks: { maxRotation: 30 } }
        } : {}
      }
    });
  }

  /* =====================================================================
   * WORKLOAD 2 — FREE TEXT SEARCH
   * ===================================================================== */
  function setupFreeTextSearch() {
    // Medical Records
    const mrForm = document.getElementById('esMedicalSearchForm');
    if (mrForm) {
      mrForm.addEventListener('submit', e => {
        e.preventDefault();
        const q = document.getElementById('esMedicalQuery').value.trim();
        const city = document.getElementById('esMedicalCity').value;
        searchMedicalRecords(q, city);
      });
    }

    // Feedback
    const fbForm = document.getElementById('esFeedbackSearchForm');
    if (fbForm) {
      fbForm.addEventListener('submit', e => {
        e.preventDefault();
        const q = document.getElementById('esFeedbackQuery').value.trim();
        const sentiment = document.getElementById('esFeedbackSentiment').value;
        const minRating = parseInt(document.getElementById('esFeedbackRating').value) || null;
        searchFeedback(q, sentiment, minRating);
      });
    }
  }

  function searchMedicalRecords(q, city) {
    const resultsDiv = document.getElementById('esMedicalResults');
    resultsDiv.innerHTML = '<div class="es-loading"><span class="spinner"></span> Searching…</div>';

    let url = `/api/elastic/search/medical-records?q=${encodeURIComponent(q)}`;
    if (city) url += `&city=${encodeURIComponent(city)}`;

    HMD_API.get(url)
      .then(data => {
        const hits = data.hits || [];
        const tookMs = data.tookMs || 0;
        const mode = data.clusterStatus || '';

        const dslBlock = renderDslBlock(data);
        if (hits.length === 0) {
          resultsDiv.innerHTML = `<p class="es-empty">No medical records matched "${q}".</p>${dslBlock}`;
          return;
        }

        const cards = hits.map(h => `
          <div class="es-result-card">
            <div class="es-result-meta">
              <span class="es-tag">🏷 ${h.suggestedSpecialty || 'General'}</span>
              <span class="es-score">Score: <strong>${h.searchScore ?? '–'}</strong></span>
              <span class="es-city">📍 ${h.patientCity || ''} | ${h.patientName || ''}</span>
            </div>
            <div class="es-result-body">
              <div class="es-field"><label>Symptoms</label>
                <p>${(h.matchHighlights && h.matchHighlights[0]) || h.symptoms || '—'}</p>
              </div>
              <div class="es-field"><label>Diagnoses</label>
                <p>${(h.matchHighlights && h.matchHighlights[1]) || h.diagnoses || '—'}</p>
              </div>
            </div>
          </div>
        `).join('');

        resultsDiv.innerHTML = `
          <div class="es-results-header">
            <strong>${hits.length} result${hits.length !== 1 ? 's' : ''}</strong>
            <span class="es-took">${tookMs}ms</span>
            <span class="es-mode-chip ${mode === 'LIVE_ELASTICSEARCH' ? 'chip-live' : 'chip-engine'}">${mode}</span>
          </div>
          ${cards}
          ${dslBlock}
        `;
      })
      .catch(err => { resultsDiv.innerHTML = `<div class="es-error">${err.message}</div>`; });
  }

  function searchFeedback(q, sentiment, minRating) {
    const resultsDiv = document.getElementById('esFeedbackResults');
    resultsDiv.innerHTML = '<div class="es-loading"><span class="spinner"></span> Searching…</div>';

    let url = `/api/elastic/search/feedback?q=${encodeURIComponent(q)}`;
    if (sentiment) url += `&sentiment=${sentiment}`;
    if (minRating) url += `&minRating=${minRating}`;

    HMD_API.get(url)
      .then(data => {
        const hits = data.hits || [];
        const dslBlock = renderDslBlock(data);
        if (hits.length === 0) {
          resultsDiv.innerHTML = `<p class="es-empty">No feedback matched your query.</p>${dslBlock}`;
          return;
        }

        const sentimentIcon = { POSITIVE: '😊', NEUTRAL: '😐', CRITICAL: '😞' };
        const cards = hits.map(h => `
          <div class="es-result-card feedback-card">
            <div class="es-result-meta">
              <span class="es-sent-chip sent-${(h.sentiment||'').toLowerCase()}">${sentimentIcon[h.sentiment] || ''} ${h.sentiment || ''}</span>
              <span class="es-stars">${'★'.repeat(h.rating || 0)}${'☆'.repeat(5 - (h.rating || 0))}</span>
              <span class="es-city">👤 ${h.patientName || ''} → ${h.doctorName || ''} (${h.specialization || ''})</span>
              <span class="es-score">Score: <strong>${h.searchScore ?? '–'}</strong></span>
            </div>
            <div class="es-result-body">
              <p>${(h.matchHighlights && h.matchHighlights[0]) || h.feedbackComment || '—'}</p>
            </div>
          </div>
        `).join('');

        resultsDiv.innerHTML = `
          <div class="es-results-header">
            <strong>${hits.length} result${hits.length !== 1 ? 's' : ''}</strong>
            <span class="es-took">${data.tookMs || 0}ms</span>
          </div>
          ${cards}
          ${dslBlock}
        `;
      })
      .catch(err => { resultsDiv.innerHTML = `<div class="es-error">${err.message}</div>`; });
  }

  /* =====================================================================
   * WORKLOAD 3 — DOCTOR ATTRIBUTE LOOKUP
   * ===================================================================== */
  function setupDoctorSearch() {
    const form = document.getElementById('esDoctorSearchForm');
    if (!form) return;
    // Populate specialization dropdown
    HMD_API.get('/api/elastic/specializations')
      .then(specs => {
        const sel = document.getElementById('esDoctorSpec');
        specs.forEach(s => { const o = document.createElement('option'); o.value = s; o.textContent = humanLabel(s); sel.appendChild(o); });
      }).catch(() => {});

    form.addEventListener('submit', e => {
      e.preventDefault();
      runDoctorSearch();
    });
  }

  function runDoctorSearch() {
    const params = new URLSearchParams();
    const q = document.getElementById('esDoctorQuery').value.trim();
    const spec = document.getElementById('esDoctorSpec').value;
    const minExp = document.getElementById('esDoctorMinExp').value;
    const minRating = document.getElementById('esDoctorMinRating').value;
    const city = document.getElementById('esDoctorCity').value.trim();
    const hospital = document.getElementById('esDoctorHospital').value.trim();

    if (q) params.set('q', q);
    if (spec) params.set('specialization', spec);
    if (minExp) params.set('minExperience', minExp);
    if (minRating) params.set('minRating', minRating);
    if (city) params.set('city', city);
    if (hospital) params.set('hospital', hospital);

    const resultsDiv = document.getElementById('esDoctorResults');
    resultsDiv.innerHTML = '<div class="es-loading"><span class="spinner"></span> Querying denormalized doctor index…</div>';

    HMD_API.get('/api/elastic/search/doctors?' + params.toString())
      .then(data => {
        const hits = data.hits || [];
        const facets = data.facets || {};
        const dslBlock = renderDslBlock(data);

        if (hits.length === 0) {
          resultsDiv.innerHTML = `<p class="es-empty">No doctors matched. Try fewer filters.</p>${dslBlock}`;
          return;
        }

        const facetPanel = renderFacetPanel(facets);
        const cards = hits.map(d => `
          <div class="es-result-card doctor-card">
            <div class="es-doctor-head">
              <div class="es-doctor-avatar">${(d.fullName || 'D').charAt(d.fullName.indexOf(' ') + 1)}</div>
              <div>
                <strong>${d.fullName || '—'}</strong>
                <span class="es-doc-exp">${d.experience} yrs exp</span>
              </div>
              <div class="es-doc-rating">${'★'.repeat(Math.round(d.avgRating || 0))}
                <small>${(d.avgRating || 0).toFixed(1)}</small>
              </div>
            </div>
            <div class="es-doctor-body">
              <div class="es-tags">
                ${(d.specializations || []).map(s => `<span class="es-tag">${humanLabel(s)}</span>`).join('')}
              </div>
              <p class="es-doctor-quals">${(d.qualifications || []).join(' • ')}</p>
              <p class="es-doctor-hospitals">
                ${(d.hospitals || []).map(h =>
                  `🏥 <strong>${h.hospitalName}</strong> · ${h.role} · ${h.city}`
                ).join('<br>')}
              </p>
              <p class="es-doctor-loc">📍 ${d.area || ''}, ${d.city || ''} — ${d.pincode || ''}</p>
            </div>
          </div>
        `).join('');

        resultsDiv.innerHTML = `
          <div class="es-results-header">
            <strong>${hits.length} doctor${hits.length !== 1 ? 's' : ''}</strong>
            <span class="es-took">${data.tookMs || 0}ms</span>
            <span class="es-mode-chip ${data.clusterStatus === 'LIVE_ELASTICSEARCH' ? 'chip-live' : 'chip-engine'}">${data.clusterStatus || ''}</span>
          </div>
          <div class="es-search-layout">
            <aside class="es-facets">${facetPanel}</aside>
            <div class="es-doctor-grid">${cards}</div>
          </div>
          ${dslBlock}
        `;
      })
      .catch(err => { resultsDiv.innerHTML = `<div class="es-error">${err.message}</div>`; });
  }

  function renderFacetPanel(facets) {
    let html = '';
    if (facets.specializations && Object.keys(facets.specializations).length > 0) {
      html += '<div class="es-facet-group"><h4>Specializations</h4>';
      Object.entries(facets.specializations).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => {
        html += `<div class="es-facet-item"><span>${humanLabel(k)}</span><span class="es-facet-count">${v}</span></div>`;
      });
      html += '</div>';
    }
    if (facets.cities && Object.keys(facets.cities).length > 0) {
      html += '<div class="es-facet-group"><h4>Cities</h4>';
      Object.entries(facets.cities).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => {
        html += `<div class="es-facet-item"><span>${k}</span><span class="es-facet-count">${v}</span></div>`;
      });
      html += '</div>';
    }
    if (facets.experience_brackets) {
      html += '<div class="es-facet-group"><h4>Experience</h4>';
      Object.entries(facets.experience_brackets).forEach(([k, v]) => {
        html += `<div class="es-facet-item"><span>${k}</span><span class="es-facet-count">${v}</span></div>`;
      });
      html += '</div>';
    }
    return html || '<p class="es-empty">No facets available.</p>';
  }

  /* =====================================================================
   * WORKLOAD 4 — GEO PROXIMITY
   * ===================================================================== */
  function setupGeoSearch() {
    // Populate city presets
    const cityPresets = document.getElementById('esCityPresets');
    if (!cityPresets) return;

    HMD_API.get('/api/elastic/cities')
      .then(cities => {
        cityPresets.innerHTML = cities.map(c =>
          `<button class="btn btn-ghost btn-sm es-city-preset"
            data-lat="${c.lat}" data-lon="${c.lon}" data-name="${c.name}">${c.name}</button>`
        ).join('');
        cityPresets.querySelectorAll('.es-city-preset').forEach(btn => {
          btn.addEventListener('click', () => {
            document.getElementById('esGeoLat').value = btn.dataset.lat;
            document.getElementById('esGeoLon').value = btn.dataset.lon;
            // highlight active
            cityPresets.querySelectorAll('.es-city-preset').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
          });
        });
      }).catch(() => {});

    const form = document.getElementById('esGeoSearchForm');
    if (!form) return;
    HMD_API.get('/api/elastic/specializations')
      .then(specs => {
        const sel = document.getElementById('esGeoSpecialty');
        specs.forEach(s => { const o = document.createElement('option'); o.value = s; o.textContent = humanLabel(s); sel.appendChild(o); });
      }).catch(() => {});

    form.addEventListener('submit', e => {
      e.preventDefault();
      runGeoSearch();
    });
  }

  function runGeoSearch() {
    const lat = parseFloat(document.getElementById('esGeoLat').value);
    const lon = parseFloat(document.getElementById('esGeoLon').value);
    const radius = parseFloat(document.getElementById('esGeoRadius').value) || 10.0;
    const specialty = document.getElementById('esGeoSpecialty').value;

    if (isNaN(lat) || isNaN(lon)) {
      document.getElementById('esGeoResults').innerHTML = '<div class="es-error">Please enter valid coordinates or pick a city.</div>';
      return;
    }

    const resultsDiv = document.getElementById('esGeoResults');
    resultsDiv.innerHTML = '<div class="es-loading"><span class="spinner"></span> Running geo_distance filter…</div>';

    let url = `/api/elastic/search/hospitals/nearby?lat=${lat}&lon=${lon}&radiusKm=${radius}`;
    if (specialty) url += `&specialty=${specialty}`;

    HMD_API.get(url)
      .then(data => {
        const hits = data.hits || [];
        const dslBlock = renderDslBlock(data);

        if (hits.length === 0) {
          resultsDiv.innerHTML = `<p class="es-empty">No hospitals found within ${radius} km.</p>${dslBlock}`;
          return;
        }

        const cards = hits.map(h => `
          <div class="es-result-card hospital-geo-card">
            <div class="es-hospital-head">
              <span class="es-hosp-dist">📍 ${h.distanceKm} km away</span>
              <strong>${h.name || '—'}</strong>
            </div>
            <div class="es-hospital-body">
              <span class="es-city-area">${h.area || ''}, ${h.city || ''} — ${h.pincode || ''}</span>
              <div class="es-tags">${(h.specialties || []).map(s => `<span class="es-tag">${humanLabel(s)}</span>`).join('')}</div>
              <span class="es-doc-count">👨‍⚕️ ~${h.doctorCount} doctors</span>
              ${(h.phones || []).length ? `<span class="es-phone">📞 ${h.phones.join(' | ')}</span>` : ''}
            </div>
          </div>
        `).join('');

        resultsDiv.innerHTML = `
          <div class="es-results-header">
            <strong>${hits.length} hospital${hits.length !== 1 ? 's' : ''} within ${radius} km</strong>
            <span class="es-took">${data.tookMs || 0}ms</span>
          </div>
          ${cards}
          ${dslBlock}
        `;
      })
      .catch(err => { resultsDiv.innerHTML = `<div class="es-error">${err.message}</div>`; });
  }

  /* =====================================================================
   * BENCHMARK RUNNER
   * ===================================================================== */
  function setupBenchmark() {
    const btn = document.getElementById('esBenchmarkBtn');
    if (!btn) return;
    btn.addEventListener('click', runBenchmark);
  }

  function runBenchmark() {
    const resultsDiv = document.getElementById('esBenchmarkResults');
    const btn = document.getElementById('esBenchmarkBtn');
    btn.disabled = true;
    btn.textContent = 'Running…';
    resultsDiv.innerHTML = '<div class="es-loading"><span class="spinner"></span> Benchmarking SQL vs. Elasticsearch…</div>';

    HMD_API.get('/api/elastic/benchmark')
      .then(data => {
        benchmarkData = data;
        const rows = data.map(b => `
          <tr class="bench-row" data-id="${b.workloadId}">
            <td><strong>${b.workloadTitle}</strong><br><small>${b.description}</small></td>
            <td class="num">${b.sqlDurationMs > 0 ? b.sqlDurationMs + ' ms' : '<em>Oracle offline</em>'}</td>
            <td class="num">${b.elasticDurationMs} ms</td>
            <td class="num speedup">${b.speedup}</td>
            <td><button class="btn btn-ghost btn-sm bench-detail-btn" data-id="${b.workloadId}">Details</button></td>
          </tr>
          <tr class="bench-detail-row" id="bench-detail-${b.workloadId}" style="display:none">
            <td colspan="5">
              <div class="es-compare">
                <div class="es-compare-col">
                  <h5>🐘 Oracle SQL</h5>
                  <pre class="es-dsl-pre">${escHtml(b.sqlQuery || '')}</pre>
                </div>
                <div class="es-compare-col">
                  <h5>⚡ Elasticsearch DSL</h5>
                  <pre class="es-dsl-pre">${escHtml(b.elasticQueryDsl || '')}</pre>
                </div>
              </div>
              <div class="es-advantage-box">💡 <strong>Why Elasticsearch wins:</strong> ${b.architecturalAdvantage || ''}</div>
            </td>
          </tr>
        `).join('');

        resultsDiv.innerHTML = `
          <table class="data-table bench-table">
            <thead>
              <tr>
                <th>Workload</th>
                <th>Oracle SQL (ms)</th>
                <th>Elasticsearch (ms)</th>
                <th>Speedup</th>
                <th></th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        `;

        // Toggle detail rows
        resultsDiv.querySelectorAll('.bench-detail-btn').forEach(btn => {
          btn.addEventListener('click', () => {
            const row = document.getElementById('bench-detail-' + btn.dataset.id);
            const visible = row.style.display !== 'none';
            row.style.display = visible ? 'none' : 'table-row';
            btn.textContent = visible ? 'Details' : 'Hide';
          });
        });
      })
      .catch(err => { resultsDiv.innerHTML = `<div class="es-error">${err.message}</div>`; })
      .finally(() => { btn.disabled = false; btn.textContent = '▶ Run Benchmark'; });
  }

  /* =====================================================================
   * SYNC
   * ===================================================================== */
  function setupSync() {
    const btn = document.getElementById('esSyncBtn');
    if (!btn) return;
    btn.addEventListener('click', () => {
      btn.disabled = true;
      btn.textContent = 'Syncing…';
      HMD_API.post('/api/elastic/sync', {})
        .then(res => {
          showToast(`Sync complete — ${res.mode || 'done'}`, 'success');
          loadClusterStatus();
        })
        .catch(err => showToast('Sync failed: ' + err.message, 'error'))
        .finally(() => { btn.disabled = false; btn.textContent = '↺ Sync'; });
    });
  }

  /* =====================================================================
   * SHARED HELPERS
   * ===================================================================== */
  function renderDslBlock(data) {
    if (!data.queryDsl && !data.sqlEquivalent) return '';
    return `
      <details class="es-dsl-details">
        <summary>View Query DSL vs. SQL Equivalent</summary>
        <div class="es-compare">
          <div class="es-compare-col">
            <h5>🐘 Oracle SQL Equivalent</h5>
            <pre class="es-dsl-pre">${escHtml(data.sqlEquivalent || 'N/A')}</pre>
          </div>
          <div class="es-compare-col">
            <h5>⚡ Elasticsearch DSL</h5>
            <pre class="es-dsl-pre">${escHtml(data.queryDsl || 'N/A')}</pre>
          </div>
        </div>
      </details>
    `;
  }

  function renderDslPanel(containerId, data) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = renderDslBlock(data);
  }

  function humanLabel(s) {
    if (!s) return '';
    return s.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  function escHtml(s) {
    return String(s || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  /* =====================================================================
   * WIRE ALL EVENT LISTENERS (called once on page load)
   * ===================================================================== */
  function setupEventListeners() {
    setupFreeTextSearch();
    setupDoctorSearch();
    setupGeoSearch();
    setupBenchmark();
    setupSync();

    // Tab switching inside elastic page
    document.querySelectorAll('.es-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.dataset.tab;
        document.querySelectorAll('.es-tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.es-tab-panel').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        const panel = document.getElementById('es-tab-' + target);
        if (panel) panel.classList.add('active');
      });
    });
  }

})();
