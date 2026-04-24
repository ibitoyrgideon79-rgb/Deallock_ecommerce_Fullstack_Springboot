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

function showShortPopup(message, type = 'success') {
  const popup = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-black';
  popup.className = `fixed top-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[340px]`;
  popup.textContent = message;
  document.body.appendChild(popup);
  setTimeout(() => popup.remove(), 3500);
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
  const customWrap = document.getElementById('custom-weeks');
  if (!weeksSelect || !customWrap) return;
  const show = weeksSelect.value === 'custom';
  customWrap.classList.toggle('hidden', !show);
  if (!show) customWrap.value = '';
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
  document.querySelectorAll('.tab-content').forEach(el => {
    el.classList.remove('active');
    el.classList.add('hidden');
  });
  const tabEl = document.getElementById(tab + '-tab');
  tabEl?.classList.add('active');
  tabEl?.classList.remove('hidden');

  document.querySelectorAll('.tab-link').forEach(link => link.classList.remove('active', 'bg-gray-100'));
  if (typeof event !== 'undefined' && event?.currentTarget) {
    event.currentTarget.classList.add('active', 'bg-gray-100');
  }
  if (tab === 'orders') {
    loadOrders();
  }
}

let dealsCache = Array.isArray(window.__DEALLOCK_DEALS__) ? window.__DEALLOCK_DEALS__ : [];
let dealFilter = 'all'; // all | active | completed
let ordersCache = [];

function dealUiStage(deal) {
  const status = (deal?.status || '').toString().toLowerCase();
  if (deal?.deliveryConfirmedAt) return 'completed';
  if (status.includes('concluded') || status.includes('completed') || status.includes('delivered')) return 'completed';
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
    const canExtend = !!deal?.canRequestExtension;

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
          ${canExtend && dealId != null ? `<button onclick="requestPaymentExtension(${dealId})" class="ml-3 text-[11px] border border-black px-2 py-1 hover:bg-black hover:text-white">Extend Payment Period</button>` : ''}
        </td>
      </tr>
    `;
  }).join('');
}

function orderStatusClass(status) {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT':
      return 'bg-yellow-100 text-yellow-700';
    case 'PAYMENT_SUBMITTED':
      return 'bg-blue-100 text-blue-700';
    case 'PAYMENT_NOT_RECEIVED':
      return 'bg-red-100 text-red-700';
    case 'PAYMENT_RECEIVED':
      return 'bg-indigo-100 text-indigo-700';
    case 'PROCESSING':
      return 'bg-purple-100 text-purple-700';
    case 'SHIPPED':
      return 'bg-cyan-100 text-cyan-700';
    case 'DELIVERED':
      return 'bg-emerald-100 text-emerald-700';
    case 'REVIEW':
      return 'bg-gray-200 text-gray-700';
    default:
      return 'bg-gray-100 text-gray-700';
  }
}

function readableOrderStatus(status) {
  return (status || 'PENDING_PAYMENT').toString().toUpperCase().replaceAll('_', ' ');
}

function renderOrdersTable() {
  const tbody = document.getElementById('orders-table-body');
  if (!tbody) return;
  if (!Array.isArray(ordersCache) || ordersCache.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">No orders yet.</td></tr>`;
    return;
  }
  tbody.innerHTML = ordersCache.map((order, idx) => {
    const code = order?.orderCode || `MO-${order?.id || idx + 1}`;
    const summaryName = order?.summaryName || 'Marketplace Order';
    const status = (order?.status || 'PENDING_PAYMENT').toString().toUpperCase();
    const badgeClass = orderStatusClass(status);
    const track = `Pay via ${order?.paymentMethod || 'BANK_TRANSFER'} · ${order?.deliveryMethod === 'pickup' ? 'Store pickup' : 'Door delivery'}`;
    const detailsHref = order?.id ? `/dashboard/order/${order.id}` : '#';
    return `
      <tr class="hover:bg-gray-50">
        <td class="p-5">${idx + 1}</td>
        <td class="p-5 font-medium">${escapeHtml(code)}</td>
        <td class="p-5">${escapeHtml(summaryName)}</td>
        <td class="p-5 font-medium">${escapeHtml(naira(order?.totalAmount || 0))}</td>
        <td class="p-5">
          <span class="px-4 py-1 text-xs font-medium rounded-full ${badgeClass}">${escapeHtml(readableOrderStatus(status))}</span>
        </td>
        <td class="p-5">
          <a href="${detailsHref}" class="text-blue-600 hover:underline font-medium">Order Details / Track</a>
          <div class="text-xs text-gray-600 mt-1">${escapeHtml(track)}</div>
        </td>
      </tr>
    `;
  }).join('');
}

