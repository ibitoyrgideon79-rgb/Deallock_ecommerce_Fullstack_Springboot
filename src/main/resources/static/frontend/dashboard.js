

document.addEventListener('DOMContentLoaded', () => {
  const triggers = document.querySelectorAll('.user-trigger');
  triggers.forEach(trigger => {
    trigger.addEventListener('click', e => {
      if (window.innerWidth >= 992) return; 
      e.stopPropagation();
      const item = trigger.closest('.user-menu-item');
      const wasOpen = item.classList.contains('open');
      
      
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
      
      if (!wasOpen) item.classList.add('open');
    });
  });

 
  document.addEventListener('click', e => {
    if (window.innerWidth >= 992) return;
    if (!e.target.closest('.user-menu-item')) {
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
    }
  });
});




const dealsList = document.getElementById('deals-list');
const form = document.getElementById('new-deal-form');
const modal = document.getElementById('create-deal-modal');


function loadDeals() {
  const deals = JSON.parse(localStorage.getItem('userDeals') || '[]');
  dealsList.innerHTML = ''; 

  if (deals.length === 0) {
    dealsList.innerHTML = '<div class="no-deals">No active deals yet</div>';
    return;
  }

  deals.forEach(deal => {
    const card = document.createElement('div');
    card.className = `deal-card ${deal.status.toLowerCase().replace(' ', '-')}`;
    card.innerHTML = `
      <div class="deal-title">${deal.title || 'Untitled Deal'}</div>
      <div class="deal-status">${deal.status}</div>
      <div class="deal-value">₦${Number(deal.value || 0).toLocaleString()}</div>
    `;
    dealsList.appendChild(card);
  });
}


function saveDeal(data) {
  const deals = JSON.parse(localStorage.getItem('userDeals') || '[]');
  
  const newDeal = {
    id: Date.now(),
    title: data.title,
    client: data.client,
    value: data.value,
    description: data.description,
    status: 'Pending Approval',   
    createdAt: new Date().toISOString()
  };

  deals.push(newDeal);
  localStorage.setItem('userDeals', JSON.stringify(deals));
  loadDeals();
}


form.addEventListener('submit', e => {
  e.preventDefault();

  const formData = new FormData(form);
  const data = {
    title: formData.get('deal-title'),
    client: formData.get('client-name'),
    value: formData.get('deal-value'),
    description: formData.get('description')
  };

  saveDeal(data);
  modal.classList.remove('active');
  form.reset();

  alert('Deal created! It is now Pending Approval.');
});


document.getElementById('open-create-modal')?.addEventListener('click', () => {
  modal.classList.add('active');
});

document.getElementById('close-modal')?.addEventListener('click', () => {
  modal.classList.remove('active');
});

document.getElementById('cancel-create')?.addEventListener('click', () => {
  modal.classList.remove('active');
});

modal?.addEventListener('click', e => {
  if (e.target === modal) modal.classList.remove('active');
});


loadDeals();


const fileInput = document.getElementById('item-photo');
const uploadArea = document.getElementById('upload-area');
const previewContainer = document.getElementById('preview-container');
const previewImg = document.getElementById('preview-img');
const removeBtn = document.getElementById('remove-preview');

function showPreview(file) {
  if (!file.type.startsWith('image/')) {
    alert('Please select an image file');
    return;
  }

  const reader = new FileReader();
  reader.onload = e => {
    previewImg.src = e.target.result;
    previewContainer.style.display = 'block';
    uploadArea.style.display = 'none';
  };
  reader.readAsDataURL(file);
}

function clearPreview() {
  previewContainer.style.display = 'none';
  uploadArea.style.display = 'block';
  fileInput.value = '';
}


fileInput.addEventListener('change', e => {
  if (e.target.files[0]) {
    showPreview(e.target.files[0]);
  }
});


uploadArea.addEventListener('dragover', e => {
  e.preventDefault();
  uploadArea.classList.add('dragover');
});

uploadArea.addEventListener('dragleave', () => {
  uploadArea.classList.remove('dragover');
});

uploadArea.addEventListener('drop', e => {
  e.preventDefault();
  uploadArea.classList.remove('dragover');
  if (e.dataTransfer.files[0]) {
    fileInput.files = e.dataTransfer.files;
    showPreview(e.dataTransfer.files[0]);
  }
});


removeBtn.addEventListener('click', clearPreview);


