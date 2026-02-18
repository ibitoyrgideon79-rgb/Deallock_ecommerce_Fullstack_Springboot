document.addEventListener('DOMContentLoaded', () => {
  
  const header       = document.querySelector('header');
  const menuToggle   = document.getElementById('menu-toggle');
  const navLinks     = document.getElementById('nav-links');
  const navClose     = document.getElementById('nav-close');
  const backToTopBtn = document.querySelector('.back-to-top');

 
  if (!menuToggle || !navLinks) {
    console.warn('Menu toggle or nav links not found');
  }


  let lastScrollY = window.scrollY;
  const scrollThreshold = 80;

  function updateHeader() {
    const currentScrollY = window.scrollY;

    if (currentScrollY > scrollThreshold) {
      header.classList.add('sticky');
    } else {
      header.classList.remove('sticky');
    }

    lastScrollY = currentScrollY;
  }

  let ticking = false;
  window.addEventListener('scroll', () => {
    if (!ticking) {
      window.requestAnimationFrame(() => {
        updateHeader();
        ticking = false;
      });
      ticking = true;
    }
  }, { passive: true });

  updateHeader(); 
  function toggleMenu() {
    const isOpen = navLinks.classList.toggle('active');

   
    const icon = menuToggle.querySelector('i');
    if (icon) {
      if (isOpen) {
        icon.classList.remove('fa-bars');
        icon.classList.add('fa-xmark');
      } else {
        icon.classList.remove('fa-xmark');
        icon.classList.add('fa-bars');
      }
    }
  }

 
  if (menuToggle) {
    menuToggle.addEventListener('click', toggleMenu);
  }

  
  if (navClose) {
    navClose.addEventListener('click', toggleMenu);
  }

  if (navLinks) {
    navLinks.querySelectorAll('a').forEach(link => {
      link.addEventListener('click', () => {
        navLinks.classList.remove('active');
        const icon = menuToggle?.querySelector('i');
        if (icon) {
          icon.classList.remove('fa-xmark');
          icon.classList.add('fa-bars');
        }
      });
    });
  }

  
  document.addEventListener('click', (e) => {
    if (
      navLinks?.classList.contains('active') &&
      !navLinks.contains(e.target) &&
      !menuToggle?.contains(e.target)
    ) {
      navLinks.classList.remove('active');
      const icon = menuToggle?.querySelector('i');
      if (icon) {
        icon.classList.remove('fa-xmark');
        icon.classList.add('fa-bars');
      }
    }
  });

 
  if (backToTopBtn) {
    window.addEventListener('scroll', () => {
      if (window.scrollY > 400) {
        backToTopBtn.classList.add('show');
      } else {
        backToTopBtn.classList.remove('show');
      }
    });

    backToTopBtn.addEventListener('click', () => {
      window.scrollTo({
        top: 0,
        behavior: 'smooth'
      });
    });
  }
});