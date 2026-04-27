function showToast(message, type) {
  const t = document.createElement('div');
  t.className = 'fixed bottom-6 right-6 z-[9999] text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[360px]';
  t.style.background = type === 'error' ? '#dc2626' : '#059669';
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4000);
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

async function loadOrder() {
  const orderId = Number(window.__ORDER_ID__ || document.body?.dataset?.orderId || 0);
  if (!orderId) return;
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
  if (!ct.includes('application/json') || !res.ok) return;
  const order = await res.json();

  const badge = document.getElementById('status-badge');
  if (badge) {
    badge.textContent = readableStatus(order?.status);
    badge.className = `px-3 py-1 text-xs rounded-full ${badgeClass(order?.status)}`;
  }
  const proofLink = document.getElementById('view-proof-link');
  if (proofLink && order?.paymentProofUploaded) {
    proofLink.href = `/api/marketplace/orders/${orderId}/payment-proof`;
    proofLink.classList.remove('hidden');
  }
  const noteBox = document.getElementById('order-admin-note');
  if (noteBox && order?.adminNote) {
    noteBox.textContent = `Admin Note: ${order.adminNote}`;
    noteBox.classList.remove('hidden');
  }
}

async function submitProof(event) {
  event.preventDefault();
  const orderId = Number(window.__ORDER_ID__ || document.body?.dataset?.orderId || 0);
  if (!orderId) return;
  const file = document.getElementById('payment-proof-file')?.files?.[0];
  const note = (document.getElementById('payment-proof-note')?.value || '').trim();
  if (!file) {
    showToast('Please choose a proof file.', 'error');
    return;
  }

  const fd = new FormData();
  fd.append('paymentProof', file);
  if (note) fd.append('note', note);

  const btn = document.getElementById('upload-proof-btn');
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Uploading...';
  }
  try {
    const res = await fetch(`/api/marketplace/orders/${orderId}/payment-proof`, {
      method: 'POST',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      body: fd
    });
    if (res.status === 401 || res.status === 403) {
      throw new Error('Session expired. Please log in again.');
    }
    const ct = (res.headers.get('content-type') || '').toLowerCase();
    if (!ct.includes('application/json')) {
      throw new Error(`Upload failed (${res.status}).`);
    }
    const payload = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(payload?.message || `Upload failed (${res.status})`);
    }
    showToast(payload?.message || 'Payment proof uploaded successfully.', 'success');
    setTimeout(() => {
      window.location.href = `/dashboard/order/${orderId}/track`;
    }, 500);
  } catch (e) {
    showToast(e?.message || 'Upload failed.', 'error');
    if ((e?.message || '').toLowerCase().includes('session expired')) {
      setTimeout(() => { window.location.href = '/login'; }, 700);
    }
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Upload Proof Of Payment';
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('payment-proof-form')?.addEventListener('submit', submitProof);
  loadOrder();
});
