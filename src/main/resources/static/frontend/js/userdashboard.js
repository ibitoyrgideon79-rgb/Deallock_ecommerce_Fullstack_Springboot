// User Dashboard - Cleaned & Fixed Version

function showToast(message, type = 'success') {
  const t = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  t.className = `fixed bottom-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px]`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function naira(amount) {
  const n = typeof amount === 'number' ? amount : Number(amount || 0);
  return `₦ ${n.toLocaleString()}`;
}

function resolveWeeksInput() {
  const weeksSelect = document.getElementById('weeks');
  const mode = weeksSelect?.value || '';
  if (mode === 'custom') {
    const custom = parseInt(document.getElementById('custom-weeks')?.value || '0', 10);
    return { weeks: custom, mode: 'custom' };
  }
  const weeks = parseInt(mode || '0', 10);
  return { weeks, mode: mode || '' };
}

function toggleCustomWeeks() {
  const weeksSelect = document.getElementById('weeks');
  const customInput = document.getElementById('custom-weeks');
  if (!weeksSelect || !customInput) return;
  const show = weeksSelect.value === 'custom';
  customInput.classList.toggle('hidden', !show);
  if (!show) customInput.value = '';
}

async function apiJson(url, options = {}) {
  const res = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.headers || {})
    }
  });

  const contentType = (res.headers.get('content-type') || '').toLowerCase();

  if (res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Session expired. Please log in again.');
    e.redirectToLogin = true;
    throw e;
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && data.message) ? data.message : `Request failed (${res.status})`);
  }
  return data;
}

// Tab Switching
function showTab(tab) {
  document.querySelectorAll('.tab-content').forEach(el => {
    el.classList.remove('active');
    el.classList.add('hidden');
  });
  const tabEl = document.getElementById(tab + '-tab');
  if (tabEl) {
    tabEl.classList.add('active');
    tabEl.classList.remove('hidden');
  }

  document.querySelectorAll('.tab-link').forEach(link => link.classList.remove('active'));
  const activeLink = document.querySelector(`[onclick*="showTab('${tab}')"]`);
  if (activeLink) activeLink.classList.add('active');

  if (tab === 'orders') loadOrders();
}

let dealsCache = [];
let dealFilter = 'all';
let ordersCache = [];

function dealUiStage(deal) {
  const status = (deal?.status || '').toString().toLowerCase();
  if (deal?.deliveryConfirmedAt || status.includes('completed') || status.includes('delivered') || status.includes('concluded')) 
    return 'completed';
  if (status.includes('rejected')) return 'completed';
  return 'active';
}

function dealStatusLabel(deal) {
  const raw = (deal?.status || '').toString().trim().toUpperCase();
  return raw || 'PENDING';
}

function filterDeals(type) {
  dealFilter = type;
  document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
  if (event && event.currentTarget) event.currentTarget.classList.add('active');
  renderDealsTable();
}

async function loadDeals() {
  const tbody = document.getElementById('deals-table-body');
  if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-gray-500">Loading...</td></tr>`;

  try {
    dealsCache = await apiJson('/api/deals');
    renderDealsTable();
  } catch (e) {
    if (e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    showToast(e?.message || 'Failed to load deals', 'error');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-red-600">${escapeHtml(e?.message || 'Failed to load deals.')}</td></tr>`;
    }
  }
}

function renderDealsTable() {
  const tbody = document.getElementById('deals-table-body');
  if (!tbody) return;

  let rows = Array.isArray(dealsCache) ? [...dealsCache] : [];
  if (dealFilter !== 'all') {
    rows = rows.filter(d => dealUiStage(d) === dealFilter);
  }

  if (rows.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">No deals found.</td></tr>`;
    return;
  }

  tbody.innerHTML = rows.map((deal, idx) => {
    const stage = dealUiStage(deal);
    const statusClass = stage === 'active' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-700';
    const dealId = deal?.id;
    const title = deal?.title || 'Untitled Deal';
    const price = naira(deal?.value || 0);
    const statusLabel = dealStatusLabel(deal);
    const detailsHref = dealId ? `/dashboard/deal/${dealId}` : '#';

    return `
      <tr class="hover:bg-gray-50">
        <td class="p-5">${idx + 1}</td>
        <td class="p-5 font-medium">DL-${dealId || idx + 1}</td>
        <td class="p-5">${escapeHtml(title)}</td>
        <td class="p-5 font-medium">${price}</td>
        <td class="p-5">
          <span class="px-4 py-1 text-xs font-medium rounded-full ${statusClass}">${escapeHtml(statusLabel)}</span>
        </td>
        <td class="p-5">
          <a href="${detailsHref}" class="text-blue-600 hover:underline font-medium">View Details &rarr;</a>
        </td>
      </tr>
    `;
  }).join('');
}

// Orders functions (kept minimal but working)
function renderOrdersTable() {
  const tbody = document.getElementById('orders-table-body');
  if (!tbody) return;
  if (!Array.isArray(ordersCache) || ordersCache.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">No orders yet.</td></tr>`;
    return;
  }
  // ... (add your order rendering logic here if needed)
  tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">Orders loaded (${ordersCache.length})</td></tr>`;
}

async function loadOrders() {
  const tbody = document.getElementById('orders-table-body');
  if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">Loading...</td></tr>`;

  try {
    ordersCache = await apiJson('/api/marketplace/orders');
    renderOrdersTable();
  } catch (e) {
    if (e.redirectToLogin) window.location.href = '/login';
    else showToast(e?.message || 'Failed to load orders', 'error');
  }
}

