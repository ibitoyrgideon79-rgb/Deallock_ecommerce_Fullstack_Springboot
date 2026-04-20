function showToast(message, type) {
  const t = document.createElement('div');
  const tone = type === 'error' ? 'bg-red-600' : 'bg-emerald-600';
  t.className = `fixed bottom-6 right-6 z-[9999] ${tone} text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px]`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

const products = [
  { id: 1, name: '900ML Steel Flask', price: 11920, oldPrice: 14900, image: 'https://picsum.photos/seed/deallock-1/600/400' },
  { id: 2, name: 'LED Coffee Mug', price: 6690, oldPrice: 13410, image: 'https://picsum.photos/seed/deallock-2/600/400' },
  { id: 3, name: 'Vacuum Tumbler Pro', price: 8542, oldPrice: 20360, image: 'https://picsum.photos/seed/deallock-3/600/400' },
  { id: 4, name: 'Electric Mixer', price: 4500, oldPrice: 9000, image: 'https://picsum.photos/seed/deallock-4/600/400' },
  { id: 5, name: 'Wireless Speaker', price: 12500, oldPrice: 18900, image: 'https://picsum.photos/seed/deallock-5/600/400' },
  { id: 6, name: 'Studio Headphones', price: 28500, oldPrice: 45000, image: 'https://picsum.photos/seed/deallock-6/600/400' },
  { id: 7, name: 'Smart Fitness Watch', price: 18990, oldPrice: 29900, image: 'https://picsum.photos/seed/deallock-7/600/400' },
  { id: 8, name: 'Desk Lamp White', price: 8950, oldPrice: 15900, image: 'https://picsum.photos/seed/deallock-8/600/400' },
  { id: 9, name: 'Portable Cooler', price: 12400, oldPrice: 18500, image: 'https://picsum.photos/seed/deallock-9/600/400' },
  { id: 10, name: 'Insulated Box', price: 6750, oldPrice: 12000, image: 'https://picsum.photos/seed/deallock-10/600/400' },
  { id: 11, name: 'HD Web Camera', price: 16800, oldPrice: 25000, image: 'https://picsum.photos/seed/deallock-11/600/400' },
  { id: 12, name: 'Hand Warmer USB', price: 4200, oldPrice: 7800, image: 'https://picsum.photos/seed/deallock-12/600/400' },
  { id: 13, name: 'Travel Kettle', price: 9500, oldPrice: 14500, image: 'https://picsum.photos/seed/deallock-13/600/400' },
  { id: 14, name: 'Power Bank Max', price: 12450, oldPrice: 19900, image: 'https://picsum.photos/seed/deallock-14/600/400' },
  { id: 15, name: 'Ergo Office Chair', price: 42500, oldPrice: 68000, image: 'https://picsum.photos/seed/deallock-15/600/400' },
  { id: 16, name: 'RGB Gaming Mouse', price: 6800, oldPrice: 12900, image: 'https://picsum.photos/seed/deallock-16/600/400' }
];

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

  grid.innerHTML = products
    .map(
      p => `
        <div onclick="showProductDetail(${p.id})" class="border border-black bg-white cursor-pointer group">
          <img src="${p.image}" class="w-full h-40 object-cover border-b border-black" alt="${p.name}">
          <div class="p-3">
            <h3 class="text-[10px] font-black uppercase mb-2">${p.name}</h3>
            <div class="flex justify-between items-center">
              <span class="text-sm font-black">${formatPrice(p.price)}</span>
              <button onclick="addToCartDirect(${p.id}); event.stopPropagation()" class="border border-black px-2 py-1 text-[9px] font-black hover:bg-black hover:text-white">ADD</button>
            </div>
          </div>
        </div>
      `
    )
    .join('');
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

  if (img) img.src = currentProduct.image;
  if (title) title.textContent = currentProduct.name;
  if (price) price.textContent = formatPrice(currentProduct.price);
  if (oldPrice) oldPrice.textContent = formatPrice(currentProduct.oldPrice);
  if (modal) modal.classList.replace('hidden', 'flex');
}

function closeProductModal() {
  document.getElementById('product-modal')?.classList.replace('flex', 'hidden');
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
  renderProducts();
  handleDeliveryUI();
});
