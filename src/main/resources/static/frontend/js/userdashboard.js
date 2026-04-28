// ==================== FULL MERGED JAVASCRIPT FOR DASHBOARD + NEW MODAL ====================

// ====================== UTILITY FUNCTIONS ======================

function showToast(message, type = 'success') {
  const toast = document.createElement('div');
  const bg = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  toast.className = `fixed bottom-6 right-6 z-[9999] ${bg} text-white px-5 py-3.5 rounded-2xl shadow-2xl text-sm max-w-xs`;
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 4500);
}

function showShortPopup(message, type = 'success') {
  const popup = document.createElement('div');
  const bg = type === 'error' ? 'bg-red-600' : 'bg-black';
  popup.className = `fixed top-6 right-6 z-[9999] ${bg} text-white px-5 py-3 rounded-2xl shadow-2xl text-sm max-w-xs`;
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
  const n = Number(amount || 0);
  return '₦ ' + n.toLocaleString('en-NG');
}

// apiJson helper — treats non-JSON responses as session expiry
// (Spring Security redirects expired sessions to /login as HTML)
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
    const err = new Error('Session expired. Please log in again.');
    err.redirectToLogin = true;
    throw err;
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Request failed (${res.status})`);
  return data;
}

// ====================== DEALS STATE ======================

let dealsCache = Array.isArray(window.__DEALLOCK_DEALS__) ? window.__DEALLOCK_DEALS__ : [];
let dealFilter = 'all'; // all | active | completed
let ordersCache = [];

// ====================== DEAL HELPERS ======================

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

// ====================== DEALS TABLE ======================

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
          ${canExtend && dealId != null
            ? `<button onclick="requestPaymentExtension(${dealId})" class="ml-3 text-[11px] border border-black px-2 py-1 hover:bg-black hover:text-white">Extend Payment Period</button>`
            : ''}
        </td>
      </tr>
    `;
  }).join('');
}

function filterDeals(type) {
  dealFilter = type;
  document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
  if (typeof event !== 'undefined' && event?.currentTarget) {
    event.currentTarget.classList.add('active');
  }
  renderDealsTable();
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
    showToast(
      `Extension added (+${payload?.addedWeeks || weeks} week). Extra fee: ${naira(payload?.extensionFeeAdded || 0)}`,
      'success'
    );
    await loadDeals();
  } catch (e) {
    if (e && e.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    showToast(e?.message || 'Could not extend payment period.', 'error');
  }
}

// ====================== ORDERS TABLE ======================