async function loadOrders() {
  const tbody = document.getElementById('orders-table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-gray-500">Loading...</td></tr>`;
  }
  try {
    ordersCache = await apiJson('/api/marketplace/orders');
    renderOrdersTable();
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-red-600">${escapeHtml(e?.message || 'Failed to load orders.')}</td></tr>`;
    }
  }
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
  const resolved = resolveWeeksInput();
  const weeks = resolved.weeks || 0;
  const planBox = document.getElementById('payment-plan');

  // Hide the entire breakdown until we have enough inputs to calculate something meaningful.
  if (planBox) {
    const shouldShow = value > 0 && weeks > 0;
    planBox.classList.toggle('hidden', !shouldShow);
  }

  if (value <= 0 || weeks <= 0) {
    return;
  }

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
  const submitButtons = Array.from(document.querySelectorAll('button[onclick="submitNewDeal()"]'));
  submitButtons.forEach(btn => {
    btn.disabled = true;
    btn.dataset.originalText = btn.textContent || 'Submit Deal';
    btn.textContent = 'Submitting...';
  });
  const releaseSubmit = () => {
    submitButtons.forEach(btn => {
      btn.disabled = false;
      if (btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
    });
  };

  const itemName = document.getElementById('item-name')?.value?.trim() || '';
  const link = document.getElementById('item-link')?.value?.trim() || '';
  const sellerName = document.getElementById('seller-name')?.value?.trim() || '';
  const sellerPhone = document.getElementById('seller-phone')?.value?.trim() || '';
  const sellerAddress = document.getElementById('seller-address')?.value?.trim() || '';
  const deliveryAddress = document.getElementById('delivery-address')?.value?.trim() || '';
  const itemSize = document.getElementById('item-size')?.value?.trim() || '';
  const itemPhotos = Array.from(document.getElementById('item-photo')?.files || []).slice(0, 3);
  const value = document.getElementById('expected-value')?.value;
  const resolvedWeeks = resolveWeeksInput();
  const weeks = resolvedWeeks.weeks;
  const description = document.getElementById('description')?.value?.trim() || '';
  const listingChoice = document.querySelector('input[name=\"listing\"]:checked')?.value || 'yes';
  const subscribeUpdates = !!document.getElementById('subscribe-updates')?.checked;

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
    document.getElementById('error-weeks').textContent = 'Select weeks (or enter custom weeks).';
    hasError = true;
  }
  if (hasError) {
    releaseSubmit();
    return;
  }

  const agree = document.getElementById('agree-terms');
  if (agree && !agree.checked) {
    showToast('Please agree to the Terms and Conditions.', 'error');
    releaseSubmit();
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
  fd.append('listing', listingChoice);
  fd.append('weeks', resolvedWeeks.mode === 'custom' ? 'custom' : String(weeks));
  if (resolvedWeeks.mode === 'custom') fd.append('customWeeks', String(weeks));
  fd.append('deal-value', String(value));
  fd.append('subscribeUpdates', subscribeUpdates ? 'true' : 'false');
  if (description) fd.append('description', description);
  // Send multiple files under one field name (server keeps backward compatibility too).
  itemPhotos.forEach(f => fd.append('itemPhotos', f));

  let payload;
  try {
    payload = await apiJson('/api/deals', { method: 'POST', body: fd });
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      releaseSubmit();
      return;
    }
    showToast(e?.message || 'Failed to submit deal.', 'error');
    releaseSubmit();
    return;
  }

  const upfront = payload?.upfrontPaymentAmount != null ? naira(payload.upfrontPaymentAmount) : '';
  const total = payload?.totalAmount != null ? naira(payload.totalAmount) : '';
  showToast(`Deal saved. Upfront: ${upfront} Total: ${total}`, 'success');

  if (!subscribeUpdates) {
    promptNewsletterAfterDeal();
  } else {
    showShortPopup("Thanks for subscribing. You'll hear from us faster.");
  }

  closeNewDealModal();
  dealFilter = 'all';
  await loadDeals();
  releaseSubmit();
}

async function requestPaymentExtension(dealId) {
  const raw = window.prompt('Extend by how many weeks? (1 or 2)');
  if (raw == null) return;
  const weeks = Math.max(1, Math.min(2, parseInt(String(raw).trim(), 10) || 0));
  if (!weeks) {
    showToast('Enter 1 or 2 weeks.', 'error');
    return;
  }

  try {
    const payload = await apiJson(`/api/deals/${dealId}/request-extension?weeks=${weeks}`, { method: 'POST' });
    showToast(`Extension added (+${payload?.addedWeeks || weeks} week). Extra fee: ${naira(payload?.extensionFeeAdded || 0)}`, 'success');
    await loadDeals();
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    showToast(e?.message || 'Could not extend payment period.', 'error');
  }
}

