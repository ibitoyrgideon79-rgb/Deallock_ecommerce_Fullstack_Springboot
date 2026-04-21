// User dashboard UI logic.
// Key point: always treat non-JSON responses as "not logged in" (Spring redirects to /login).

function showToast(message, type) {
  const t = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  t.className = `fixed bottom-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px]`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function naira(amount) {
  const n = typeof amount === 'number' ? amount : Number(amount || 0);
  return `\u20A6 ${n.toLocaleString()}`;
}

async function apiJson(url, options) {
  const res = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options && options.headers ? options.headers : {})
    }
  });

  const contentType = (res.headers.get('content-type') || '').toLowerCase();

  // If auth expired, Spring Security typically redirects to /login and returns HTML.
  if (res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Session expired. Please log in again.');
    e.redirectToLogin = true;
    throw e;
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((data && data.message) ? data.message : `Request failed (${res.status}).`);
  }
  return data;
}

// Toggle Sidebar on Mobile
function toggleSidebar() {
  document.getElementById('sidebar')?.classList.toggle('hidden');
}

// Tab Switching
function showTab(tab) {
  document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
  document.getElementById(tab + '-tab')?.classList.add('active');

  document.querySelectorAll('.tab-link').forEach(link => link.classList.remove('active', 'bg-gray-100'));
  if (typeof event !== 'undefined' && event?.currentTarget) {
    event.currentTarget.classList.add('active', 'bg-gray-100');
  }
}

let dealsCache = Array.isArray(window.__DEALLOCK_DEALS__) ? window.__DEALLOCK_DEALS__ : [];
let dealFilter = 'all'; // all | active | completed

function dealUiStage(deal) {
  const status = (deal?.status || '').toString().toLowerCase();
  if (deal?.deliveryConfirmedAt) return 'completed';
  if (status.includes('rejected')) return 'completed';
  return 'active';
}

function dealStatusLabel(deal) {
  const raw = (deal?.status || '').toString().trim();
  if (!raw) return 'PENDING';
  if (raw.toLowerCase().includes('pending')) return 'PENDING';
  if (raw.toLowerCase() === 'approved') return 'APPROVED';
  if (raw.toLowerCase().includes('reject')) return 'REJECTED';
  return raw.toUpperCase();
}

// Filter Deals (buttons: All / Active / Completed)
function filterDeals(type) {
  dealFilter = type;
  document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
  if (typeof event !== 'undefined' && event?.currentTarget) {
    event.currentTarget.classList.add('active');
  }
  renderDealsTable();
}

async function loadDeals() {
  const tbody = document.getElementById('deals-table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-gray-500">Loading...</td></tr>`;
  }

  try {
    dealsCache = await apiJson('/api/deals');
    renderDealsTable();
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }

    // If we have server-rendered/initial deals, keep showing them so filters still work.
    if (Array.isArray(dealsCache) && dealsCache.length > 0) {
      showToast(e?.message || 'Failed to refresh deals list.', 'error');
      renderDealsTable();
      return;
    }

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
    const labelId = dealId != null ? `DL-${dealId}` : `DL-${idx + 1}`;
    const title = (deal?.title || 'Untitled Deal').toString();
    const price = naira(deal?.value || 0);
    const statusLabel = dealStatusLabel(deal);
    const detailsHref = dealId != null ? `/dashboard/deal/${dealId}` : '#';

    return `
      <tr class="hover:bg-gray-50">
        <td class="p-5">${idx + 1}</td>
        <td class="p-5 font-medium">${escapeHtml(labelId)}</td>
        <td class="p-5">${escapeHtml(title)}</td>
        <td class="p-5 font-medium">${escapeHtml(price)}</td>
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

function renderOrdersTable() {
  const tbody = document.getElementById('orders-table-body');
  if (!tbody) return;
  tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">No orders yet.</td></tr>`;
}