function orderStatusClass(status) {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT':     return 'bg-yellow-100 text-yellow-700';
    case 'PAYMENT_SUBMITTED':   return 'bg-blue-100 text-blue-700';
    case 'PAYMENT_NOT_RECEIVED':return 'bg-red-100 text-red-700';
    case 'PAYMENT_RECEIVED':    return 'bg-indigo-100 text-indigo-700';
    case 'PROCESSING':          return 'bg-purple-100 text-purple-700';
    case 'SHIPPED':             return 'bg-cyan-100 text-cyan-700';
    case 'DELIVERED':           return 'bg-emerald-100 text-emerald-700';
    case 'REVIEW':              return 'bg-gray-200 text-gray-700';
    default:                    return 'bg-gray-100 text-gray-700';
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

// ====================== NEWSLETTER ======================

async function subscribeCurrentUser(source = 'dashboard-deal-popup') {
  const emailFromWindow = (window.__DEALLOCK_CURRENT_EMAIL__ || '').toString().trim();
  const emailFromInput = document.querySelector('#settings-tab input[type="email"]')?.value?.trim() || '';
  const email = emailFromWindow || emailFromInput;
  if (!email) throw new Error('Email not found');

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
  setTimeout(() => { if (document.body.contains(box)) box.remove(); }, 10000);
}

// ====================== PROFILE ======================

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

function changeProfilePicture() {
  document.getElementById('profile-upload')?.click();
}

function uploadNewPicture() {
  const input = document.getElementById('profile-upload');
  if (!input || !input.files || !input.files[0]) {
    alert('Please select a photo first using the camera icon.');
    return;
  }
  const formData = new FormData();
  formData.append('file', input.files[0]);
  fetch('/profile/upload', { method: 'POST', body: formData })
    .then(r => r.ok ? alert('Profile picture updated!') : alert('Upload failed, please try again.'))
    .catch(() => alert('Network error. Please try again.'));
}

// ====================== MISC UI ======================

function toggleSidebar() {
  document.getElementById('sidebar')?.classList.toggle('hidden');
}

function openDateFilter() {
  alert('Date range filter coming soon!');
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function showNewDealIndicatorIfRequested() {
  const params = new URLSearchParams(window.location.search);
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

// ====================== MAIN SCRIPT ======================

document.addEventListener('DOMContentLoaded', () => {

  // ── Mobile user menu ──
  const triggers = document.querySelectorAll('.user-trigger');
  triggers.forEach(trigger => {
    trigger.addEventListener('click', e => {
      if (window.innerWidth >= 992) return;
      e.stopPropagation();
      const item = trigger.closest('.user-menu-item');
      const wasOpen = item.classList.contains('open');
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
      if (!wasOpen) item.classList.add('open');
    });
  });

  document.addEventListener('click', e => {
    if (window.innerWidth >= 992) return;
    if (!e.target.closest('.user-menu-item')) {
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
    }
  });

  // ── Tab switching ──
  // CSS controls visibility via .tab-content { display:none } / .tab-content.active { display:block }
  // so we manage the 'active' class here, NOT 'hidden'.
  window.showTab = function(tab) {
    document.querySelectorAll('.tab-content').forEach(el => {
      el.classList.remove('active');
    });
    const tabEl = document.getElementById(tab + '-tab');
    if (tabEl) tabEl.classList.add('active');

    document.querySelectorAll('.tab-link').forEach(link => link.classList.remove('active'));
    const activeLink = document.querySelector(`[onclick*="showTab('${tab}')"]`);
    if (activeLink) activeLink.classList.add('active');

    if (tab === 'orders') loadOrders();
  };

  // ── Sidebar drawer ──
  window.openSidebar = function() {
    document.getElementById('sidebar-drawer').classList.remove('-translate-x-full');
    document.getElementById('sidebar-overlay').classList.remove('hidden');
    document.body.classList.add('overflow-hidden');
  };

  window.closeSidebar = function() {
    document.getElementById('sidebar-drawer').classList.add('-translate-x-full');
    document.getElementById('sidebar-overlay').classList.add('hidden');
    document.body.classList.remove('overflow-hidden');
  };

  // ── Mobile top nav menu ──
  document.getElementById('menu-toggle')?.addEventListener('click', () => {
    document.getElementById('mobile-menu')?.classList.toggle('hidden');
  });

  // ── Swipe gesture for sidebar ──
  (function () {
    let startX = 0;
    let startY = 0;

    document.addEventListener('touchstart', e => {
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
    }, { passive: true });

    document.addEventListener('touchend', e => {
      const dx = e.changedTouches[0].clientX - startX;
      const dy = Math.abs(e.changedTouches[0].clientY - startY);
      if (dy > 40) return; // ignore vertical-dominant swipes
      if (dx > 60 && startX < 30) window.openSidebar();  // right swipe from left edge
      if (dx < -60) window.closeSidebar();                // left swipe anywhere closes
    }, { passive: true });
  })();

  // ── Scroll-to-top button ──
  const scrollBtn = document.getElementById('scroll-top-btn');
  if (scrollBtn) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 300) {
        scrollBtn.classList.remove('opacity-0', 'pointer-events-none');
      } else {
        scrollBtn.classList.add('opacity-0', 'pointer-events-none');
      }
    });
  }

  // ── Footer year ──
  const yearEl = document.getElementById('year');
  if (yearEl) yearEl.textContent = new Date().getFullYear();

  // ====================== NEW DEAL MODAL ======================
  const modal = document.getElementById('create-deal-modal');
  const form  = document.getElementById('new-deal-form');
  const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

  window.openModal = function() {
    if (modal) modal.classList.add('active');
  };

  window.closeModal = function() {
    if (modal) modal.classList.remove('active');
    if (form) form.reset();
    clearPreview();
  };

  modal?.addEventListener('click', e => {
    if (e.target === modal) window.closeModal();
  });

  document.getElementById('close-modal')?.addEventListener('click', window.closeModal);
  document.getElementById('cancel-create')?.addEventListener('click', window.closeModal);

  // ── File Upload ──
  const fileInput        = document.getElementById('item-photo');
  const uploadArea       = document.getElementById('upload-area');
  const previewContainer = document.getElementById('preview-container');
  const previewImg       = document.getElementById('preview-img');
  const removeBtn        = document.getElementById('remove-preview');

  function showPreview(file) {
    if (!file.type.startsWith('image/')) {
      alert('Please select an image file');
      return;
    }
    const reader = new FileReader();
    reader.onload = e => {
      previewImg.src = e.target.result;
      previewContainer.classList.remove('hidden');
      uploadArea.style.display = 'none';
    };
    reader.readAsDataURL(file);
  }

  function clearPreview() {
    previewContainer?.classList.add('hidden');
    if (uploadArea) uploadArea.style.display = 'block';
    if (fileInput) fileInput.value = '';
  }

  fileInput?.addEventListener('change', e => {
    if (e.target.files[0]) showPreview(e.target.files[0]);
  });

  uploadArea?.addEventListener('dragover', e => {
    e.preventDefault();
    uploadArea.classList.add('border-green-500', 'bg-green-50');
  });

  uploadArea?.addEventListener('dragleave', () => {
    uploadArea.classList.remove('border-green-500', 'bg-green-50');
  });

  uploadArea?.addEventListener('drop', e => {
    e.preventDefault();
    uploadArea.classList.remove('border-green-500', 'bg-green-50');
    if (e.dataTransfer.files[0]) {
      fileInput.files = e.dataTransfer.files;
      showPreview(e.dataTransfer.files[0]);
    }
  });

  removeBtn?.addEventListener('click', clearPreview);

  // ── Payment Preview ──
  const valueInput    = document.getElementById('deal-value');
  const weeksSelect   = document.getElementById('weeks');
  const customWeeks   = document.getElementById('custom-weeks');
  const customGroup   = document.getElementById('custom-weeks-group');
  const extraFeeRow   = document.getElementById('extra-fee-row');
  const breakdown     = document.getElementById('breakdown');

  const displayValue   = document.getElementById('display-value');
  const displayService = document.getElementById('display-service-fee');
  const displayExtra   = document.getElementById('display-extra-fee');
  const displayVat     = document.getElementById('display-vat');
  const displayTotal   = document.getElementById('display-total');
  const upfrontEl      = document.getElementById('upfront-amount');
  const weeklyCountEl  = document.getElementById('weekly-count');
  const weeklyAmountEl = document.getElementById('weekly-amount');

  function updatePaymentPreview() {
    const value = parseFloat(valueInput?.value) || 0;
    if (value < 1000) {
      resetPaymentDisplays();
      return;
    }

    let weeks = parseInt(weeksSelect?.value) || 0;
    let extraFeePercent = 0;
    const isCustom = weeksSelect?.value === 'custom';

    if (isCustom) {
      customGroup?.classList.remove('hidden');
      weeks = parseInt(customWeeks?.value) || 0;
      if (weeks > 2) extraFeePercent = 0.05;
    } else {
      customGroup?.classList.add('hidden');
      if (customWeeks) customWeeks.value = '';
    }

    if (weeks < 1) {
      resetPaymentDisplays();
      return;
    }

    const serviceFee = value * 0.05 * weeks;
    const extraFee   = (value + serviceFee) * extraFeePercent;
    const subTotal   = value + serviceFee + extraFee;
    const vat        = subTotal * 0.075;
    const grandTotal = subTotal + vat;

    if (displayValue)   displayValue.textContent   = value.toLocaleString();
    if (displayService) displayService.textContent = 'NGN ' + serviceFee.toLocaleString();

    if (extraFeePercent > 0) {
      extraFeeRow?.classList.remove('hidden');
      if (displayExtra) displayExtra.textContent = 'NGN ' + extraFee.toLocaleString();
    } else {
      extraFeeRow?.classList.add('hidden');
    }

    if (displayVat)   displayVat.textContent   = 'NGN ' + vat.toLocaleString();
    if (displayTotal) displayTotal.textContent = 'NGN ' + grandTotal.toLocaleString();

    const upfront = grandTotal * 0.5;
    const weekly  = weeks > 0 ? (grandTotal * 0.5) / weeks : 0;

    if (upfrontEl)      upfrontEl.textContent      = 'NGN ' + Math.round(upfront).toLocaleString();
    if (weeklyCountEl)  weeklyCountEl.textContent  = weeks;
    if (weeklyAmountEl) weeklyAmountEl.textContent = 'NGN ' + Math.round(weekly).toLocaleString();

    breakdown?.classList.remove('hidden');
  }

  function resetPaymentDisplays() {
    [displayValue, displayService, displayExtra, displayVat, displayTotal, upfrontEl, weeklyAmountEl]
      .forEach(el => { if (el) el.textContent = '0'; });
    extraFeeRow?.classList.add('hidden');
    breakdown?.classList.add('hidden');
  }

  valueInput?.addEventListener('input', updatePaymentPreview);
  weeksSelect?.addEventListener('change', updatePaymentPreview);
  customWeeks?.addEventListener('input', updatePaymentPreview);

  updatePaymentPreview();

  // ====================== FORM SUBMISSION ======================
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();

    const formData    = new FormData(form);
    const submitBtn   = form.querySelector('button[type="submit"]');
    const originalText = submitBtn ? submitBtn.textContent : 'Lock Deal';

    const title            = formData.get('deal-title');
    const client           = formData.get('client-name');
    const value            = formData.get('deal-value');
    const photo            = formData.get('itemPhoto');
    const weeks            = formData.get('weeks');
    const customWeeksValue = formData.get('customWeeks');

    if (!title || !client || !value) {
      showToast('Please fill all required fields.', 'error');
      return;
    }
    if (!weeks || weeks === '') {
      showToast('Please select the number of weekly installments.', 'error');
      return;
    }
    if (weeks === 'custom' && (!customWeeksValue || Number(customWeeksValue) < 3)) {
      showToast('Please enter requested weeks (3 or more).', 'error');
      return;
    }
    if (photo && photo.size > MAX_PHOTO_BYTES) {
      showToast('Image is too large. Max 10MB.', 'error');
      return;
    }

    if (submitBtn) {
      submitBtn.disabled    = true;
      submitBtn.textContent = 'Saving...';
    }

    try {
      const res = await fetch('/api/deals', {
        method: 'POST',
        body: formData,
        credentials: 'same-origin'
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || 'Failed to create deal');
      }

      showToast('Deal created successfully! Pending Approval.', 'success');
      promptNewsletterAfterDeal();

      window.closeModal();
      form.reset();
      clearPreview();

      await loadDeals();

    } catch (err) {
      showToast(err.message || 'Failed to create deal.', 'error');
    } finally {
      if (submitBtn) {
        submitBtn.disabled    = false;
        submitBtn.textContent = originalText;
      }
    }
  });

  // ====================== INITIALIZATION ======================

  const params       = new URLSearchParams(window.location.search);
  const requestedTab = (params.get('tab') || '').toLowerCase();

  if (requestedTab === 'orders') {
    window.showTab('orders');
  } else {
    window.showTab('deals');
  }

  renderOrdersTable();
  showNewDealIndicatorIfRequested();
  loadDeals();

  // ── Expose globals (for inline onclick attributes in HTML) ──
  window.showToast              = showToast;
  window.showShortPopup         = showShortPopup;
  window.escapeHtml             = escapeHtml;
  window.naira                  = naira;
  window.loadDeals              = loadDeals;
  window.renderDealsTable       = renderDealsTable;
  window.filterDeals            = filterDeals;
  window.loadOrders             = loadOrders;
  window.renderOrdersTable      = renderOrdersTable;
  window.requestPaymentExtension= requestPaymentExtension;
  window.previewImage           = previewImage;
  window.changeProfilePicture   = changeProfilePicture;
  window.uploadNewPicture       = uploadNewPicture;
  window.toggleSidebar          = toggleSidebar;
  window.openDateFilter         = openDateFilter;
  window.scrollToTop            = scrollToTop;
  window.promptNewsletterAfterDeal = promptNewsletterAfterDeal;
});