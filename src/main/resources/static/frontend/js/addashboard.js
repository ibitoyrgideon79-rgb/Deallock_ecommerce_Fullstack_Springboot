function toggleNavDropdown(id) {
    const target = document.getElementById(id);
    const isHidden = target.classList.contains('hidden');

    // 1. Close all other dropdowns
    document.querySelectorAll('[id$="-drop"]').forEach(el => {
        el.classList.add('hidden');
        // Reset icons to plus
        const btn = el.previousElementSibling;
        if (btn) btn.querySelector('.fas').classList.replace('fa-minus', 'fa-plus');
    });

    // 2. Open clicked dropdown if it was hidden
    if (isHidden) {
        target.classList.remove('hidden');
        target.previousElementSibling.querySelector('.fas').classList.replace('fa-plus', 'fa-minus');
    }
}
// DUMMY DATA FOR EACH VIEW
const DATA_STORE = {
    Orders: [
        { sn: 1, id: "ORD-901", name: "Steel Flask 900ml", price: 11920, status: "active", date: "2023-11-01" },
        { sn: 2, id: "ORD-902", name: "LED Smart Cup", price: 6690, status: "completed", date: "2023-11-05" }
    ],
    Products: [
        { sn: 1, id: "PRD-101", name: "Vacuum Tumbler", price: 8500, status: "active", date: "2023-10-20" },
        { sn: 2, id: "PRD-102", name: "Office Chair", price: 42000, status: "active", date: "2023-10-21" }
    ],
    Pending: [
        { sn: 1, id: "DEL-44", name: "Bluetooth Speaker", price: 12500, status: "active", date: "2023-11-12" }
    ],
    Concluded: [
        { sn: 1, id: "DEL-12", name: "HD Web Camera", price: 16800, status: "completed", date: "2023-11-10" }
    ]
};

let currentPage = 'Orders';
let currentFilter = 'all';

function switchPage(pageName) {
    currentPage = pageName;
    document.getElementById('page-title').innerText = `Marketplace: ${pageName}`;
    applyFilters();
}

function renderTable(data) {
    const tbody = document.getElementById('table-body');
    if (data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">No data found in this timeframe</td></tr>`;
        return;
    }
    
    tbody.innerHTML = data.map(item => `
        <tr class="border-b border-black hover:bg-gray-50 transition">
            <td class="p-4 border-r border-black font-bold">${item.sn}</td>
            <td class="p-4 border-r border-black">${item.id}</td>
            <td class="p-4 border-r border-black truncate max-w-[250px]">${item.name}</td>
            <td class="p-4 border-r border-black">₦${item.price.toLocaleString()}</td>
            <td class="p-4 border-r border-black">
                <span class="px-2 py-0.5 text-[8px] font-black border border-black ${item.status === 'active' ? 'bg-white text-black' : 'bg-black text-white'}">
                    ${item.status}
                </span>
            </td>
            <td class="p-4 text-center">
                <button onclick="viewDetails('${item.id}')" class="text-[9px] font-black underline hover:text-gray-500">VIEW MORE</button>
            </td>
        </tr>
    `).join('');
}

function applyFilters() {
    let filtered = [...DATA_STORE[currentPage]];
    
    // Status Logic
    if (currentFilter !== 'all') {
        filtered = filtered.filter(i => i.status === currentFilter);
    }
    
    // Date Logic
    const from = document.getElementById('date-from').value;
    const to = document.getElementById('date-to').value;
    if (from && to) {
        filtered = filtered.filter(i => i.date >= from && i.date <= to);
    }

    renderTable(filtered);
}

function filterStatus(status) {
    currentFilter = status;
    ['all', 'active', 'completed'].forEach(s => {
        const btn = document.getElementById(`btn-${s}`);
        btn.classList.toggle('bg-black', s === status);
        btn.classList.toggle('text-white', s === status);
    });
    applyFilters();
}

function toggleNav(id) {
    document.getElementById(id).classList.toggle('hidden');
}

// Initial Run
document.addEventListener('DOMContentLoaded', () => applyFilters());
