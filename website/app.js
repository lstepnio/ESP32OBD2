'use strict';
const readings = {
  rpm: { label: 'ENGINE SPEED', value: '2,450', unit: 'RPM', arc: 35, color: '#d6ed83' },
  coolant: { label: 'COOLANT TEMPERATURE', value: '90', unit: '°C', arc: 49, color: '#f0bc88' },
  speed: { label: 'VEHICLE SPEED', value: '62', unit: 'MPH', arc: 39, color: '#b5d4ed' }
};
const gauge = document.querySelector('#demo-gauge');
document.querySelectorAll('[data-reading]').forEach(button => {
  button.addEventListener('click', () => {
    const reading = readings[button.dataset.reading];
    document.querySelectorAll('[data-reading]').forEach(option => option.setAttribute('aria-pressed', String(option === button)));
    document.querySelector('#demo-label').textContent = reading.label;
    document.querySelector('#demo-value').textContent = reading.value;
    document.querySelector('#demo-unit').textContent = reading.unit;
    document.querySelector('#demo-arc').style.strokeDasharray = `${reading.arc} 100`;
    gauge.style.setProperty('--accent', reading.color);
  });
});
document.querySelectorAll('[data-layout]').forEach(button => {
  button.addEventListener('click', () => {
    document.querySelectorAll('[data-layout]').forEach(option => option.setAttribute('aria-pressed', String(option === button)));
    gauge.classList.toggle('number-layout', button.dataset.layout === 'number');
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
    message.textContent = 'You are on the list. We will email you when preorder details are ready.';
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
