function showToast(message, type) {
  const t = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  t.className = `fixed bottom-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px]`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
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

  grid.innerHTML = products
    .map(
      p => `
        <div onclick="showProductDetail(${p.id})" class="flex flex-col border border-black bg-white cursor-pointer group h-full">
          <!-- Wrapper ensures the height is strictly enforced -->
          <div class="h-48 w-full overflow-hidden border-b border-black flex-shrink-0">
            <img src="${p.image}" class="w-full h-full object-cover" alt="${p.name}">
          </div>
          
          <div class="p-3 flex flex-col justify-between flex-grow">
            <h3 class="text-[10px] font-black uppercase mb-2 line-clamp-2">${p.name}</h3>
            <div class="flex justify-between items-center mt-auto">
              <span class="text-sm font-black">${formatPrice(p.price)}</span>
              <button onclick="addToCartDirect(${p.id}); event.stopPropagation()" class="border border-black px-2 py-1 text-[9px] font-black hover:bg-black hover:text-white">ADD</button>
            </div>
          </div>
        </div>
      `
    )
    .join('');

}

function imageHeightClass(size) {
  const v = (size || '').toString().toLowerCase();
  if (v === 'big' || v === 'large') return 'h-48';
  if (v === 'medium') return 'h-44';
  return 'h-40';
}

async function loadProducts() {
  try {
    const res = await fetch('/api/marketplace/items', { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`Failed to load (${res.status})`);
    const rows = await res.json();
    products = (Array.isArray(rows) ? rows : []).map(r => ({
      id: r.id,
      name: r.name || 'Item',
      price: Number(r.price || 0),
      oldPrice: r.oldPrice != null ? Number(r.oldPrice) : null,
      size: r.size || 'small',
      image: r.imageUrl || '/frontend/images/logo.jpeg'
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

  container.innerHTML = cart
    .map(
      (item, index) => `
        <div class="flex gap-3 border-b border-gray-100 pb-3">
          <img src="${item.image}" class="w-10 h-10 border border-black" alt="${item.name}">
          <div class="flex-1">
            <p class="text-[9px] font-black uppercase truncate">${item.name}</p>
            <p class="text-[10px] font-bold">${formatPrice(item.price)} x${item.quantity}</p>
          </div>
          <button onclick="removeFromCart(${index})" class="text-lg" aria-label="Remove">&times;</button>
        </div>
      `
    )
    .join('');
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

  const subtotal = cart.reduce((s, i) => s + i.price * i.quantity, 0);
  const checked = document.querySelector('input[name="delivery"]:checked');
  const isDoor = checked ? checked.value === 'door' : false;
  const delivery = isDoor && cart.length > 0 ? DOOR_DELIVERY_FEE : 0;

  const subEl = document.getElementById('cart-subtotal');
  const delEl = document.getElementById('delivery-fee');
  const totalEl = document.getElementById('grand-total');
  const countEl = document.getElementById('mobile-cart-count');

  if (subEl) subEl.textContent = formatPrice(subtotal);
  if (delEl) delEl.textContent = formatPrice(delivery);
  if (totalEl) totalEl.textContent = formatPrice(subtotal + delivery);
  if (countEl) countEl.textContent = String(cart.reduce((s, i) => s + i.quantity, 0));
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
  
  // NEW: Get the description container and thumbnail wrapper
  const description = document.getElementById('modal-description');
  const thumbContainer = document.getElementById('modal-thumbnails');

  // Set basic details
  if (img) img.src = currentProduct.image;
  if (title) title.textContent = currentProduct.name;
  if (price) price.textContent = formatPrice(currentProduct.price);
  
  // Handle old price visibility
  if (oldPrice) {
    oldPrice.textContent = currentProduct.oldPrice != null ? formatPrice(currentProduct.oldPrice) : '';
    oldPrice.style.display = currentProduct.oldPrice != null ? 'inline' : 'none';
  }

  // 1. FETCHED DESCRIPTION: Use innerHTML to render fetched admin content
  if (description) {
    description.innerHTML = currentProduct.descriptionHTML || '<p>No details available.</p>';
  }

  // 2. THUMBNAILS: Generate 3 clickable images
  if (thumbContainer && currentProduct.images) {
    thumbContainer.innerHTML = ''; // Clear old thumbs
    // Assuming currentProduct.images is an array of 3+ strings
    currentProduct.images.slice(0, 3).forEach((src) => {
      const thumb = document.createElement('img');
      thumb.src = src;
      thumb.className = "w-20 h-20 object-cover border border-black cursor-pointer hover:opacity-70 transition-opacity";
      // Update main image on click
      thumb.onclick = () => { if (img) img.src = src; };
      thumbContainer.appendChild(thumb);
    });
  }

  if (modal) {
    modal.classList.replace('hidden', 'flex');
    document.body.style.overflow = 'hidden'; // 3. MOBILE: Disable background scroll
  }
}

function closeProductModal() {
  const modal = document.getElementById('product-modal');
  if (modal) {
    modal.classList.replace('flex', 'hidden');
    document.body.style.overflow = ''; // Restore scroll
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
  const method = checked ? checked.value : 'pickup';
  const address = (document.getElementById('address-input')?.value || '').trim();

  if (cart.length === 0) {
    showToast('Your cart is empty.', 'error');
    return;
  }
  if (method === 'door' && !address) {
    showToast('Enter delivery address.', 'error');
    return;
  }

  // Marketplace checkout is not wired to backend yet.
  showToast('Checkout captured (demo). Backend integration next.', 'success');
}

document.addEventListener('DOMContentLoaded', () => {
  loadProducts();
  handleDeliveryUI();
});
