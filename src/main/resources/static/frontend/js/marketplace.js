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

async function apiJson(url, options) {
  const res = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options?.headers || {})
    }
  });
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (res.redirected || !contentType.includes('application/json')) {
    const e = new Error('Please log in to continue.');
    e.redirectToLogin = true;
    throw e;
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data?.message || `Request failed (${res.status})`);
  }
  return data;
}

let products = [];
let cart = JSON.parse(localStorage.getItem('bw_cart')) || [];
let currentProduct = null;
const DOOR_DELIVERY_FEE = 2500;

function formatPrice(price) {
  const n = typeof price === 'number' ? price : Number(price || 0);
  return '\u20A6' + n.toLocaleString('en-NG');
}

function renderProducts() {
  const grid = document.getElementById('product-grid');
  if (!grid) return;

  if (!products || products.length === 0) {
    grid.innerHTML = `<div class="col-span-full p-10 text-center text-[10px] font-bold uppercase text-gray-400">No items yet</div>`;
    return;
  }

  grid.innerHTML = products.map(p => `
    <div onclick="showProductDetail(${p.id})" class="flex flex-col border border-black bg-white cursor-pointer group h-full">
      <div class="h-48 w-full overflow-hidden border-b border-black flex-shrink-0 flex items-center justify-center bg-gray-50">
        <img src="${p.image}" class="max-w-full max-h-full object-contain p-2" alt="${escapeHtml(p.name)}">
      </div>
      <div class="p-3 flex flex-col justify-between flex-grow">
        <h3 class="text-[10px] font-black uppercase mb-2 line-clamp-2">${escapeHtml(p.name)}</h3>
        <div class="flex justify-between items-center mt-auto">
          <span class="text-sm font-black">${formatPrice(p.price)}</span>
          <button onclick="addToCartDirect(${p.id}); event.stopPropagation()" class="border border-black px-2 py-1 text-[9px] font-black hover:bg-black hover:text-white">ADD</button>
        </div>
      </div>
    </div>
  `).join('');
}

async function loadProducts() {
  try {
    const rows = await apiJson('/api/marketplace/items');
    products = (Array.isArray(rows) ? rows : []).map(r => ({
      id: r.id,
      name: r.name || 'Item',
      price: Number(r.price || 0),
      oldPrice: r.oldPrice != null ? Number(r.oldPrice) : null,
      images: Array.isArray(r.imageUrls) && r.imageUrls.length > 0
        ? r.imageUrls
        : (r.imageUrl ? [r.imageUrl] : []),
      image: (Array.isArray(r.imageUrls) && r.imageUrls.length > 0)
        ? r.imageUrls[0]
        : (r.imageUrl || '/frontend/images/logo.jpeg'),
      descriptionHTML: (r.description || '').toString().trim()
    }));
  } catch (e) {
    products = [];
    showToast(e?.message || 'Failed to load marketplace items', 'error');
  }
  renderProducts();
}

function renderCart() {
  const container = document.getElementById('cart-items');
  if (!container) return;
  if (cart.length === 0) {
    container.innerHTML = `<div class="py-10 text-center text-[10px] font-bold uppercase text-gray-400">Empty</div>`;
    return;
  }

  container.innerHTML = cart.map((item, index) => `
    <div class="flex gap-3 border-b border-gray-100 pb-3">
      <img src="${item.image}" class="w-10 h-10 object-contain bg-gray-50 border border-black" alt="${escapeHtml(item.name)}">
      <div class="flex-1">
        <p class="text-[9px] font-black uppercase truncate">${escapeHtml(item.name)}</p>
        <p class="text-[10px] font-bold">${formatPrice(item.price)} x${item.quantity}</p>
      </div>
      <button onclick="removeFromCart(${index})" class="text-lg" aria-label="Remove">&times;</button>
    </div>
  `).join('');
}

function handleDeliveryUI() {
  const checked = document.querySelector('input[name="delivery"]:checked');
  const method = checked ? checked.value : 'pickup';
  const addr = document.getElementById('address-section');
  if (addr) addr.style.display = method === 'door' ? 'block' : 'none';
  saveAndUpdate();
}

function saveAndUpdate() {
  localStorage.setItem('bw_cart', JSON.stringify(cart));
  renderCart();
  const subtotal = cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const checked = document.querySelector('input[name="delivery"]:checked');
  const delivery = checked?.value === 'door' && cart.length > 0 ? DOOR_DELIVERY_FEE : 0;
  document.getElementById('cart-subtotal').textContent = formatPrice(subtotal);
  document.getElementById('delivery-fee').textContent = formatPrice(delivery);
  document.getElementById('grand-total').textContent = formatPrice(subtotal + delivery);
  document.getElementById('mobile-cart-count').textContent = String(cart.reduce((sum, i) => sum + i.quantity, 0));
}

