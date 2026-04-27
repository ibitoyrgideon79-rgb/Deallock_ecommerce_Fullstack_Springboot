function toggleNavDropdown(id) {
  const target = document.getElementById(id);
  const isHidden = target.classList.contains('hidden');

  // Close all other dropdowns
  document.querySelectorAll('[id$="-drop"]').forEach(el => {
    el.classList.add('hidden');
    const btn = el.previousElementSibling;
    if (btn) btn.querySelector('.fas')?.classList.replace('fa-minus', 'fa-plus');
  });

  if (isHidden) {
    target.classList.remove('hidden');
    target.previousElementSibling.querySelector('.fas')?.classList.replace('fa-plus', 'fa-minus');
  }
}

function showToast(message, type) {
  const t = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  t.className = `fixed bottom-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px]`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function handleApiError(err) {
  const msg = err?.message || 'Request failed.';
  showToast(msg, 'error');
  if (err?.redirectToLogin) {
    setTimeout(() => { window.location.href = '/login'; }, 700);
  }
}

// Never fail silently in admin: surface any JS/runtime/API errors as toasts.
window.addEventListener('error', (e) => {
  const msg = e?.message || 'Unexpected error.';
  showToast(msg, 'error');
});
window.addEventListener('unhandledrejection', (e) => {
  handleApiError(e?.reason);
});

let currentPage = 'Pending Approval';
let dealsCache = [];
let marketItemsCache = [];
let ordersCache = [];
let statusFilter = 'all'; // all | active | completed

function naira(amount) {
  const n = typeof amount === 'number' ? amount : Number(amount || 0);
  return `\u20A6${n.toLocaleString()}`;
}

function getStatus(deal) {
  return (deal?.status || '').toString().trim();
}

function getPaymentStatus(deal) {
  return (deal?.paymentStatus || '').toString().trim();
}

function getBalanceStatus(deal) {
  return (deal?.balancePaymentStatus || '').toString().trim();
}

function isApproved(deal) {
  return getStatus(deal).toLowerCase() === 'approved';
}

function isRejected(deal) {
  return getStatus(deal).toLowerCase().includes('reject');
}

function isPendingApproval(deal) {
  const s = getStatus(deal).toLowerCase();
  if (!s) return true;
  return s.includes('pending');
}

function isConcluded(deal) {
  const s = getStatus(deal).toLowerCase();
  return !!deal?.deliveryConfirmedAt || isRejected(deal) || !!deal?.expiredUnpaid || s.includes('concluded') || s.includes('completed');
}

function filterStatus(type) {
  statusFilter = type;

  const btnAll = document.getElementById('btn-all');
  const btnActive = document.getElementById('btn-active');
  const btnCompleted = document.getElementById('btn-completed');

  [btnAll, btnActive, btnCompleted].forEach(b => b?.classList.remove('bg-black', 'text-white'));

  if (type === 'active') btnActive?.classList.add('bg-black', 'text-white');
  else if (type === 'completed') btnCompleted?.classList.add('bg-black', 'text-white');
  else btnAll?.classList.add('bg-black', 'text-white');

  render();
}

function switchPage(pageName) {
  currentPage = pageName;
  const title = document.getElementById('page-title');
  if (title) {
    const group = isMarketplacePage(pageName) ? 'Marketplace' : 'Deal Flow';
    title.innerText = `${group}: ${pageName}`;
  }
  updateHeaderActions();
  loadCurrentPageData().catch(e => showToast(e.message || 'Failed to load', 'error'));
}

function isMarketplacePage(pageName) {
  return pageName === 'Orders' || pageName === 'Products';
}

function updateHeaderActions() {
  const btn = document.getElementById('btn-new-product');
  if (!btn) return;
  btn.style.display = currentPage === 'Products' ? 'inline-flex' : 'none';
}

function filterDealsForPage() {
  if (currentPage === 'Products') {
    let rows = Array.isArray(marketItemsCache) ? marketItemsCache : [];
    if (statusFilter === 'active') rows = rows.filter(i => !!i?.listed);
    if (statusFilter === 'completed') rows = rows.filter(i => !i?.listed);
    return rows;
  }

  if (currentPage === 'Orders') {
    let rows = Array.isArray(ordersCache) ? ordersCache : [];
    if (statusFilter === 'active') {
      rows = rows.filter(o => (o?.status || '').toUpperCase() !== 'DELIVERED');
    }
    if (statusFilter === 'completed') {
      rows = rows.filter(o => (o?.status || '').toUpperCase() === 'DELIVERED');
    }
    return rows;
  }

  let rows = Array.isArray(dealsCache) ? dealsCache : [];

  if (statusFilter === 'active') rows = rows.filter(d => !isConcluded(d));
  if (statusFilter === 'completed') rows = rows.filter(d => isConcluded(d));

  switch (currentPage) {
    case 'Pending Approval':
      return rows.filter(d => isPendingApproval(d) && !isApproved(d) && !isRejected(d));
    case 'Payment Not Received':
      return rows.filter(d => isApproved(d) && getPaymentStatus(d).toUpperCase() === 'NOT_PAID');
    case 'Payment Confirmed':
      return rows.filter(d => isApproved(d) && getPaymentStatus(d).toUpperCase() === 'PAID_CONFIRMED' && !d?.secured);
    case 'Secured':
      return rows.filter(d => isApproved(d) && !!d?.secured);
    case 'Balance Pending':
      return rows.filter(d => isApproved(d) && !!d?.secured && getBalanceStatus(d).toUpperCase() !== 'PAID_CONFIRMED');
    case 'Delivery Initiation':
      return rows.filter(d => isApproved(d) && !!d?.secured && getBalanceStatus(d).toUpperCase() === 'PAID_CONFIRMED' && !d?.deliveryInitiatedAt);
    case 'In Transit':
      return rows.filter(d => isApproved(d) && !!d?.deliveryInitiatedAt && !d?.deliveryConfirmedAt);
    case 'Delivery Confirmation':
      return rows.filter(d => isApproved(d) && !!d?.deliveryInitiatedAt && !d?.deliveryConfirmedAt);
    case 'Concluded':
      return rows.filter(d => isConcluded(d));
    default:
      return rows;
  }
}

async function apiPost(path, body) {
  const headers = { 'Accept': 'application/json' };
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, {
    method: 'POST',
    headers,
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin'
  });
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Session expired. Please log in again.');
    e.redirectToLogin = true;
    throw e;
  }
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401) {
      const e = new Error('Session expired. Please log in again.');
      e.redirectToLogin = true;
      throw e;
    }
    throw new Error(payload?.message || `Request failed (${res.status})`);
  }
  return payload;
}

