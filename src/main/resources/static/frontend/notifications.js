(function () {
  const toggle = document.getElementById('notifToggle');
  const pop = document.getElementById('notifPop');
  const list = document.getElementById('notifList');
  if (!toggle || !pop || !list) return;

  async function loadNotifications() {
    try {
      const res = await fetch('/api/notifications?limit=6', {
        headers: { 'Accept': 'application/json' },
        credentials: 'include'
      });
      if (!res.ok) return;
      const data = await res.json();
      if (!Array.isArray(data) || data.length === 0) {
        list.innerHTML = '<div class="notification-empty">No notifications yet.</div>';
        return;
      }
      list.innerHTML = data.map(n => {
        const time = n.createdAt ? new Date(n.createdAt).toLocaleString() : '';
        return `
          <div class="notification-item-row">
            <div class="notification-text">${n.message || ''}</div>
            <div class="notification-time">${time}</div>
          </div>
        `;
      }).join('');
    } catch (e) {
      list.innerHTML = '<div class="notification-empty">Unable to load notifications.</div>';
    }
  }

  toggle.addEventListener('click', (e) => {
    e.stopPropagation();
    pop.classList.toggle('active');
    if (pop.classList.contains('active')) {
      loadNotifications();
    }
  });

  document.addEventListener('click', (e) => {
    if (!pop.contains(e.target) && !toggle.contains(e.target)) {
      pop.classList.remove('active');
    }
  });
})();
