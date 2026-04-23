function setContactStatus(message, ok) {
  const el = document.getElementById('contact-status');
  if (!el) return;
  el.textContent = message;
  el.classList.remove('hidden', 'text-red-600', 'text-emerald-600');
  el.classList.add(ok ? 'text-emerald-600' : 'text-red-600');
}

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('contact-form');
  const submitBtn = document.getElementById('contact-submit');
  if (!form) return;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const name = document.getElementById('contact-name')?.value?.trim() || '';
    const email = document.getElementById('contact-email')?.value?.trim() || '';
    const message = document.getElementById('contact-message')?.value?.trim() || '';
    if (!name || !email || !message) {
      setContactStatus('Please complete all fields.', false);
      return;
    }

    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Sending...';
    }

    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ name, email, message })
      });
      const payload = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(payload?.message || `Request failed (${res.status})`);

      setContactStatus(payload?.message || "Message sent successfully. We'll keep in touch shortly.", true);
      form.reset();
    } catch (err) {
      setContactStatus(err?.message || 'Failed to send message. Please try again.', false);
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Send Message';
      }
    }
  });
});
