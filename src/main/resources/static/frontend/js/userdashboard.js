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
  const itemName = document.getElementById('item-name')?.value?.trim() || '';
  const link = document.getElementById('item-link')?.value?.trim() || '';
  const sellerName = document.getElementById('seller-name')?.value?.trim() || '';
  const sellerPhone = document.getElementById('seller-phone')?.value?.trim() || '';
  const sellerAddress = document.getElementById('seller-address')?.value?.trim() || '';
  const deliveryAddress = document.getElementById('delivery-address')?.value?.trim() || '';
  const itemSize = document.getElementById('item-size')?.value?.trim() || '';
  const itemPhotos = Array.from(document.getElementById('item-photo')?.files || []).slice(0, 3);
  const value = document.getElementById('expected-value')?.value;
  const weeks = document.getElementById('weeks')?.value;
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
  fd.append('listing', listingChoice);
  fd.append('weeks', String(weeks));
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
      return;
    }
    showToast(e?.message || 'Failed to submit deal.', 'error');
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
  renderOrdersTable();
  showTab('deals');
  showNewDealIndicatorIfRequested();
  loadDeals();
});

  /* ── Profile image preview ── */
  function previewImage(input) {
    if (input.files && input.files[0]) {
      const reader = new FileReader();
      reader.onload = e => document.getElementById('settings-profile-preview').src = e.target.result;
      reader.readAsDataURL(input.files[0]);
    }
  }

  /* ── Header mobile menu (top nav only) ── */
  document.getElementById('menu-toggle').addEventListener('click', () => {
    document.getElementById('mobile-menu').classList.toggle('hidden');
  });

  /* ── Sidebar drawer ── */
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

  /* ── Swipe gesture ── */
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

      // Only trigger if horizontal swipe is dominant (not a vertical scroll)
      if (dy > 40) return;

      if (dx > 60 && startX < 30) openSidebar();  // right swipe from left edge
      if (dx < -60) closeSidebar();                // left swipe anywhere closes
    }, { passive: true });
  })();

  /* ── Tab switching ── */
  function showTab(name) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.tab-link').forEach(el => el.classList.remove('active'));
    document.getElementById(name + '-tab').classList.add('active');
    const link = document.querySelector(`[onclick*="showTab('${name}')"]`);
    if (link) link.classList.add('active');
  }

  /* ── Deal filter buttons ── */
  function filterDeals(type) {
    document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
    event.currentTarget.classList.add('active');
    // Wire to your actual filter logic in userdashboard.js
  }

  /* ── New Deal modal ── */
  function openNewDealModal()  { document.getElementById('new-deal-modal').classList.remove('hidden'); }
  function closeNewDealModal() { document.getElementById('new-deal-modal').classList.add('hidden'); }

  /* ── Payment plan calculator ── */
  function calculatePaymentPlan() {
    const value   = parseFloat(document.getElementById('expected-value').value) || 0;
    const weeks   = parseInt(document.getElementById('weeks').value) || 0;
    const plan    = document.getElementById('payment-plan');

    if (value <= 0 || weeks <= 0) { plan.classList.add('hidden'); return; }

    const holdingRate = 0.05;
    const vatRate     = 0.075;
    const logistics   = 1950;

    const holding  = value * holdingRate;
    const vat      = holding * vatRate;
    const totalFee = holding + vat;
    const total    = value + totalFee + logistics;
    const upfront  = (value * 0.5) + logistics;

    const fmt = n => '₦ ' + n.toLocaleString('en-NG', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    document.getElementById('plan-item-value').textContent = fmt(value);
    document.getElementById('plan-holding').textContent    = fmt(totalFee);
    document.getElementById('plan-total').textContent      = fmt(total);
    document.getElementById('plan-upfront').textContent    = fmt(upfront);

    plan.classList.remove('hidden');
  }

  /* ── Date filter placeholder ── */
  function openDateFilter() {
    alert('Date range filter coming soon!');
  }

  /* ── Newsletter ── */
  function handleSubscribe(e) {
    e.preventDefault();
    const email = document.getElementById('newsletter-email').value.trim();
    if (!email) return;
    alert('Subscribed! Thank you, ' + email);
    document.getElementById('newsletter-email').value = '';
  }

  /* ── Scroll to top ── */
  function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }); }

  const scrollBtn = document.getElementById('scroll-top-btn');
  window.addEventListener('scroll', () => {
    if (window.scrollY > 300) {
      scrollBtn.classList.remove('opacity-0', 'pointer-events-none');
    } else {
      scrollBtn.classList.add('opacity-0', 'pointer-events-none');
    }
  });

  /* ── Footer year ── */
  document.getElementById('year').textContent = new Date().getFullYear();

  /* ── Profile picture click (sidebar avatar) ── */
  function changeProfilePicture() {
    document.getElementById('profile-upload') && document.getElementById('profile-upload').click();
  }

  /* ── Upload new picture ── */
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

  /* ── Submit new deal ── */
  function submitNewDeal() {
    let valid = true;

    const itemName = document.getElementById('item-name').value.trim();
    document.getElementById('error-item-name').textContent = '';
    if (!itemName) {
      document.getElementById('error-item-name').textContent = 'Item name is required.';
      valid = false;
    }

    const value = parseFloat(document.getElementById('expected-value').value);
    document.getElementById('error-value').textContent = '';
    if (!value || value <= 0) {
      document.getElementById('error-value').textContent = 'Please enter a valid expected value.';
      valid = false;
    }

    const weeks = parseInt(document.getElementById('weeks').value);
    document.getElementById('error-weeks').textContent = '';
    if (!weeks || weeks <= 0) {
      document.getElementById('error-weeks').textContent = 'Please enter the number of installments.';
      valid = false;
    }

    if (!document.getElementById('agree-terms').checked) {
      alert('Please agree to the Terms and Conditions.');
      valid = false;
    }

    if (!valid) return;

    const listing    = document.querySelector('input[name="listing"]:checked')?.value || 'no';
    const photoInput = document.getElementById('item-photo');
    const formData   = new FormData();

    formData.append('title',           itemName);
    formData.append('itemLink',        document.getElementById('item-link').value.trim());
    formData.append('sellerName',      document.getElementById('seller-name').value.trim());
    formData.append('sellerPhone',     document.getElementById('seller-phone').value.trim());
    formData.append('sellerAddress',   document.getElementById('seller-address').value.trim());
    formData.append('deliveryAddress', document.getElementById('delivery-address').value.trim());
    formData.append('itemSize',        document.getElementById('item-size').value);
    formData.append('value',           value);
    formData.append('description',     document.getElementById('description').value.trim());
    formData.append('weeks',           weeks);
    formData.append('listing',         listing);
    formData.append('subscribeUpdates',document.getElementById('subscribe-updates').checked);

    if (photoInput.files.length > 0) {
      for (const file of photoInput.files) formData.append('photos', file);
    }

    fetch('/dashboard/deals/new', { method: 'POST', body: formData })
      .then(r => {
        if (r.ok) {
          alert('Deal submitted successfully!');
          closeNewDealModal();
          window.location.reload();
        } else {
          alert('Failed to submit deal. Please try again.');
        }
      })
      .catch(() => alert('Network error. Please try again.'));
  }

  /*<![CDATA[*/
  window.__DEALLOCK_DEALS__         = [[${dealsVm}]];
  window.__DEALLOCK_CURRENT_EMAIL__ = [[${currentUser != null ? currentUser.email : null}]];
  window.__DEALLOCK_CURRENT_NAME__  = [[${currentUser != null ? currentUser.fullName : null}]];
  /*]]>*/
