function showToast(message, type) {
  const t = document.createElement('div');
  t.style.position = 'fixed';
  t.style.bottom = '24px';
  t.style.right = '24px';
  t.style.zIndex = '9999';
  t.style.padding = '10px 14px';
  t.style.borderRadius = '12px';
  t.style.color = '#fff';
  t.style.fontSize = '13px';
  t.style.boxShadow = '0 6px 20px rgba(0,0,0,0.16)';
  t.style.background = type === 'error' ? '#dc2626' : '#059669';
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

function readableStatus(status) {
  return (status || '').toString().replaceAll('_', ' ');
}

function badgeClass(status) {
  switch ((status || '').toUpperCase()) {
    case 'PENDING_PAYMENT': return 'bg-yellow-100 text-yellow-800';
    case 'PAYMENT_SUBMITTED': return 'bg-blue-100 text-blue-800';
    case 'PAYMENT_NOT_RECEIVED': return 'bg-red-100 text-red-800';
    case 'PAYMENT_RECEIVED': return 'bg-indigo-100 text-indigo-800';
    case 'PROCESSING': return 'bg-purple-100 text-purple-800';
    case 'SHIPPED': return 'bg-cyan-100 text-cyan-800';
    case 'DELIVERED': return 'bg-emerald-100 text-emerald-800';
    case 'REVIEW': return 'bg-gray-200 text-gray-800';
    default: return 'bg-gray-100 text-gray-700';
  }
}

function done(order, key) {
  const status = (order?.status || '').toUpperCase();
  if (key === 'created') return true;
  if (key === 'submitted') return !!order?.paymentSubmittedAt || ['PAYMENT_SUBMITTED', 'PAYMENT_RECEIVED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'REVIEW'].includes(status);
  if (key === 'confirmed') return !!order?.paymentReceivedAt || ['PAYMENT_RECEIVED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'REVIEW'].includes(status);
  if (key === 'shipped') return !!order?.shippedAt || ['SHIPPED', 'DELIVERED', 'REVIEW'].includes(status);
  if (key === 'delivered') return !!order?.deliveredAt || ['DELIVERED', 'REVIEW'].includes(status);
  if (key === 'review') return status === 'REVIEW';
  return false;
}

function row(label, complete, at) {
  const icon = complete ? '&#10003;' : '&#9675;';
  const iconCls = complete ? 'bg-emerald-600 text-white' : 'bg-gray-200 text-gray-600';
  const timeText = at && at !== 'Pending' ? at : (complete ? 'Completed' : 'Pending');
  return `
    <div class="flex items-start gap-3 border border-gray-200 rounded-xl p-3 ${complete ? 'bg-emerald-50 border-emerald-200' : 'bg-white'}">
      <span class="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${iconCls}">${icon}</span>
      <div class="flex-1">
        <div class="text-sm font-semibold text-gray-900">${label}</div>
        <div class="text-xs text-gray-500">${timeText}</div>
      </div>
    </div>
  `;
}

async function loadTrack() {
  const orderId = Number(window.__ORDER_ID__ || document.body?.dataset?.orderId || 0);
  if (!orderId) {
    showToast('Order ID missing.', 'error');
    return;
  }

  const res = await fetch(`/api/marketplace/orders/${orderId}`, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (res.status === 401 || res.status === 403) {
    showToast('Session expired. Please log in again.', 'error');
    setTimeout(() => { window.location.href = '/login'; }, 700);
    return;
  }
  const ct = (res.headers.get('content-type') || '').toLowerCase();
  if (!ct.includes('application/json')) {
    showToast('Could not load tracking data.', 'error');
    return;
  }
  if (!res.ok) {
    showToast(`Failed to load track (${res.status}).`, 'error');
    return;
  }

  const order = await res.json();
  const badge = document.getElementById('status-badge');
  if (badge) {
    badge.textContent = readableStatus(order?.status);
    badge.className = `px-3 py-1 text-xs rounded-full ${badgeClass(order?.status)}`;
  }

  const lastStepLabel = (order?.deliveryMethod || '').toLowerCase() === 'pickup' ? 'Picked Up' : 'Delivered';
  const list = document.getElementById('track-list');
  if (!list) return;
  list.innerHTML = [
    row('Order Placed', done(order, 'created'), order?.createdAt || 'Pending'),
    row('Payment Proof Submitted', done(order, 'submitted'), order?.paymentSubmittedAt || 'Pending'),
    row('Payment Confirmed', done(order, 'confirmed'), order?.paymentReceivedAt || 'Pending'),
    row('Order Shipped', done(order, 'shipped'), order?.shippedAt || 'Pending'),
    row(lastStepLabel, done(order, 'delivered'), order?.deliveredAt || 'Pending'),
    row('Review', done(order, 'review'), done(order, 'review') ? 'Open for feedback' : 'Pending')
  ].join('');
}

document.addEventListener('DOMContentLoaded', loadTrack);