const valueInput     = document.getElementById('deal-value');
const weeksSelect    = document.getElementById('weeks');
const customWeeks    = document.getElementById('custom-weeks');
const customGroup    = document.getElementById('custom-weeks-group');
const extraFeeRow    = document.getElementById('extra-fee-row');
const breakdown      = document.getElementById('breakdown');

const displayValue   = document.getElementById('display-value');
const displayService = document.getElementById('display-service-fee');
const displayExtra   = document.getElementById('display-extra-fee');
const displayVat     = document.getElementById('display-vat');
const displayTotal   = document.getElementById('display-total');

const upfrontEl      = document.getElementById('upfront-amount');
const weeklyCountEl  = document.getElementById('weekly-count');
const weeklyAmountEl = document.getElementById('weekly-amount');

function updatePaymentPreview() {
  const value = parseFloat(valueInput.value) || 0;
  if (value < 1000) {
    resetAllDisplays();
    return;
  }

  let weeks = parseInt(weeksSelect.value) || 0;
  let isCustom = weeksSelect.value === 'custom';
  let extraFeePercent = 0;

  if (isCustom) {
    customGroup.style.display = 'block';
    weeks = parseInt(customWeeks.value) || 0;
    if (weeks > 2) extraFeePercent = 0.05; 
  } else {
    customGroup.style.display = 'none';
  }

  if (weeks < 1) {
    resetAllDisplays();
    return;
  }

 
  const serviceFee = value * 0.05 * weeks;               
  const extraFee   = (value + serviceFee) * extraFeePercent;
  const subTotal   = value + serviceFee + extraFee;
  const vat        = subTotal * 0.075;                    
  const grandTotal = subTotal + vat;

  
  displayValue.textContent   = '₦' + value.toLocaleString();
  displayService.textContent = '₦' + serviceFee.toLocaleString();
  
  if (extraFeePercent > 0) {
    extraFeeRow.style.display = 'flex';
    displayExtra.textContent  = '₦' + extraFee.toLocaleString();
  } else {
    extraFeeRow.style.display = 'none';
  }

  displayVat.textContent     = '₦' + vat.toLocaleString();
  displayTotal.textContent   = '₦' + grandTotal.toLocaleString();


  const upfront = grandTotal * 0.5;
  const remaining = grandTotal * 0.5;
  const weekly = weeks > 0 ? remaining / weeks : 0;

  upfrontEl.textContent     = '₦' + upfront.toFixed(0).toLocaleString();
  weeklyCountEl.textContent = weeks;
  weeklyAmountEl.textContent = '₦' + weekly.toFixed(0).toLocaleString();

  breakdown.style.display = 'block';
}

function resetAllDisplays() {
  displayValue.textContent = displayService.textContent = 
  displayVat.textContent = displayTotal.textContent = 
  upfrontEl.textContent = weeklyAmountEl.textContent = '₦0';
  
  extraFeeRow.style.display = 'none';
  breakdown.style.display = 'none';
}


valueInput.addEventListener('input', updatePaymentPreview);
weeksSelect.addEventListener('change', updatePaymentPreview);
customWeeks.addEventListener('input', updatePaymentPreview);


updatePaymentPreview();



function makeDealsClickable() {
  const dealsList = document.getElementById('deals-list');
  if (!dealsList) return;

  const dealCards = dealsList.querySelectorAll('.deal-card');

  dealCards.forEach(card => {
    
    card.style.cursor = 'pointer';

    
    card.addEventListener('mouseenter', () => {
      card.style.transform = 'translateY(-3px)';
      card.style.boxShadow = '0 8px 20px rgba(0,0,0,0.08)';
      card.style.transition = 'all 0.18s ease';
    });

    card.addEventListener('mouseleave', () => {
      card.style.transform = '';
      card.style.boxShadow = '';
    });

   
    card.addEventListener('click', (e) => {
      if (e.target.closest('button, a')) return;

      const titleEl = card.querySelector('.deal-title');
      if (!titleEl) return;

      const titleText = titleEl.textContent.trim();

      const idMatch = titleText.match(/(?:#|Deal\s*#?|Order\s*)?(\d+)/i);
      const dealId = idMatch ? idMatch[1] : null;

      if (!dealId) {
        console.warn('Could not extract deal ID from:', titleText);
        return;
      }

      

      window.location.href = `/dashboard/deal/${dealId}`;

      
    });
  });
}


renderDeals = (deals) => {
  makeDealsClickable();
};


document.addEventListener('DOMContentLoaded', makeDealsClickable);