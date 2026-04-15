const products = [{ id: 1, name: "900ML Steel Flask", price: 11920, oldPrice: 14900, img: "https://picsum.photos" },
            { id: 2, name: "LED Coffee Mug", price: 6690, oldPrice: 13410, img: "https://picsum.photos" },
            { id: 3, name: "Vacuum Tumbler Pro", price: 8542, oldPrice: 20360, img: "https://picsum.photos" },
            { id: 4, name: "Electric Mixer", price: 4500, oldPrice: 9000, img: "https://picsum.photos" },
            { id: 5, name: "Wireless Speaker", price: 12500, oldPrice: 18900, img: "https://picsum.photos" },
            { id: 6, name: "Studio Headphones", price: 28500, oldPrice: 45000, img: "https://picsum.photos" },
            { id: 7, name: "Smart Fitness Watch", price: 18990, oldPrice: 29900, img: "https://picsum.photos" },
            { id: 8, name: "Desk Lamp White", price: 8950, oldPrice: 15900, img: "https://picsum.photos" },
            { id: 9, name: "Portable Cooler", price: 12400, oldPrice: 18500, img: "https://picsum.photos" },
            { id: 10, name: "Insulated Box", price: 6750, oldPrice: 12000, img: "https://picsum.photos" },
            { id: 11, name: "HD Web Camera", price: 16800, oldPrice: 25000, img: "https://picsum.photos" },
            { id: 12, name: "Hand Warmer USB", price: 4200, oldPrice: 7800, img: "https://picsum.photos" },
            { id: 13, name: "Travel Kettle", price: 9500, oldPrice: 14500, img: "https://picsum.photos" },
            { id: 14, name: "Power Bank Max", price: 12450, oldPrice: 19900, img: "https://picsum.photos" },
            { id: 15, name: "Ergo Office Chair", price: 42500, oldPrice: 68000, img: "https://picsum.photos" },
            { id: 16, name: "RGB Gaming Mouse", price: 6800, oldPrice: 12900, img: "https://picsum.photos" }
        ];

let cart = JSON.parse(localStorage.getItem('bw_cart')) || [];
let currentProduct = null;
const DOOR_DELIVERY_FEE = 2500;

function formatPrice(price) { return "₦" + price.toLocaleString('en-NG'); }

function renderProducts() {
    document.getElementById('product-grid').innerHTML = products.map(p => `
        <div onclick="showProductDetail(${p.id})" class="border border-black bg-white cursor-pointer group">
            <img src="${p.image}" class="w-full h-40 object-cover border-b border-black">
            <div class="p-3">
                <h3 class="text-[10px] font-black uppercase mb-2">${p.name}</h3>
                <div class="flex justify-between items-center">
                    <span class="text-sm font-black">${formatPrice(p.price)}</span>
                    <button onclick="addToCartDirect(${p.id}); event.stopPropagation()" class="border border-black px-2 py-1 text-[9px] font-black hover:bg-black hover:text-white">ADD</button>
                </div>
            </div>
        </div>
    `).join('');
}

function renderCart() {
    const container = document.getElementById('cart-items');
    if (cart.length === 0) {
        container.innerHTML = `<div class="py-10 text-center text-[10px] font-bold uppercase text-gray-400">Empty</div>`;
        return;
    }
    container.innerHTML = cart.map((item, index) => `
        <div class="flex gap-3 border-b border-gray-100 pb-3">
            <img src="${item.image}" class="w-10 h-10 border border-black">
            <div class="flex-1">
                <p class="text-[9px] font-black uppercase truncate">${item.name}</p>
                <p class="text-[10px] font-bold">${formatPrice(item.price)} x${item.quantity}</p>
            </div>
            <button onclick="removeFromCart(${index})" class="text-lg">&times;</button>
        </div>
    `).join('');
}

function handleDeliveryUI() {
    const method = document.querySelector('input[name="delivery"]:checked').value;
    document.getElementById('address-section').style.display = method === 'door' ? 'block' : 'none';
    saveAndUpdate();
}

function saveAndUpdate() {
    localStorage.setItem('bw_cart', JSON.stringify(cart));
    renderCart();
    
    const subtotal = cart.reduce((s, i) => s + (i.price * i.quantity), 0);
    const isDoor = document.querySelector('input[name="delivery"]:checked').value === 'door';
    const delivery = (isDoor && cart.length > 0) ? DOOR_DELIVERY_FEE : 0;

    document.getElementById('cart-subtotal').textContent = formatPrice(subtotal);
    document.getElementById('delivery-fee').textContent = formatPrice(delivery);
    document.getElementById('grand-total').textContent = formatPrice(subtotal + delivery);
    document.getElementById('mobile-cart-count').textContent = cart.reduce((s, i) => s + i.quantity, 0);
}

function addToCartDirect(id) {
    const p = products.find(i => i.id === id);
    const existing = cart.find(i => i.id === id);
    if (existing) existing.quantity++; else cart.push({...p, quantity: 1});
    saveAndUpdate();
}

function removeFromCart(index) { cart.splice(index, 1); saveAndUpdate(); }

function showProductDetail(id) {
    currentProduct = products.find(p => p.id === id);
    document.getElementById('modal-main-image').src = currentProduct.image;
    document.getElementById('modal-title').textContent = currentProduct.name;
    document.getElementById('modal-price').textContent = formatPrice(currentProduct.price);
    document.getElementById('modal-old-price').textContent = formatPrice(currentProduct.oldPrice);
    document.getElementById('product-modal').classList.replace('hidden', 'flex');
}

function closeProductModal() { document.getElementById('product-modal').classList.replace('flex', 'hidden'); }

function addCurrentProductToCart() { addToCartDirect(currentProduct.id); closeProductModal(); }

function toggleMobileCart() { document.getElementById('cart-sidebar').classList.toggle('translate-x-full'); }

function proceedToCheckout() {
    const method = document.querySelector('input[name="delivery"]:checked').value;
    const address = document.getElementById('address-input').value;
    if (method === 'door' && !address) return alert("ENTER ADDRESS");
    alert("ORDER PROCESSED");
}

document.addEventListener('DOMContentLoaded', () => {
    renderProducts();
    handleDeliveryUI();
});