async function subscribeCurrentUser(source = 'dashboard-deal-popup') {
  const emailFromWindow = (window.__DEALLOCK_CURRENT_EMAIL__ || '').toString().trim();
  const emailFromInput = document.querySelector('#settings-tab input[type="email"]')?.value?.trim() || '';
  const email = emailFromWindow || emailFromInput;
  if (!email) {
    showShortPopup('Could not find your email for subscription.', 'error');
    return false;
  }

  const res = await fetch('/api/newsletter/subscribe', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      email,
      name: window.__DEALLOCK_CURRENT_NAME__ || '',
      source
    })
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);
  return true;
}

function promptNewsletterAfterDeal() {
  const box = document.createElement('div');
  box.className = 'fixed inset-0 z-[9999] bg-black/55 flex items-center justify-center p-4';
  box.innerHTML = `
    <div class="bg-white rounded-2xl p-6 w-full max-w-md shadow-2xl">
      <h3 class="text-lg font-semibold mb-2">Stay Updated?</h3>
      <p class="text-sm text-gray-700 mb-5">Would you like to subscribe so you hear from us faster on deals and updates?</p>
      <div class="flex gap-3">
        <button id="sub-yes" class="flex-1 bg-black text-white py-2.5 rounded-xl">Yes, subscribe me</button>
        <button id="sub-no" class="flex-1 border border-gray-300 py-2.5 rounded-xl">No, thanks</button>
      </div>
    </div>
  `;

  const close = () => box.remove();
  box.querySelector('#sub-no')?.addEventListener('click', close);
  box.querySelector('#sub-yes')?.addEventListener('click', async () => {
    const yesBtn = box.querySelector('#sub-yes');
    if (yesBtn) yesBtn.textContent = 'Subscribing...';
    try {
      await subscribeCurrentUser();
      close();
      showShortPopup("You'll hear from us faster. Subscription complete.");
    } catch (e) {
      close();
      showShortPopup(e?.message || 'Subscription failed. Try again later.', 'error');
    }
  });

  document.body.appendChild(box);
  setTimeout(() => {
    if (document.body.contains(box)) box.remove();
  }, 10000);
}

function showNewDealIndicatorIfRequested() {
  const params = new URLSearchParams(window.location.search || '');
  if (params.get('newDeal') !== '1') return;

  const btn = document.getElementById('new-deal-cta');
  if (!btn) return;

  btn.classList.add('ring-4', 'ring-emerald-400', 'ring-offset-2', 'animate-pulse');
  const tip = document.createElement('div');
  tip.className = 'fixed top-24 right-6 z-[9999] bg-emerald-600 text-white px-4 py-3 rounded-xl shadow-lg text-sm';
  tip.textContent = 'Next step: click New Deal to submit your item.';
  document.body.appendChild(tip);
  setTimeout(() => {
    tip.remove();
    btn.classList.remove('ring-4', 'ring-emerald-400', 'ring-offset-2', 'animate-pulse');
  }, 6000);
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search || '');
  const requestedTab = (params.get('tab') || '').toLowerCase();
  // Preload orders in the background so My Orders is ready immediately.
  loadOrders();
  if (requestedTab === 'orders') {
    showTab('orders');
  } else {
    showTab('deals');
  }
  renderOrdersTable();
  toggleCustomWeeks();
  showNewDealIndicatorIfRequested();
  loadDeals();
});

// Browser back/forward cache can restore stale DOM. Refresh orders and deals when page is shown again.
window.addEventListener('pageshow', () => {
  loadOrders();
  loadDeals();
});

function previewImage(input) {
  if (input.files && input.files[0]) {
    const reader = new FileReader();
    reader.onload = e => {
      const preview = document.getElementById('settings-profile-preview');
      if (preview) preview.src = e.target.result;
    };
    reader.readAsDataURL(input.files[0]);
  }
}

function openSidebar() {
  document.getElementById('sidebar-drawer')?.classList.remove('-translate-x-full');
  document.getElementById('sidebar-overlay')?.classList.remove('hidden');
  document.body.classList.add('overflow-hidden');
}

function closeSidebar() {
  document.getElementById('sidebar-drawer')?.classList.add('-translate-x-full');
  document.getElementById('sidebar-overlay')?.classList.add('hidden');
  document.body.classList.remove('overflow-hidden');
}

function openDateFilter() {
  showToast('Date range filter is coming soon.', 'success');
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function changeProfilePicture() {
  document.getElementById('profile-upload')?.click();
}

function uploadNewPicture() {
  const input = document.getElementById('profile-upload');
  if (!input || !input.files || !input.files[0]) {
    showToast('Select a photo first.', 'error');
    return;
  }
  const formData = new FormData();
  formData.append('profileImage', input.files[0]);
  fetch('/profile/upload', {
    method: 'POST',
    body: formData,
    credentials: 'same-origin'
  })
    .then(r => {
      if (!r.ok) throw new Error('Upload failed');
      showToast('Profile picture updated.', 'success');
    })
    .catch(() => showToast('Upload failed, please try again.', 'error'));
}
