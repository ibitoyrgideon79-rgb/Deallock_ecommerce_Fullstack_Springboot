function toggleNavDropdown(id) {
    const target = document.getElementById(id);
    const isHidden = target.classList.contains('hidden');

    // 1. Close all other dropdowns
    document.querySelectorAll('[id$="-drop"]').forEach(el => {
        el.classList.add('hidden');
        // Reset icons to plus
        const btn = el.previousElementSibling;
        if (btn) btn.querySelector('.fas')?.classList.replace('fa-minus', 'fa-plus');
    });

    // 2. Open clicked dropdown if it was hidden
    if (isHidden) {
        target.classList.remove('hidden');
        target.previousElementSibling.querySelector('.fas')?.classList.replace('fa-plus', 'fa-minus');
    }
}

let currentPage = 'Pending';
let currentFilter = 'all';
let dealsCache = [];

function naira(amount) {
    const n = typeof amount === 'number' ? amount : Number(amount || 0);
    return `\u20A6${n.toLocaleString()}`;
}

function dealUiStage(deal) {
    const status = (deal?.status || '').toString().toLowerCase();
    if (deal?.deliveryConfirmedAt) return 'completed';
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

function switchPage(pageName) {
    currentPage = pageName;
    const title = document.getElementById('page-title');
    if (title) {
        const prefix = (pageName === 'Pending' || pageName === 'Concluded') ? 'Deal Flow' : 'Marketplace';
        title.innerText = `${prefix}: ${pageName}`;
    }
    applyFilters();
}

function getRowsForPage() {
    if (currentPage === 'Pending') {
        return dealsCache.filter(d => dealUiStage(d) === 'active');
    }
    if (currentPage === 'Concluded') {
        return dealsCache.filter(d => dealUiStage(d) === 'completed');
    }

    // Marketplace sections are not wired yet.
    return [];
}

async function approveDeal(id) {
    if (!confirm('Approve this deal?')) return;
    const res = await fetch(`/api/admin/deals/${id}/approve`, {
        method: 'POST',
        headers: { 'Accept': 'application/json' }
    });
    if (!res.ok) {
        alert(`Failed to approve (${res.status})`);
        return;
    }
    await loadDeals();
}

async function rejectDeal(id) {
    const reason = prompt('Reason for rejection (optional):') || '';
    if (!confirm('Reject this deal?')) return;

    const res = await fetch(`/api/admin/deals/${id}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ reason })
    });
    if (!res.ok) {
        alert(`Failed to reject (${res.status})`);
        return;
    }
    await loadDeals();
}

function renderTable(data) {
    const tbody = document.getElementById('table-body');
    if (!tbody) return;

    if (!data || data.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">No data found</td></tr>`;
        return;
    }

    tbody.innerHTML = data.map((item, idx) => {
        const stage = dealUiStage(item);
        const status = dealStatusLabel(item);
        const statusClass = stage === 'active' ? 'bg-white text-black' : 'bg-black text-white';
        const id = item?.id != null ? `DL-${item.id}` : `DL-${idx + 1}`;
        const name = item?.title || 'Untitled Deal';
        const price = naira(item?.value || 0);
        const detailsHref = item?.id != null ? `/dashboard/deal/${item.id}` : '#';

        let actions = `<a href="${detailsHref}" class="text-[9px] font-black underline hover:text-gray-500">VIEW MORE</a>`;
        if (status === 'PENDING' && item?.id != null) {
            actions = `
              <div class="flex gap-2 justify-center">
                <button onclick="approveDeal(${item.id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-black hover:text-white">APPROVE</button>
                <button onclick="rejectDeal(${item.id})" class="px-3 py-1 text-[9px] font-black border border-black hover:bg-gray-100">REJECT</button>
              </div>
            `;
        }

        return `
        <tr class="border-b border-black hover:bg-gray-50 transition">
            <td class="p-4 border-r border-black font-bold">${idx + 1}</td>
            <td class="p-4 border-r border-black">${id}</td>
            <td class="p-4 border-r border-black truncate max-w-[250px]">${name}</td>
            <td class="p-4 border-r border-black">${price}</td>
            <td class="p-4 border-r border-black">
                <span class="px-2 py-0.5 text-[8px] font-black border border-black ${statusClass}">
                    ${status}
                </span>
            </td>
            <td class="p-4 text-center">${actions}</td>
        </tr>
        `;
    }).join('');
}

function applyFilters() {
    let filtered = getRowsForPage();

    // Status Logic
    if (currentFilter !== 'all') {
        filtered = filtered.filter(i => dealUiStage(i) === currentFilter);
    }

    // Date Logic (createdAt from API)
    const from = document.getElementById('date-from')?.value;
    const to = document.getElementById('date-to')?.value;
    if (from && to) {
        const fromDate = new Date(from + 'T00:00:00Z');
        const toDate = new Date(to + 'T23:59:59Z');
        filtered = filtered.filter(i => {
            const d = i?.createdAt ? new Date(i.createdAt) : null;
            return d && d >= fromDate && d <= toDate;
        });
    }

    renderTable(filtered);
}

function filterStatus(status) {
    currentFilter = status;
    ['all', 'active', 'completed'].forEach(s => {
        const btn = document.getElementById(`btn-${s}`);
        if (!btn) return;
        btn.classList.toggle('bg-black', s === status);
        btn.classList.toggle('text-white', s === status);
    });
    applyFilters();
}

function toggleNav(id) {
    document.getElementById(id)?.classList.toggle('hidden');
}

async function loadDeals() {
    const tbody = document.getElementById('table-body');
    if (tbody) {
        tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-gray-400 font-bold uppercase">Loading...</td></tr>`;
    }

    const res = await fetch('/api/admin/deals', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) {
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="6" class="p-10 text-center text-[10px] text-red-600 font-bold uppercase">Failed to load (${res.status})</td></tr>`;
        }
        return;
    }

    dealsCache = await res.json();
    applyFilters();
}

// Initial Run
document.addEventListener('DOMContentLoaded', () => {
    switchPage('Pending');
    loadDeals().catch(() => applyFilters());
});