// New Deal Modal
function openNewDealModal() {
  document.getElementById('new-deal-modal').classList.remove('hidden');
  document.getElementById('new-deal-modal').classList.add('flex');
  calculatePaymentPlan();
}

function closeNewDealModal() {
  const modal = document.getElementById('new-deal-modal');
  modal.classList.add('hidden');
  modal.classList.remove('flex');
}

function calculatePaymentPlan() {
  const value = parseFloat(document.getElementById('expected-value')?.value) || 0;
  const resolved = resolveWeeksInput();
  const weeks = resolved.weeks || 0;
  const planBox = document.getElementById('payment-plan');

  if (planBox) planBox.classList.toggle('hidden', !(value > 0 && weeks > 0));

  if (value <= 0 || weeks <= 0) return;

  const holdingFee = value * 0.05 * weeks;
  const vat = holdingFee * 0.075;
  const logistics = 1950;
  const total = value + holdingFee + vat + logistics;
  const upfront = (value * 0.5) + logistics;

  document.getElementById('plan-item-value').textContent = naira(value);
  document.getElementById('plan-holding').textContent = naira(holdingFee + vat);
  document.getElementById('plan-total').textContent = naira(total);
  document.getElementById('plan-upfront').textContent = naira(upfront);
}

async function submitNewDeal() {
  const submitBtn = document.querySelector('button[onclick="submitNewDeal()"]');
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Submitting...';
  }

  // Clear previous errors
  document.querySelectorAll('.error-text').forEach(el => el.textContent = '');

  const itemName = document.getElementById('item-name')?.value?.trim() || '';
  const value = document.getElementById('expected-value')?.value;
  const resolved = resolveWeeksInput();
  const weeks = resolved.weeks;

  let hasError = false;

  if (!itemName) {
    document.getElementById('error-item-name').textContent = 'Item Name is required';
    hasError = true;
  }
  if (!value || Number(value) <= 0) {
    document.getElementById('error-value').textContent = 'Valid Expected Value is required';
    hasError = true;
  }
  if (!weeks || Number(weeks) <= 0) {
    document.getElementById('error-weeks').textContent = 'Select number of weeks';
    hasError = true;
  }
  if (hasError) {
    if (submitBtn) submitBtn.disabled = false;
    return;
  }

  if (!document.getElementById('agree-terms')?.checked) {
    showToast('Please agree to the Terms and Conditions', 'error');
    if (submitBtn) submitBtn.disabled = false;
    return;
  }

  const fd = new FormData();
  fd.append('deal-title', itemName);
  fd.append('deal-link', document.getElementById('item-link')?.value?.trim() || '');
  fd.append('client-name', document.getElementById('seller-name')?.value?.trim() || 'N/A');
  fd.append('deal-value', value);
  fd.append('description', document.getElementById('description')?.value?.trim() || '');
  fd.append('weeks', resolved.mode === 'custom' ? 'custom' : String(weeks));
  if (resolved.mode === 'custom') fd.append('customWeeks', String(weeks));
  fd.append('listing', document.querySelector('input[name="listing"]:checked')?.value || 'yes');
  fd.append('subscribeUpdates', document.getElementById('subscribe-updates')?.checked ? 'true' : 'false');

  try {
    const payload = await apiJson('/api/deals', { method: 'POST', body: fd });
    showToast(`Deal created successfully! Upfront: ${naira(payload.upfrontPaymentAmount || 0)}`, 'success');
    closeNewDealModal();
    await loadDeals();
  } catch (e) {
    if (e.redirectToLogin) window.location.href = '/login';
    else showToast(e?.message || 'Failed to submit deal', 'error');
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Submit Deal';
    }
  }
}

// Sidebar Controls
function openSidebar() {
  document.getElementById('sidebar-drawer').classList.remove('-translate-x-full');
  document.getElementById('sidebar-overlay').classList.remove('hidden');
  document.body.classList.add('overflow-hidden');
}

function closeSidebar() {
  document.getElementById('sidebar-drawer').classList.add('-translate-x-full');
  document.getElementById('sidebar-overlay').classList.add('hidden');
  document.body.classList.remove('overflow-hidden');
}

// Profile Picture
function previewImage(input) {
  if (input.files && input.files[0]) {
    const reader = new FileReader();
    reader.onload = e => {
      document.getElementById('settings-profile-preview').src = e.target.result;
      document.getElementById('profile-photo').src = e.target.result;
    };
    reader.readAsDataURL(input.files[0]);
  }
}

function changeProfilePicture() {
  document.getElementById('profile-upload').click();
}

function uploadNewPicture() {
  const input = document.getElementById('profile-upload');
  if (!input?.files?.[0]) {
    showToast('Please select a photo first', 'error');
    return;
  }
  // Add your upload logic here
  showToast('Profile picture upload feature coming soon', 'success');
}

// Date Filter Placeholder
function openDateFilter() {
  showToast('Date range filter coming soon!', 'success');
}

// Initialization
document.addEventListener('DOMContentLoaded', () => {
  // Initial tab
  showTab('deals');

  // Load data
  loadDeals();

  // Footer year
  document.getElementById('year').textContent = new Date().getFullYear();

  // Mobile menu
  document.getElementById('menu-toggle')?.addEventListener('click', () => {
    document.getElementById('mobile-menu').classList.toggle('hidden');
  });

  // Scroll to top button
  const scrollBtn = document.getElementById('scroll-top-btn');
  window.addEventListener('scroll', () => {
    if (scrollBtn) {
      scrollBtn.classList.toggle('opacity-0', window.scrollY <= 300);
      scrollBtn.classList.toggle('pointer-events-none', window.scrollY <= 300);
    }
  });
});
