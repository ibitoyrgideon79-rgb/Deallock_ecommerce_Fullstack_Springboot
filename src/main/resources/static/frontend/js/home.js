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
    newsletterForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const email = document.getElementById('newsletter-email');
      if (email && email.value.trim()) {
        email.value = '';
      }
    });
  }

 

});

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