async function apiPostMultipart(path, formData) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Accept': 'application/json' },
    body: formData,
    credentials: 'same-origin'
  });
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Session expired. Please log in again.');
    e.redirectToLogin = true;
    throw e;
  }
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401) {
      const e = new Error('Session expired. Please log in again.');
      e.redirectToLogin = true;
      throw e;
    }
    throw new Error(payload?.message || `Request failed (${res.status})`);
  }
  return payload;
}

function pickFile(accept) {
  return new Promise(resolve => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept || 'image/*';
    input.onchange = () => resolve(input.files && input.files[0] ? input.files[0] : null);
    input.click();
  });
}

async function fetchJsonList(url) {
  const res = await fetch(url, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (res.status === 401 || res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Session expired. Please log in again.');
    e.redirectToLogin = true;
    throw e;
  }
  if (!res.ok) {
    throw new Error(`Failed to load (${res.status})`);
  }
  const payload = await res.json().catch(() => ([]));
  return Array.isArray(payload) ? payload : [];
}

async function approveDeal(id) {
  if (!confirm('Approve this deal?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/approve`);
    showToast('Deal approved', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function listDealOnMarketplace(id) {
  if (!confirm('List this expired unpaid item on marketplace?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/list-on-marketplace`);
    showToast('Listed on marketplace', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function toggleProductListed(id) {
  try {
    await apiPost(`/api/admin/marketplace/items/${id}/toggle-listed`);
    showToast('Updated', 'success');
    await loadMarketItems();
  } catch (e) {
    handleApiError(e);
  }
}

async function deleteProduct(id) {
  if (!confirm('Delete this marketplace item?')) return;
  try {
    const res = await fetch(`/api/admin/marketplace/items/${id}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin'
    });
    const ct = (res.headers.get('content-type') || '').toLowerCase();
    if (res.redirected || !ct.includes('application/json')) {
      const e = new Error('Session expired. Please log in again.');
      e.redirectToLogin = true;
      throw e;
    }
    const payload = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(payload?.message || `Request failed (${res.status})`);
    }
    showToast('Deleted', 'success');
    await loadMarketItems();
  } catch (e) {
    handleApiError(e);
  }
}

async function updateOrderStatus(orderId, status) {
  if (!confirm(`Update order to ${status.replaceAll('_', ' ')}?`)) return;
  try {
    const payload = await apiPost(`/api/admin/marketplace/items/orders/${orderId}/status`, { status });
    const updatedCode = payload?.order?.orderCode || `MO-${orderId}`;
    showToast(`Updated ${updatedCode} to ${status.replaceAll('_', ' ')}`, 'success');
    await loadOrders();
  } catch (e) {
    handleApiError(e);
  }
}

async function rejectDeal(id) {
  const reason = prompt('Reason for rejection (optional):') || '';
  if (!confirm('Reject this deal?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/reject`, { reason });
    showToast('Deal rejected', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function confirmPayment(id) {
  if (!confirm('Mark payment as confirmed?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/payment-confirmed`);
    showToast('Payment confirmed', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function markSecured(id) {
  if (!confirm('Upload secured photo and mark deal as secured?')) return;
  const file = await pickFile('image/*');
  if (!file) {
    showToast('Secured photo is required.', 'error');
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    showToast('Secured photo must be at most 2MB.', 'error');
    return;
  }
  const fd = new FormData();
  fd.append('securedPhoto', file);
  await apiPostMultipart(`/api/admin/deals/${id}/secured`, fd);
  showToast('Deal secured', 'success');
  await loadDeals();
}

async function confirmBalance(id) {
  if (!confirm('Mark balance as confirmed?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/balance-confirmed`);
    showToast('Balance confirmed', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function initiateDelivery(id) {
  if (!confirm('Initiate delivery?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/delivery-initiated`);
    showToast('Delivery initiated', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function confirmDelivery(id) {
  if (!confirm('Confirm delivery?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/delivery-confirmed`);
    showToast('Delivery confirmed', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

async function deleteDeal(id) {
  if (!confirm('Delete this deal?')) return;
  try {
    await apiPost(`/api/admin/deals/${id}/delete`);
    showToast('Deal deleted', 'success');
    await loadDeals();
  } catch (e) {
    handleApiError(e);
  }
}

function actionCell(deal) {
  const id = deal?.id;
  if (!id) return '';

  if (currentPage === 'Orders') {
    const status = (deal?.status || '').toUpperCase();
    const proofLink = deal?.paymentProofUploaded ? `<a href="/api/admin/marketplace/items/orders/${id}/payment-proof" target="_blank" rel="noopener" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-gray-100">VIEW PROOF</a>` : '';
    if (status === 'PENDING_PAYMENT') {
      return `<span class="text-[9px] font-black text-yellow-700">AWAITING BUYER PAYMENT</span>`;
    }
    if (status === 'PAYMENT_SUBMITTED') {
      return `
        <div class="flex gap-2 justify-center">
          ${proofLink}
          <button onclick="updateOrderStatus(${id}, 'PAYMENT_RECEIVED')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">PAYMENT RECEIVED</button>
          <button onclick="updateOrderStatus(${id}, 'PAYMENT_NOT_RECEIVED')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-gray-100">NOT RECEIVED</button>
        </div>
      `;
    }
    if (status === 'PAYMENT_NOT_RECEIVED') {
      return `
        <div class="flex gap-2 justify-center">
          ${proofLink}
          <button onclick="updateOrderStatus(${id}, 'PAYMENT_RECEIVED')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MARK RECEIVED</button>
        </div>
      `;
    }
    if (status === 'PAYMENT_RECEIVED') {
      return `<button onclick="updateOrderStatus(${id}, 'PROCESSING')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MOVE TO PROCESSING</button>`;
    }
    if (status === 'PROCESSING') {
      return `<button onclick="updateOrderStatus(${id}, 'SHIPPED')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MARK SHIPPED</button>`;
    }
    if (status === 'SHIPPED') {
      return `<button onclick="updateOrderStatus(${id}, 'DELIVERED')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MARK DELIVERED</button>`;
    }
    if (status === 'DELIVERED') {
      return `<button onclick="updateOrderStatus(${id}, 'REVIEW')" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MOVE TO REVIEW</button>`;
    }
    return `<span class="text-[9px] font-black text-emerald-700">COMPLETED</span>`;
  }

  if (currentPage === 'Products') {
    const listed = !!deal?.listed;
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="toggleProductListed(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">${listed ? 'UNLIST' : 'LIST'}</button>
        <button onclick="deleteProduct(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-gray-100">DELETE</button>
      </div>
    `;
  }

  if (currentPage === 'Pending Approval') {
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="approveDeal(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">APPROVE</button>
        <button onclick="rejectDeal(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-gray-100">REJECT</button>
      </div>
    `;
  }

  if (currentPage === 'Payment Not Received') {
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="confirmPayment(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">CONFIRM PAYMENT</button>
      </div>
    `;
  }

  if (currentPage === 'Payment Confirmed') {
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="markSecured(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">MARK SECURED</button>
      </div>
    `;
  }

  if (currentPage === 'Balance Pending' || currentPage === 'Secured') {
    if ((deal?.balancePaymentStatus || '').toString().toUpperCase() !== 'PAID_CONFIRMED') {
      return `
        <div class="flex gap-2 justify-center">
          <button onclick="confirmBalance(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">CONFIRM BALANCE</button>
        </div>
      `;
    }
  }

  if (currentPage === 'Delivery Initiation') {
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="initiateDelivery(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">INITIATE DELIVERY</button>
      </div>
    `;
  }

  if (currentPage === 'In Transit' || currentPage === 'Delivery Confirmation') {
    return `
      <div class="flex gap-2 justify-center">
        <button onclick="confirmDelivery(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">CONFIRM DELIVERY</button>
      </div>
    `;
  }

  if (currentPage === 'Concluded') {
    if (deal?.expiredUnpaid && deal?.allowMarketplaceListing && !deal?.marketplaceListed) {
      return `
        <div class="flex gap-2 justify-center">
          <button onclick="listDealOnMarketplace(${id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">LIST ON MARKETPLACE</button>
        </div>
      `;
    }
  }

  const detailsHref = `/dashboard/deal/${id}`;
  return `
    <div class="flex gap-2 justify-center">
      <a href="${detailsHref}" class="text-[9px] font-black underline hover:text-gray-500">VIEW</a>
      <button onclick="deleteDeal(${id})" class="text-[9px] font-black underline hover:text-red-600">DELETE</button>
    </div>
  `;
}

function renderTable(data) {
  const tbody = document.getElementById('table-body');
  if (!tbody) return;

  if (!data || data.length === 0) {
    const label = currentPage === 'Products' ? 'No marketplace items' : (currentPage === 'Orders' ? 'No orders yet' : 'No deals in this bucket');
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">${label}</td></tr>`;
    return;
  }

  tbody.innerHTML = data.map((item, idx) => {
    const isOrder = currentPage === 'Orders';
    const isProduct = currentPage === 'Products';
    const id = item?.id != null ? `${isOrder ? 'MO' : (isProduct ? 'MP' : 'DL')}-${item.id}` : `${isOrder ? 'MO' : (isProduct ? 'MP' : 'DL')}-${idx + 1}`;
    const name = isOrder ? (item?.summaryName || item?.buyerName || 'Marketplace Order') : (isProduct ? (item?.name || 'Untitled Item') : (item?.title || 'Untitled Deal'));
    const price = naira(isOrder ? (item?.totalAmount || 0) : (isProduct ? (item?.price || 0) : (item?.value || 0)));
    const status = isOrder
      ? ((item?.status || 'PENDING_PAYMENT').toString().toUpperCase().replaceAll('_', ' '))
      : (isProduct ? (item?.listed ? 'LISTED' : 'UNLISTED') : ((item?.status || 'PENDING').toString().toUpperCase()));

    return `
      <tr class="border-b border-black hover:bg-gray-50 transition">
        <td class="p-4 border-r border-black font-bold">${idx + 1}</td>
        <td class="p-4 border-r border-black">${id}</td>
        <td class="p-4 border-r border-black truncate max-w-[250px]">${name}</td>
        <td class="p-4 border-r border-black">${price}</td>
        <td class="p-4 border-r border-black">
          <span class="px-2 py-0.5 text-[8px] font-black border border-black">${status}</span>
        </td>
        <td class="p-4 text-center">${actionCell(item)}</td>
      </tr>
    `;
  }).join('');
}

function render() {
  renderTable(filterDealsForPage());
}

async function loadMarketItems() {
  const tbody = document.getElementById('table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">Loading...</td></tr>`;
  }
  try {
    marketItemsCache = await fetchJsonList('/api/admin/marketplace/items');
    render();
  } catch (e) {
    if (e?.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-red-600 font-bold uppercase">${e?.message || 'Failed to load items.'}</td></tr>`;
    }
  }
}

async function loadCurrentPageData() {
  if (currentPage === 'Products') {
    await loadMarketItems();
    return;
  }
  if (currentPage === 'Orders') {
    await loadOrders();
    return;
  }
  await loadDeals();
}

async function loadOrders() {
  const tbody = document.getElementById('table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">Loading...</td></tr>`;
  }

  try {
    ordersCache = await fetchJsonList('/api/admin/marketplace/items/orders');
    render();
  } catch (e) {
    if (e?.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-red-600 font-bold uppercase">${e?.message || 'Failed to load orders.'}</td></tr>`;
    }
  }
}

async function loadDeals() {
  const tbody = document.getElementById('table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">Loading...</td></tr>`;
  }

  try {
    dealsCache = await fetchJsonList('/api/admin/deals');
    render();
  } catch (e) {
    if (e?.redirectToLogin) {
      window.location.href = '/login';
      return;
    }
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-red-600 font-bold uppercase">${e?.message || 'Failed to load deals.'}</td></tr>`;
    }
  }
}

function toggleNav(id) {
  document.getElementById(id)?.classList.toggle('hidden');
}

function openNewProductModal() {
  document.getElementById('new-product-modal')?.classList.remove('hidden');
  document.getElementById('new-product-modal')?.classList.add('flex');
}

function closeNewProductModal() {
  document.getElementById('new-product-modal')?.classList.add('hidden');
  document.getElementById('new-product-modal')?.classList.remove('flex');
}

async function submitNewProduct() {
  const name = document.getElementById('mp-name')?.value?.trim() || '';
  const price = document.getElementById('mp-price')?.value;
  const oldPrice = document.getElementById('mp-old-price')?.value;
  const description = document.getElementById('mp-description')?.value?.trim() || '';
  const size = document.getElementById('mp-size')?.value?.trim() || 'small';
  const listed = document.getElementById('mp-listed')?.checked ? 'true' : 'false';
  const photos = Array.from(document.getElementById('mp-photos')?.files || []).slice(0, 3);

  if (!name) {
    showToast('Name is required', 'error');
    return;
  }
  if (!price || Number(price) <= 0) {
    showToast('Valid price is required', 'error');
    return;
  }

  const fd = new FormData();
  fd.append('name', name);
  fd.append('price', String(price));
  if (oldPrice && Number(oldPrice) > 0) fd.append('oldPrice', String(oldPrice));
  if (description) fd.append('description', description);
  fd.append('size', size);
  fd.append('listed', listed);
  photos.forEach(file => fd.append('photos', file));

  const res = await fetch('/api/admin/marketplace/items', {
    method: 'POST',
    body: fd,
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (res.redirected || !contentType.includes('application/json')) {
    showToast('Session expired. Please log in again.', 'error');
    setTimeout(() => { window.location.href = '/login'; }, 700);
    return;
  }
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) {
    showToast(payload?.message || `Request failed (${res.status})`, 'error');
    return;
  }

  showToast('Marketplace item created', 'success');
  closeNewProductModal();
  await loadMarketItems();
}

// Initial Run
document.addEventListener('DOMContentLoaded', () => {
  switchPage('Pending Approval');
});