function addToCartDirect(id) {
  const p = products.find(i => i.id === id);
  if (!p) return;
  const existing = cart.find(i => i.id === id);
  if (existing) existing.quantity++;
  else cart.push({ ...p, quantity: 1 });
  saveAndUpdate();
  showToast('Added to cart', 'success');
}

function removeFromCart(index) {
  cart.splice(index, 1);
  saveAndUpdate();
}

function showProductDetail(id) {
  currentProduct = products.find(p => p.id === id);
  if (!currentProduct) return;

  const img = document.getElementById('modal-main-image');
  const title = document.getElementById('modal-title');
  const price = document.getElementById('modal-price');
  const oldPrice = document.getElementById('modal-old-price');
  const modal = document.getElementById('product-modal');
  const description = document.getElementById('modal-description');
  const thumbContainer = document.getElementById('modal-thumbnails');

  if (img) img.src = currentProduct.image;
  if (title) title.textContent = currentProduct.name;
  if (price) price.textContent = formatPrice(currentProduct.price);
  if (oldPrice) {
    oldPrice.textContent = currentProduct.oldPrice != null ? formatPrice(currentProduct.oldPrice) : '';
    oldPrice.style.display = currentProduct.oldPrice != null ? 'inline' : 'none';
  }
  if (description) {
    description.innerHTML = currentProduct.descriptionHTML || '<p>No details available.</p>';
  }
  if (thumbContainer) {
    thumbContainer.innerHTML = '';
    (currentProduct.images || []).slice(0, 3).forEach(src => {
      const thumb = document.createElement('img');
      thumb.src = src;
      thumb.className = 'w-20 h-20 object-cover border border-black cursor-pointer hover:opacity-70 transition-opacity';
      thumb.onclick = () => { if (img) img.src = src; };
      thumbContainer.appendChild(thumb);
    });
  }

  if (modal) {
    modal.classList.replace('hidden', 'flex');
    document.body.style.overflow = 'hidden';
  }
}

function closeProductModal() {
  const modal = document.getElementById('product-modal');
  if (modal) {
    modal.classList.replace('flex', 'hidden');
    document.body.style.overflow = '';
  }
}

function addCurrentProductToCart() {
  if (!currentProduct) return;
  addToCartDirect(currentProduct.id);
  closeProductModal();
}

function toggleMobileCart() {
  document.getElementById('cart-sidebar')?.classList.toggle('translate-x-full');
}

function proceedToCheckout() {
  const checked = document.querySelector('input[name="delivery"]:checked');
  const deliveryMethod = checked ? checked.value : 'pickup';
  const deliveryAddress = (document.getElementById('address-input')?.value || '').trim();
  const paymentMethod = (document.getElementById('payment-method')?.value || 'BANK_TRANSFER').trim();

  if (cart.length === 0) {
    showToast('Your cart is empty.', 'error');
    return;
  }
  if (deliveryMethod === 'door' && !deliveryAddress) {
    showToast('Enter delivery address.', 'error');
    return;
  }

  const payload = {
    deliveryMethod,
    deliveryAddress: deliveryMethod === 'door' ? deliveryAddress : '',
    paymentMethod,
    items: cart.map(i => ({ id: i.id, quantity: i.quantity }))
  };

  apiJson('/api/marketplace/checkout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
    .then(data => {
      const orderId = Number(data?.order?.id || 0);
      cart = [];
      localStorage.setItem('bw_cart', JSON.stringify(cart));
      saveAndUpdate();
      const addrInput = document.getElementById('address-input');
      if (addrInput) addrInput.value = '';
      if (orderId > 0) {
        window.location.href = `/dashboard/order/${orderId}/pay?created=1`;
      } else {
        window.location.href = '/dashboard?tab=orders';
      }
    })
    .catch(e => {
      showToast(e?.message || 'Checkout failed.', 'error');
      if (e?.redirectToLogin || (e?.message || '').toLowerCase().includes('log in')) {
        setTimeout(() => { window.location.href = '/login'; }, 700);
      }
    });
}

document.addEventListener('DOMContentLoaded', () => {
  loadProducts();
  handleDeliveryUI();
});
