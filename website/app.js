'use strict';
const screens = /* EGAUGE_SCREEN_DATA */ {};
const screenImage = document.querySelector('#screen-image');
document.querySelectorAll('[data-screen]').forEach(button => {
  button.addEventListener('click', () => {
    const name = button.dataset.screen;
    const [caption, alt] = screens[name];
    document.querySelectorAll('[data-screen]').forEach(option => option.setAttribute('aria-pressed', String(option === button)));
    screenImage.src = new URL(`${name}.png`, screenImage.src).href;
    screenImage.alt = alt;
    document.querySelector('#screen-caption').textContent = caption;
  });
});
document.querySelector('#year').textContent = new Date().getFullYear();

function registerForm(selector, messageSelector, pending, success) {
  const form = document.querySelector(selector);
  form?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    const message = document.querySelector(messageSelector);
    if (button.disabled) return;
    button.disabled = true;
    message.classList.remove('error');
    message.textContent = pending;
    const unavailable = 'We could not confirm your submission. Your details are still here. Please try again later.';
    try {
      const response = await fetch(form.action, {method:'POST', body:new URLSearchParams(new FormData(form)), credentials:'same-origin', signal:AbortSignal.timeout(15000)});
      if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Unexpected response');
      const result = await response.json();
      if (!response.ok) {
        message.classList.add('error');
        message.textContent = response.status >= 400 && response.status < 500 && typeof result.error === 'string' && result.error.length <= 200 ? result.error : unavailable;
        return;
      }
      if (result.ok !== true) throw new Error('Unconfirmed response');
      form.reset();
      message.textContent = success;
    } catch (_) {
      message.classList.add('error');
      message.textContent = unavailable;
    } finally {button.disabled = false;}
  });
}
registerForm('#interest-form', '#form-message', 'Submitting…', 'You are on the list. We will email you about eGauge development and new vehicle profiles.');
registerForm('#custom-form', '#custom-message', 'Sending…', 'Request received. We will review it and follow up by email.');
