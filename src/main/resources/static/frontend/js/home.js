document.addEventListener('DOMContentLoaded', () => {

  const mobileMenuBtn = document.getElementById('menu-toggle');
  const mobileMenu = document.getElementById('mobile-menu');

if (mobileMenuBtn && mobileMenu) {
  const icon = mobileMenuBtn.querySelector('i');

  // Helper to sync menu state and icon
  const toggleMenu = (forceClose = false) => {
    const isHidden = forceClose || !mobileMenu.classList.contains('hidden');
    
    mobileMenu.classList.toggle('hidden', isHidden);
    
    if (icon) {
      icon.classList.toggle('fa-bars', isHidden);
      icon.classList.toggle('fa-xmark', !isHidden);
    }
  };

  // Toggle on button click
  mobileMenuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    toggleMenu();
  });

  // Close when clicking a link
  mobileMenu.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => toggleMenu(true));
  });

  // Close when clicking outside
  document.addEventListener('click', (e) => {
    if (!mobileMenu.contains(e.target) && !mobileMenuBtn.contains(e.target)) {
      toggleMenu(true);
    }
  });
}



  const userButtons = document.getElementById('user-buttons');
  if (userButtons) {
    const dropdown = document.getElementById('user-dropdown');
    
    if (dropdown) {
      userButtons.querySelector('button').addEventListener('click', (e) => {
        e.stopPropagation();
        dropdown.classList.toggle('hidden');
      });

      document.addEventListener('click', (e) => {
        if (!userButtons.contains(e.target)) {
          dropdown.classList.add('hidden');
        }
      });
    }
  }

  const scrollTopBtn = document.getElementById('scroll-top-btn');

  if (scrollTopBtn) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 300) {
        scrollTopBtn.classList.add('opacity-100', 'pointer-events-auto');
        scrollTopBtn.classList.remove('opacity-0', 'pointer-events-none');
      } else {
        scrollTopBtn.classList.remove('opacity-100', 'pointer-events-auto');
        scrollTopBtn.classList.add('opacity-0', 'pointer-events-none');
      }
    });

    scrollTopBtn.addEventListener('click', () => {
      window.scrollTo({
        top: 0,
        behavior: 'smooth'
      });
    });
  }

 
  const header = document.querySelector('header');
  if (header) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 80) {
        header.classList.add('shadow-md');
      } else {
        header.classList.remove('shadow-md');
      }
    });
  }


  const newsletterForm = document.getElementById('newsletter-form');
  if (newsletterForm) {
    newsletterForm.addEventListener('submit', window.handleSubscribe);
  }

 

});

function newsletterToast(message, ok) {
  const t = document.createElement('div');
  t.className = `fixed bottom-6 right-6 z-[9999] text-white px-4 py-3 rounded-xl shadow-lg text-sm max-w-[320px] ${
    ok ? 'bg-emerald-600' : 'bg-red-600'
  }`;
  t.textContent = message;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 4500);
}

window.handleSubscribe = async function handleSubscribe(e) {
  if (e && typeof e.preventDefault === 'function') e.preventDefault();

  const form = document.getElementById('newsletter-form');
  const emailInput = document.getElementById('newsletter-email');
  const email = emailInput?.value?.trim() || '';
  if (!email) {
    newsletterToast('Enter your email address first.', false);
    return false;
  }

  const btn = form?.querySelector('button[type="submit"]');
  const prevText = btn ? btn.textContent : null;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Subscribing...';
  }

  try {
    const payload = {
      email,
      source: window.location.pathname || 'website'
    };
    const res = await fetch('/api/newsletter/subscribe', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload)
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body?.message || `Request failed (${res.status})`);

    newsletterToast(body?.message || 'Subscribed successfully.', true);
    if (emailInput) emailInput.value = '';
    return false;
  } catch (err) {
    newsletterToast(err?.message || 'Subscription failed. Please try again.', false);
    return false;
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = prevText || 'SUBSCRIBE';
    }
  }
};

 const steps = {
    1: { 
      number: "STEP 1", 
      title: "Find a Deal", 
      desc: "Identify a fast-selling, discounted, auction or declutter item you want to secure." 
    },
    2: { 
      number: "STEP 2", 
      title: "Pay to Lock", 
      desc: "Sign up or log in, submit the item link. We assess and if approved, pay 50% upfront to lock it." 
    },
    3: { 
      number: "STEP 3", 
      title: "We Secure the Item", 
      desc: "DealLock.ng completes the purchase and holds the item securely for you." 
    },
    4: { 
      number: "STEP 4", 
      title: "Complete Your Payment", 
      desc: "Pay the remaining balance within the agreed Validity Period." 
    },
    5: { 
      number: "STEP 5", 
      title: "Pickup or Delivery", 
      desc: "Once full payment is confirmed, the item is released for pickup or delivery." 
    },
    6: { 
      number: "STEP 6", 
      title: "What If You Can't Complete?", 
      desc: "If you fail to pay the balance, we will resell the item. You receive a refund minus holding fees." 
    }
  };

  function showStep(stepNum) {
    const content = document.getElementById('step-content');
    const pen = document.getElementById('pen-icon');
    const numberEl = document.getElementById('phone-step-number');
    const titleEl = document.getElementById('phone-step-title');
    const descEl = document.getElementById('phone-step-desc');

    const step = steps[stepNum];

    content.classList.add('opacity-0');

    setTimeout(() => {
      numberEl.textContent = step.number;
      titleEl.textContent = step.title;
      descEl.textContent = step.desc;

     
      pen.style.transition = 'transform 0.6s ease';
      pen.style.transform = 'rotate(20deg)';

      setTimeout(() => pen.style.transform = 'rotate(-15deg)', 350);

      content.classList.remove('opacity-0');
    }, 250);

    
    document.querySelectorAll('.step-card').forEach(card => {
      card.classList.remove('ring-2', 'ring-emerald-600', 'bg-emerald-50');
    });
    document.getElementById(`step-${stepNum}`).classList.add('ring-2', 'ring-emerald-600', 'bg-emerald-50');
  }

 
  window.onload = () => showStep(1);
