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

let currentPage = 'Pending Approval';
let dealsCache = [];

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
  return !!deal?.deliveryConfirmedAt || isRejected(deal);
}

function switchPage(pageName) {
  currentPage = pageName;
  const title = document.getElementById('page-title');
  if (title) {
    title.innerText = `Deal Flow: ${pageName}`;
  }
  render();
}

function filterDealsForPage() {
  const rows = Array.isArray(dealsCache) ? dealsCache : [];

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
  const res = await fetch(path, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': body ? 'application/json' : undefined
    },
    body: body ? JSON.stringify(body) : undefined,
    credentials: 'same-origin'
  });
  const payload = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(payload?.message || `Request failed (${res.status})`);
  }
  return payload;
}

async function approveDeal(id) {
  if (!confirm('Approve this deal?')) return;
  await apiPost(`/api/admin/deals/${id}/approve`);
  showToast('Deal approved', 'success');
  await loadDeals();
}

async function rejectDeal(id) {
  const reason = prompt('Reason for rejection (optional):') || '';
  if (!confirm('Reject this deal?')) return;
  await apiPost(`/api/admin/deals/${id}/reject`, { reason });
  showToast('Deal rejected', 'success');
  await loadDeals();
}

async function confirmPayment(id) {
  if (!confirm('Mark payment as confirmed?')) return;
  await apiPost(`/api/admin/deals/${id}/payment-confirmed`);
  showToast('Payment confirmed', 'success');
  await loadDeals();
}

async function markSecured(id) {
  if (!confirm('Mark deal as secured?')) return;
  await apiPost(`/api/admin/deals/${id}/secured`);
  showToast('Deal secured', 'success');
  await loadDeals();
}

async function confirmBalance(id) {
  if (!confirm('Mark balance as confirmed?')) return;
  await apiPost(`/api/admin/deals/${id}/balance-confirmed`);
  showToast('Balance confirmed', 'success');
  await loadDeals();
}

async function initiateDelivery(id) {
  if (!confirm('Initiate delivery?')) return;
  await apiPost(`/api/admin/deals/${id}/delivery-initiated`);
  showToast('Delivery initiated', 'success');
  await loadDeals();
}

async function confirmDelivery(id) {
  if (!confirm('Confirm delivery?')) return;
  await apiPost(`/api/admin/deals/${id}/delivery-confirmed`);
  showToast('Delivery confirmed', 'success');
  await loadDeals();
}

async function deleteDeal(id) {
  if (!confirm('Delete this deal?')) return;
  await apiPost(`/api/admin/deals/${id}/delete`);
  showToast('Deal deleted', 'success');
  await loadDeals();
}

function actionCell(deal) {
  const id = deal?.id;
  if (!id) return '';

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
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">No deals in this bucket</td></tr>`;
    return;
  }

  tbody.innerHTML = data.map((item, idx) => {
    const id = item?.id != null ? `DL-${item.id}` : `DL-${idx + 1}`;
    const name = item?.title || 'Untitled Deal';
    const price = naira(item?.value || 0);
    const status = (item?.status || 'PENDING').toString().toUpperCase();

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

async function loadDeals() {
  const tbody = document.getElementById('table-body');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">Loading...</td></tr>`;
  }

  const res = await fetch('/api/admin/deals', { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' });
  if (res.status === 401) {
    window.location.href = '/login';
    return;
  }
  if (!res.ok) {
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-red-600 font-bold uppercase">Failed to load (${res.status})</td></tr>`;
    }
    return;
  }

  dealsCache = await res.json();
  render();
}

function toggleNav(id) {
  document.getElementById(id)?.classList.toggle('hidden');
}

// Initial Run
document.addEventListener('DOMContentLoaded', () => {
  switchPage('Pending Approval');
  loadDeals().catch(e => showToast(e.message || 'Failed to load', 'error'));
});
