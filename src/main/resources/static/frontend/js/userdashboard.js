 // Toggle Sidebar on Mobile
  function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('hidden');
  }

  // Tab Switching
  function showTab(tab) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.getElementById(tab + '-tab').classList.add('active');

    document.querySelectorAll('.tab-link').forEach(link => link.classList.remove('active', 'bg-gray-100'));
    event.currentTarget.classList.add('active', 'bg-gray-100');
  }

  // Filter Deals
  function filterDeals(type) {
    document.querySelectorAll('.filter-btn').forEach(btn => btn.classList.remove('active'));
    event.currentTarget.classList.add('active');
  }

  // Dummy Data
  const dealsData = [
    { sn: 1, id: "DL-1001", name: "900ML Steel Flask", price: 11920, status: "active" },
    { sn: 2, id: "DL-1002", name: "Vacuum Coffee Tumbler", price: 8542, status: "active" },
    { sn: 3, id: "DL-1003", name: "Wireless Noise Cancelling Headphones", price: 28500, status: "completed" },
    { sn: 4, id: "DL-1004", name: "Smart Fitness Watch", price: 18990, status: "active" },
    { sn: 5, id: "DL-1005", name: "LED Desk Lamp", price: 8950, status: "completed" }
  ];

  const ordersData = [
    { sn: 1, id: "ORD-9001", name: "Portable Bluetooth Speaker", price: 12500, status: "completed" },
    { sn: 2, id: "ORD-9002", name: "USB C Power Bank 20000mAh", price: 12450, status: "completed" }
  ];

  function renderDealsTable() {
    const tbody = document.getElementById('deals-table-body');
    tbody.innerHTML = '';

    dealsData.forEach(deal => {
      const statusClass = deal.status === 'active' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-700';
      const row = `
        <tr class="hover:bg-gray-50">
          <td class="p-5">${deal.sn}</td>
          <td class="p-5 font-medium">${deal.id}</td>
          <td class="p-5">${deal.name}</td>
          <td class="p-5 font-medium">₦ ${deal.price.toLocaleString()}</td>
          <td class="p-5">
            <span class="px-4 py-1 text-xs font-medium rounded-full ${statusClass}">${deal.status.toUpperCase()}</span>
          </td>
          <td class="p-5">
            <button onclick="viewDealDetails('${deal.id}')" class="text-blue-600 hover:underline font-medium">View Details →</button>
          </td>
        </tr>
      `;
      tbody.innerHTML += row;
    });
  }

  function renderOrdersTable() {
    const tbody = document.getElementById('orders-table-body');
    tbody.innerHTML = '';

    ordersData.forEach(order => {
      const row = `
        <tr class="hover:bg-gray-50">
          <td class="p-5">${order.sn}</td>
          <td class="p-5 font-medium">${order.id}</td>
          <td class="p-5">${order.name}</td>
          <td class="p-5 font-medium">₦ ${order.price.toLocaleString()}</td>
          <td class="p-5">
            <span class="px-4 py-1 text-xs font-medium rounded-full bg-gray-100 text-gray-700">COMPLETED</span>
          </td>
          <td class="p-5">
            <button onclick="viewOrderDetails('${order.id}')" class="text-blue-600 hover:underline font-medium">View Details →</button>
          </td>
        </tr>
      `;
      tbody.innerHTML += row;
    });
  }

  function viewDealDetails(id) {
    alert(`Viewing full details for Deal ID: ${id}\n\nThis will be expanded into a detailed modal later.`);
  }

  function viewOrderDetails(id) {
    alert(`Viewing Order ID: ${id}`);
  }

  // New Deal Modal Functions
  function openNewDealModal() {
    document.getElementById('new-deal-modal').classList.remove('hidden');
    document.getElementById('new-deal-modal').classList.add('flex');
    calculatePaymentPlan();
  }

  function closeNewDealModal() {
    document.getElementById('new-deal-modal').classList.add('hidden');
    document.getElementById('new-deal-modal').classList.remove('flex');
  }

  function calculatePaymentPlan() {
    const value = parseFloat(document.getElementById('expected-value').value) || 0;
    const weeks = parseInt(document.getElementById('weeks').value) || 0;

    const holdingFee = value * 0.05 * weeks;
    const vat = holdingFee * 0.13;
    const logistics = 1950;
    const total = value + holdingFee + vat + logistics;
    const upfront = (value * 0.5) + logistics;

    document.getElementById('plan-item-value').textContent = `₦ ${value.toLocaleString()}`;
    document.getElementById('plan-holding').textContent = `₦ ${(holdingFee + vat).toLocaleString()}`;
    document.getElementById('plan-total').textContent = `₦ ${total.toLocaleString()}`;
    document.getElementById('plan-upfront').textContent = `₦ ${upfront.toLocaleString()}`;
  }

  function submitNewDeal() {
    const itemName = document.getElementById('item-name').value.trim();
    const value = document.getElementById('expected-value').value;
    const weeks = document.getElementById('weeks').value;

    let hasError = false;

    document.querySelectorAll('.error-text').forEach(el => el.textContent = '');

    if (!itemName) {
      document.getElementById('error-item-name').textContent = "Item Name is required";
      hasError = true;
    }
    if (!value || value <= 0) {
      document.getElementById('error-value').textContent = "Valid Expected Value is required";
      hasError = true;
    }
    if (!weeks || weeks <= 0) {
      document.getElementById('error-weeks').textContent = "Number of weeks is required";
      hasError = true;
    }

    if (hasError) return;

    alert("Deal submitted successfully!");
    closeNewDealModal();
  }

  function changeProfilePicture() {
    alert("Profile picture upload triggered");
  }

  function addNewDeliveryAddress() {
    const addr = prompt("Enter new delivery address:");
    if (addr) alert("New address added: " + addr);
  }

  function openChangePasswordModal() {
    alert("Change Password modal would open here");
  }

  function saveProfileChanges() {
    alert("Profile changes saved successfully!");
  }

  function openDateFilter() {
    alert("Date Range Filter");
  }

  // Initialize
  document.addEventListener('DOMContentLoaded', () => {
    renderDealsTable();
    renderOrdersTable();
    showTab('deals');
  });