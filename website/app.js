'use strict';
const screens = {
  numeric: ['Numeric · One reading, at a glance.', 'Numeric layout showing engine speed'],
  arc: ['Arc · A clear curved scale.', 'Arc layout showing coolant temperature'],
  bar: ['Bar · A compact horizontal scale.', 'Bar layout showing coolant temperature'],
  trend: ['Trend · See how a reading changes.', 'Trend layout showing coolant temperature'],
  dual: ['Dual · Two readings, one page.', 'Dual layout showing gear and engine speed'],
  alerts: ['Alerts · CEL above, warning below.', 'Dual page with a check-engine indicator and coolant warning']
};
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

const interestForm = document.querySelector('#interest-form');
interestForm?.addEventListener('submit', async event => {
  event.preventDefault();
  const button = interestForm.querySelector('button[type="submit"]');
  const message = document.querySelector('#form-message');
  button.disabled = true;
  message.classList.remove('error');
  message.textContent = 'Submitting…';
  try {
    const response = await fetch(interestForm.action, { method: 'POST', body: new URLSearchParams(new FormData(interestForm)), credentials: 'same-origin' });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'Unable to save your email. Please try again.');
    interestForm.reset();
    message.textContent = 'You are on the list. We will email you about eGauge development and new vehicle profiles.';
  } catch (error) {
    message.classList.add('error');
    message.textContent = error.message || 'Unable to save your email. Please try again.';
  } finally { button.disabled = false; }
});

const customForm = document.querySelector('#custom-form');
customForm?.addEventListener('submit', async event => {
  event.preventDefault();
  const button = customForm.querySelector('button[type="submit"]');
  const message = document.querySelector('#custom-message');
  button.disabled = true;
  message.classList.remove('error');
  message.textContent = 'Sending…';
  try {
    const response = await fetch(customForm.action, { method: 'POST', body: new URLSearchParams(new FormData(customForm)), credentials: 'same-origin' });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'Unable to send your request. Please try again.');
    customForm.reset();
    message.textContent = 'Request received. We will review it and follow up by email.';
  } catch (error) {
    message.classList.add('error');
    message.textContent = error.message || 'Unable to send your request. Please try again.';
  } finally { button.disabled = false; }
});