// New Deal Modal Functions
function openNewDealModal() {
  document.getElementById('new-deal-modal')?.classList.remove('hidden');
  document.getElementById('new-deal-modal')?.classList.add('flex');
  calculatePaymentPlan();
}

function closeNewDealModal() {
  document.getElementById('new-deal-modal')?.classList.add('hidden');
  document.getElementById('new-deal-modal')?.classList.remove('flex');
}

function calculatePaymentPlan() {
  const value = parseFloat(document.getElementById('expected-value')?.value) || 0;
  const weeks = parseInt(document.getElementById('weeks')?.value) || 0;

  const holdingFee = value * 0.05 * weeks;
  const vat = holdingFee * 0.075;

  const logisticsEstimate = 0;
  const totalEstimate = value + holdingFee + vat + logisticsEstimate;
  const upfrontEstimate = (value * 0.5) + logisticsEstimate;

  const planItem = document.getElementById('plan-item-value');
  const planHolding = document.getElementById('plan-holding');
  const planTotal = document.getElementById('plan-total');
  const planUpfront = document.getElementById('plan-upfront');
  if (planItem) planItem.textContent = naira(value);
  if (planHolding) planHolding.textContent = naira(holdingFee + vat);
  if (planTotal) planTotal.textContent = naira(totalEstimate);
  if (planUpfront) planUpfront.textContent = naira(upfrontEstimate);
}

async function submitNewDeal() {
  const itemName = document.getElementById('item-name')?.value?.trim() || '';
  const link = document.getElementById('item-link')?.value?.trim() || '';
  const sellerName = document.getElementById('seller-name')?.value?.trim() || '';
  const sellerPhone = document.getElementById('seller-phone')?.value?.trim() || '';
  const sellerAddress = document.getElementById('seller-address')?.value?.trim() || '';
  const deliveryAddress = document.getElementById('delivery-address')?.value?.trim() || '';
  const itemSize = document.getElementById('item-size')?.value?.trim() || '';
  const value = document.getElementById('expected-value')?.value;
  const weeks = document.getElementById('weeks')?.value;
  const description = document.getElementById('description')?.value?.trim() || '';

  let hasError = false;
  document.querySelectorAll('.error-text').forEach(el => (el.textContent = ''));

  if (!itemName) {
    document.getElementById('error-item-name').textContent = 'Item Name is required';
    hasError = true;
  }
  if (!value || Number(value) <= 0) {
    document.getElementById('error-value').textContent = 'Valid Expected Value is required';
    hasError = true;
  }
  if (!weeks || Number(weeks) <= 0) {
    document.getElementById('error-weeks').textContent = 'Number of weeks is required';
    hasError = true;
  }
  if (hasError) return;

  const agree = document.getElementById('agree-terms');
  if (agree && !agree.checked) {
    showToast('Please agree to the Terms and Conditions.', 'error');
    return;
  }

  const fd = new FormData();
  fd.append('deal-title', itemName);
  if (link) fd.append('deal-link', link);
  fd.append('client-name', sellerName || 'N/A');
  if (sellerPhone) fd.append('seller-phone', sellerPhone);
  fd.append('seller-address', sellerAddress || 'N/A');
  fd.append('delivery-address', deliveryAddress || 'N/A');
  fd.append('item-size', itemSize || 'small');
  fd.append('weeks', String(weeks));
  fd.append('deal-value', String(value));
  if (description) fd.append('description', description);

  let payload;
  try {
    payload = await apiJson('/api/deals', { method: 'POST', body: fd });
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    showToast(e?.message || 'Failed to submit deal.', 'error');
    return;
  }

  const upfront = payload?.upfrontPaymentAmount != null ? naira(payload.upfrontPaymentAmount) : '';
  const total = payload?.totalAmount != null ? naira(payload.totalAmount) : '';
  showToast(`Deal saved. Upfront: ${upfront} Total: ${total}`, 'success');

  closeNewDealModal();
  dealFilter = 'all';
  await loadDeals();
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  renderOrdersTable();
  showTab('deals');
  loadDeals();
});
