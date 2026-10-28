/**
 * router.js
 * -----------------------------------------------------------------------
 * Minimal hash-based router. Each page module (js/pages/*.js) registers
 * itself on HMD.pages with a `load()` function (fetches + renders data)
 * and an optional `search(term)` function (client-side filter of an
 * already-loaded table). The router just decides which <section> is
 * visible and calls the right lifecycle hooks — it never touches the
 * database or an API directly.
 */

const PAGE_META = {
  dashboard: { title: 'Dashboard', crumb: 'Dashboard', search: false },
  patients: { title: 'Patients', crumb: 'Patients', search: true, placeholder: 'Search patients by name…' },
  doctors: { title: 'Doctors', crumb: 'Doctors', search: true, placeholder: 'Search doctors by name…' },
  hospitals: { title: 'Hospitals', crumb: 'Hospitals', search: true, placeholder: 'Search hospitals…' },
  consultations: { title: 'Consultations', crumb: 'Consultations', search: true, placeholder: 'Search by patient or doctor…' },
  prescriptions: { title: 'Prescriptions', crumb: 'Prescriptions', search: true, placeholder: 'Search prescriptions…' },
  medicines: { title: 'Medicines', crumb: 'Medicines', search: true, placeholder: 'Search medicines…' },
  tests: { title: 'Tests', crumb: 'Tests', search: true, placeholder: 'Search tests…' },
  payments: { title: 'Payments', crumb: 'Payments', search: true, placeholder: 'Search payments…' },
  elastic: { title: 'Elasticsearch', crumb: 'Elasticsearch', search: false },
};

const HMD_ROUTER = {
  current: null,

  init() {
    window.addEventListener('hashchange', () => this.go(this._parseHash()));
    this.go(this._parseHash(), true);
  },

  _parseHash() {
    const h = (window.location.hash || '').replace('#', '');
    return PAGE_META[h] ? h : 'dashboard';
  },

  go(pageKey, isInitial = false) {
    if (!PAGE_META[pageKey]) pageKey = 'dashboard';
    if (this.current === pageKey && !isInitial) return;
    this.current = pageKey;

    document.querySelectorAll('.page').forEach((el) => {
      el.classList.toggle('active', el.dataset.page === pageKey);
    });
    document.querySelectorAll('.nav-link').forEach((el) => {
      el.classList.toggle('active', el.dataset.nav === pageKey);
    });

    const meta = PAGE_META[pageKey];
    document.getElementById('pageTitle').textContent = meta.title;
    document.getElementById('breadcrumbCurrent').textContent = meta.crumb;

    const searchWrap = document.getElementById('globalSearchWrap');
    const searchInput = document.getElementById('globalSearch');
    searchWrap.style.visibility = meta.search ? 'visible' : 'hidden';
    searchInput.value = '';
    searchInput.placeholder = meta.placeholder || 'Search…';

    window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'auto' });

    const mod = HMD.pages[pageKey];
    if (mod && typeof mod.load === 'function') mod.load();

    // Close mobile sidebar on navigation.
    document.getElementById('sidebar').classList.remove('open');
    document.getElementById('scrim').classList.remove('show');
  },

  reload() {
    const mod = HMD.pages[this.current];
    if (mod && typeof mod.load === 'function') mod.load();
  },

  search(term) {
    const mod = HMD.pages[this.current];
    if (mod && typeof mod.search === 'function') mod.search(term);
  },
};
